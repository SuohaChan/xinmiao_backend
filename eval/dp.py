"""
RAG 离线检索报告的 LLM-as-a-judge 评测脚本。

读取 RagOfflineEvalRunner 产出的 JSON（默认 eval/reports/rag-eval-result.json），
调用 OpenAI 兼容 Chat Completions API 做裁判（默认每批 20 条一次请求，见 --batch-size）。默认写出两份产物：
  - eval_llm_verdicts_<时间>.jsonl ：每行一条问答的裁判 JSON（sufficient / score / useful_ranks / noise_ranks / missing 及条数汇总）
  - eval_llm_aggregate_<时间>.json  ：本次运行元数据 + 全集指标总分析
可选 --write-full-detail 再写入含 retrieved_evidence 与系统指标的完整 JSON。

依赖: pip install "openai>=1.0"
配置: eval/.env 或环境变量 DEEPSEEK_*（见 --help）
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Final

from openai import OpenAI

logger = logging.getLogger(__name__)

_DEFAULT_BASE_URL: Final[str] = "https://api.deepseek.com"
_DEFAULT_REPORT: Final[str] = "./eval/reports/rag-eval-result.json"
_DEFAULT_OUTPUT_DIR: Final[str] = "./eval/runs"
_DEFAULT_MODEL: Final[str] = "deepseek-chat"

_JUDGE_RETRIES: Final[int] = 3
_REQUEST_DELAY_SEC: Final[float] = 0.5
_DEFAULT_BATCH_SIZE: Final[int] = 20

SYSTEM_PROMPT = """你是RAG检索质量评测裁判。只基于我提供的"问题"和"检索证据片段(snippet)"进行判断，不允许使用外部常识补全。

任务：
1) 判断这些证据是否足以回答问题（Sufficient: yes/no）
2) 给出0~3分证据支持度评分（0=完全无关或无法支撑；1=略相关但不足；2=基本足够；3=充分且直接）
3) useful_ranks：对回答有直接帮助的证据 rank；noise_ranks：无关/干扰证据 rank（见下互斥与全覆盖规则）
4) 如果不足，说明缺了什么关键信息（用一句话）

输出必须是JSON，字段固定为：
{
  "sufficient": "yes|no",
  "score": 0-3,
  "useful_ranks": [1,2],
  "noise_ranks": [3,4,5],
  "missing": "..."
}
useful_ranks：对回答该问题有直接帮助的证据 Rank（整数，可空）。
noise_ranks：无关、跑题、误导或纯冗余的证据 Rank（整数，可空）。
**同一条 Rank 不得同时出现在 useful_ranks 与 noise_ranks。本次列出的每条证据 Rank（从 1 到 K）必须恰好出现在二者之一**（K 为本次「检索证据片段」条数）。"""

BATCH_SYSTEM_PROMPT = """你是 RAG 检索质量评测裁判。你将收到一批**相互独立**的案例；每个案例有自己的 id、问题和检索证据。**严禁**把甲案例的证据用于判断乙案例。

对每个案例分别完成与单条评测相同的任务：
1) 证据是否足以回答该案例的问题（sufficient: yes/no）
2) 证据支持度 score 0～3
3) useful_ranks：对回答有直接帮助的证据 rank（可空）
4) noise_ranks：无关/干扰/冗余证据 rank（可空）；与 useful_ranks **互斥**，且**该案例本次给出的每个 Rank（1..K）必须恰好落在二者之一**
5) missing：若不足，用一句话说明缺什么（足够则可为空字符串）

你必须只输出一个 JSON 对象，且**仅含一个键** "verdicts"，其值为数组。
数组长度必须**严格等于**用户消息中声明的案例个数 N；第 i 个元素对应该批中按顺序的第 i 个案例。
每个元素必须包含键：id（与案例 id 一致）、sufficient、score、useful_ranks、noise_ranks、missing。
"""


def _load_eval_dotenv() -> None:
    """将 eval/.env 载入 os.environ（不覆盖已存在变量）。"""
    path = Path(__file__).resolve().parent / ".env"
    if not path.is_file():
        return
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key = key.strip()
        if not key:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        if key not in os.environ:
            os.environ[key] = value


_load_eval_dotenv()


@dataclass(frozen=True)
class Evidence:
    rank: int
    file_name: str
    snippet: str


@dataclass(frozen=True)
class RagCase:
    """单条检索评测用例（与 Spring 报告 results[] 元素对齐）。"""

    id: str
    query: str
    gold_docs: list[str]
    evidences: list[Evidence]
    system_recall: float
    system_precision: float

    @staticmethod
    def from_report_row(row: dict[str, Any]) -> RagCase:
        evidences: list[Evidence] = []
        for ev in row.get("evidences") or []:
            try:
                meta = ev.get("metadata") or {}
                evidences.append(
                    Evidence(
                        rank=int(ev["rank"]),
                        file_name=str(meta["fileName"]),
                        snippet=str(ev.get("snippet", "")),
                    )
                )
            except (KeyError, TypeError, ValueError) as e:
                logger.warning("跳过 malformed evidence: case_id=%s err=%s", row.get("id"), e)

        return RagCase(
            id=str(row["id"]),
            query=str(row["query"]),
            gold_docs=list(row.get("goldDocs") or []),
            evidences=evidences,
            system_recall=float(row.get("recallAtK", 0.0)),
            system_precision=float(row.get("precisionAtK", 0.0)),
        )


@dataclass
class JudgeClientConfig:
    api_key: str
    base_url: str = _DEFAULT_BASE_URL
    model: str = _DEFAULT_MODEL
    temperature: float = 0.1
    max_tokens: int = 500


def load_rag_eval_report(report_path: str | Path) -> tuple[list[RagCase], int]:
    """解析 RagOfflineEvalRunner 写出的整份 JSON 报告。返回 (用例列表, 报告中的 top-k 配置)。"""
    path = Path(report_path)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        raise ValueError(
            f"无法解析为单个 JSON：{path}\n"
            "需要 RagOfflineEvalRunner 输出（顶层含 results），不是 eval/cases/*.jsonl。"
        ) from e

    results = data.get("results")
    if not isinstance(results, list):
        raise ValueError(f"报告缺少 results 数组：{path}")

    raw_k = data.get("k", 5)
    try:
        report_k = int(raw_k)
    except (TypeError, ValueError):
        report_k = 5
    report_k = max(1, min(50, report_k))

    cases: list[RagCase] = []
    for row in results:
        if not isinstance(row, dict):
            logger.warning("跳过非 object 的 results 项")
            continue
        try:
            cases.append(RagCase.from_report_row(row))
        except KeyError as e:
            logger.warning("跳过缺字段的用例: err=%s row_keys=%s", e, list(row.keys())[:20])

    return cases, report_k


def _build_user_prompt(case: RagCase) -> str:
    parts: list[str] = [f"问题：{case.query}\n", "检索证据片段：\n"]
    for ev in case.evidences:
        parts.extend(
            [
                "---",
                f"Rank: {ev.rank}",
                f"文件: {ev.file_name}",
                f"内容: {ev.snippet}\n",
            ]
        )
    parts.append(
        "请给出评测结果（JSON格式），须含 sufficient、score、useful_ranks、noise_ranks、missing；"
        "noise_ranks 与 useful_ranks 互斥，且每条证据 Rank（1..K）须恰好落在二者之一。"
    )
    return "\n".join(parts)


def _coerce_rank_list(value: Any) -> list[int]:
    if not isinstance(value, list):
        return []
    out: list[int] = []
    for x in value:
        try:
            out.append(int(float(x)))
        except (TypeError, ValueError):
            pass
    return out


def _build_batch_user_prompt(batch: list[RagCase]) -> str:
    n = len(batch)
    lines: list[str] = [
        f"本批共 {n} 个案例（N={n}）。请输出 JSON，verdicts 数组长度必须等于 {n}。",
        "",
    ]
    for idx, case in enumerate(batch, start=1):
        lines.extend(
            [
                f"### 案例 {idx}（id={case.id}）",
                f"问题：{case.query}",
                "检索证据片段：",
            ]
        )
        for ev in case.evidences:
            lines.extend(
                [
                    "---",
                    f"Rank: {ev.rank}",
                    f"文件: {ev.file_name}",
                    f"内容: {ev.snippet}\n",
                ]
            )
        lines.append("")
    lines.append(f"请输出 verdicts（长度 {n}）：")
    return "\n".join(lines)


def _strip_verdict_for_record(raw: dict[str, Any]) -> dict[str, Any]:
    """与单条模式一致：保留裁判字段（含 noise_ranks）。"""
    return {
        "sufficient": raw["sufficient"],
        "score": raw["score"],
        "useful_ranks": raw["useful_ranks"],
        "noise_ranks": raw["noise_ranks"],
        "missing": raw["missing"],
    }


def _parse_batch_response(text: str, batch: list[RagCase]) -> list[dict[str, Any]]:
    root = json.loads(text)
    if not isinstance(root, dict):
        raise ValueError("根节点须为 JSON object")
    arr = root.get("verdicts")
    if not isinstance(arr, list):
        raise ValueError("缺少 verdicts 数组")
    n = len(batch)
    if len(arr) != n:
        raise ValueError(f"verdicts 长度须为 {n}，实际为 {len(arr)}")
    out: list[dict[str, Any]] = []
    for i, item in enumerate(arr):
        if not isinstance(item, dict):
            raise ValueError(f"verdicts[{i}] 须为 object")
        row = dict(item)
        exp_id = batch[i].id
        got = str(row.get("id", "")).strip()
        if got and got != exp_id:
            logger.warning("verdicts[%s] id 与输入不一致：期望 %s，得到 %s（按输入 id 采纳）", i, exp_id, got)
        row.pop("id", None)
        for key in ("sufficient", "score", "useful_ranks"):
            if key not in row:
                raise ValueError(f"verdicts[{i}] 缺少键 {key}")
        if "missing" not in row or row["missing"] is None:
            row["missing"] = ""
        if "noise_ranks" not in row or row["noise_ranks"] is None:
            row["noise_ranks"] = []
        _normalize_verdict(row)
        _validate_verdict(row)
        out.append(_strip_verdict_for_record(row))
    return out


def _normalize_verdict(raw: dict[str, Any]) -> None:
    sc = raw.get("score")
    if isinstance(sc, (float, str)):
        try:
            raw["score"] = int(float(sc))
        except (TypeError, ValueError):
            raw["score"] = -1

    raw["useful_ranks"] = _coerce_rank_list(raw.get("useful_ranks"))
    if "noise_ranks" not in raw or raw["noise_ranks"] is None:
        raw["noise_ranks"] = []
    raw["noise_ranks"] = _coerce_rank_list(raw.get("noise_ranks"))

    miss = raw.get("missing")
    if miss is not None and not isinstance(miss, str):
        raw["missing"] = str(miss)


def _validate_verdict(raw: dict[str, Any]) -> None:
    if raw.get("sufficient") not in ("yes", "no"):
        raise ValueError("sufficient 必须为 yes 或 no")
    score = raw.get("score")
    if not isinstance(score, int) or not 0 <= score <= 3:
        raise ValueError("score 必须为 0～3 的整数")
    if not isinstance(raw.get("useful_ranks"), list):
        raise ValueError("useful_ranks 必须为数组")
    if not isinstance(raw.get("noise_ranks"), list):
        raise ValueError("noise_ranks 必须为数组")
    if not isinstance(raw.get("missing"), str):
        raise ValueError("missing 必须为字符串")


def _failed_verdict(message: str) -> dict[str, Any]:
    return {
        "sufficient": "no",
        "score": -1,
        "useful_ranks": [],
        "noise_ranks": [],
        "missing": message,
    }


class LlmJudgeRunner:
    """OpenAI 兼容 Chat Completions 上的 RAG 证据裁判。"""

    def __init__(self, cfg: JudgeClientConfig) -> None:
        self._cfg = cfg
        self._client = OpenAI(api_key=cfg.api_key, base_url=cfg.base_url)

    def _complete_json(self, system: str, user: str, max_tokens: int) -> str:
        """带重试的 chat.completions，返回 message content 文本。"""
        last_error: Exception | None = None
        for attempt in range(_JUDGE_RETRIES):
            try:
                response = self._client.chat.completions.create(
                    model=self._cfg.model,
                    messages=[
                        {"role": "system", "content": system},
                        {"role": "user", "content": user},
                    ],
                    temperature=self._cfg.temperature,
                    max_tokens=max_tokens,
                    response_format={"type": "json_object"},
                )
                text = response.choices[0].message.content
                if not text or not text.strip():
                    raise ValueError("模型返回空 content")
                return text.strip()
            except Exception as e:
                last_error = e
                logger.warning(
                    "chat.completions 失败 attempt=%s/%s: %s",
                    attempt + 1,
                    _JUDGE_RETRIES,
                    e,
                )
                if attempt < _JUDGE_RETRIES - 1:
                    time.sleep(2**attempt)
        raise RuntimeError(f"API 或网络失败: {last_error}") from last_error

    def judge_one(self, case: RagCase) -> dict[str, Any]:
        user_prompt = _build_user_prompt(case)
        try:
            text = self._complete_json(SYSTEM_PROMPT, user_prompt, self._cfg.max_tokens)
            verdict = json.loads(text)
            if not isinstance(verdict, dict):
                raise ValueError("模型返回非 JSON object")
            _normalize_verdict(verdict)
            _validate_verdict(verdict)
            return verdict
        except Exception as e:
            return _failed_verdict(f"API 或解析失败: {e}")

    def judge_batch(self, batch: list[RagCase]) -> list[dict[str, Any]]:
        """一批案例一次请求；返回与 batch 同序的裁判结果（不含 id）。"""
        if len(batch) == 1:
            return [self.judge_one(batch[0])]
        user_prompt = _build_batch_user_prompt(batch)
        n = len(batch)
        max_tokens = min(8192, 400 + n * 300)
        text = self._complete_json(BATCH_SYSTEM_PROMPT, user_prompt, max_tokens)
        return _parse_batch_response(text, batch)

    def judge_all(
        self,
        cases: list[RagCase],
        *,
        batch_size: int = _DEFAULT_BATCH_SIZE,
        delay_sec: float = _REQUEST_DELAY_SEC,
    ) -> list[dict[str, Any]]:
        """裁判全部用例；batch_size<=1 时逐条请求，否则每批一次请求（失败则该批回退逐条）。"""
        out: list[dict[str, Any]] = []
        total = len(cases)
        bs = max(1, int(batch_size))

        if bs <= 1:
            for i, case in enumerate(cases):
                logger.info("[%s/%s] 请求中 case_id=%s …", i + 1, total, case.id)
                t_req = time.perf_counter()
                verdict = self.judge_one(case)
                req_s = time.perf_counter() - t_req
                stamp = time.strftime("%Y-%m-%d %H:%M:%S")
                out.append(
                    {
                        "id": case.id,
                        "query": case.query,
                        "system_recall": case.system_recall,
                        "evidence_count": len(case.evidences),
                        "llm_judge": verdict,
                        "timestamp": stamp,
                    }
                )
                logger.info(
                    "[%s/%s] 完成 case_id=%s score=%s 耗时=%.1fs；%.1fs 后继续",
                    i + 1,
                    total,
                    case.id,
                    verdict.get("score"),
                    req_s,
                    delay_sec,
                )
                time.sleep(delay_sec)
            return out

        num_batches = (total + bs - 1) // bs
        offset = 0
        for bidx in range(num_batches):
            batch = cases[offset : offset + bs]
            offset += len(batch)
            logger.info(
                "批次 %s/%s 条数=%s 首条 id=%s …",
                bidx + 1,
                num_batches,
                len(batch),
                batch[0].id,
            )
            t_req = time.perf_counter()
            try:
                verdicts = self.judge_batch(batch)
            except Exception as e:
                logger.warning("整批失败，本批改为逐条请求: %s", e)
                for case in batch:
                    verdict = self.judge_one(case)
                    st = time.strftime("%Y-%m-%d %H:%M:%S")
                    out.append(
                        {
                            "id": case.id,
                            "query": case.query,
                            "system_recall": case.system_recall,
                            "evidence_count": len(case.evidences),
                            "llm_judge": verdict,
                            "timestamp": st,
                        }
                    )
                req_s = time.perf_counter() - t_req
                logger.info(
                    "批次 %s/%s 完成（逐条回退）条数=%s 总耗时=%.1fs；%.1fs 后继续下一批",
                    bidx + 1,
                    num_batches,
                    len(batch),
                    req_s,
                    delay_sec,
                )
                time.sleep(delay_sec)
                continue

            req_s = time.perf_counter() - t_req
            stamp = time.strftime("%Y-%m-%d %H:%M:%S")
            for case, verdict in zip(batch, verdicts):
                out.append(
                    {
                        "id": case.id,
                        "query": case.query,
                        "system_recall": case.system_recall,
                        "evidence_count": len(case.evidences),
                        "llm_judge": verdict,
                        "timestamp": stamp,
                    }
                )
            logger.info(
                "批次 %s/%s 完成 条数=%s 总耗时=%.1fs；%.1fs 后继续下一批",
                bidx + 1,
                num_batches,
                len(batch),
                req_s,
                delay_sec,
            )
            time.sleep(delay_sec)
        return out


def classify_evidence_slots(
    evidence_count: int,
    useful_ranks: list[int],
    noise_ranks: list[int],
) -> dict[str, Any]:
    """
    将 Rank 1..K 分为有效 / 噪音 / 未标注三类。

    - 若模型给出非空 noise_ranks：以其为准，并与 useful 去交集（交集计为有效优先）。
    - 若 noise_ranks 为空且 useful_ranks 非空：视为旧版输出，其余 Rank 一律推断为噪音。
    - 若二者皆空：全部记为未标注（不强行拆成噪音）。
    """
    k = max(0, int(evidence_count))
    if k <= 0:
        return {
            "k": 0,
            "useful_count": 0,
            "noise_count": 0,
            "unlabeled_count": 0,
            "inferred_noise_from_legacy_schema": False,
            "partition_overlap_fixed": 0,
        }
    all_r = set(range(1, k + 1))
    u = {int(r) for r in useful_ranks if isinstance(r, int) and 1 <= r <= k}
    n_raw = {int(r) for r in noise_ranks if isinstance(r, int) and 1 <= r <= k}
    overlap = u & n_raw
    n_explicit = n_raw - u
    inferred = False
    if not noise_ranks:
        if u:
            n_final = all_r - u
            inferred = True
        else:
            n_final = set()
    else:
        n_final = n_explicit
    unlabeled = all_r - u - n_final
    return {
        "k": k,
        "useful_count": len(u),
        "noise_count": len(n_final),
        "unlabeled_count": len(unlabeled),
        "inferred_noise_from_legacy_schema": inferred,
        "partition_overlap_fixed": len(overlap),
    }


def aggregate_metrics(run_records: list[dict[str, Any]], *, report_top_k: int) -> dict[str, Any]:
    valid = [r for r in run_records if r["llm_judge"]["score"] >= 0]
    n = len(valid)
    if n == 0:
        return {"error": "no_valid_results", "failed_cases": len(run_records)}

    sufficient = sum(1 for r in valid if r["llm_judge"]["sufficient"] == "yes")
    hit = sum(1 for r in valid if r["llm_judge"]["score"] >= 1)
    scores = [r["llm_judge"]["score"] for r in valid]
    score_dist = {i: sum(1 for s in scores if s == i) for i in range(4)}
    system_recalls = [r["system_recall"] for r in valid]
    avg_sys = sum(system_recalls) / len(system_recalls) if system_recalls else 0.0
    hit_rate = hit / n

    hit_ev_slots = 0
    hit_useful_total = 0
    hit_noise_total = 0
    hit_unlabeled_total = 0
    legacy_infer_cases = 0
    overlap_fix_total = 0
    for r in valid:
        if r["llm_judge"]["score"] < 1:
            continue
        v = r["llm_judge"]
        k = int(r.get("evidence_count", 0))
        cls = classify_evidence_slots(k, list(v.get("useful_ranks") or []), list(v.get("noise_ranks") or []))
        hit_ev_slots += cls["k"]
        hit_useful_total += cls["useful_count"]
        hit_noise_total += cls["noise_count"]
        hit_unlabeled_total += cls["unlabeled_count"]
        if cls["inferred_noise_from_legacy_schema"]:
            legacy_infer_cases += 1
        overlap_fix_total += int(cls["partition_overlap_fixed"])

    useful_ratio = (hit_useful_total / hit_ev_slots) if hit_ev_slots > 0 else 0.0
    noise_ratio = (hit_noise_total / hit_ev_slots) if hit_ev_slots > 0 else 0.0
    unlabeled_ratio = (hit_unlabeled_total / hit_ev_slots) if hit_ev_slots > 0 else 0.0

    return {
        "total_cases": n,
        "sufficient_cases": sufficient,
        "sufficiency_rate": sufficient / n,
        "hit_cases": hit,
        "llm_relevance_hit_rate": hit_rate,
        "llm_relevance_hit_rate_at_k": hit_rate,
        "report_top_k": report_top_k,
        "note_hit_rate": "llm_relevance_hit_rate_at_k：score>=1 的用例占比；k 为 Spring 报告顶层 k（与检索条数上限一致）。",
        "within_hit_cases_evidence_slots_total": hit_ev_slots,
        "within_hit_cases_useful_evidence_total": hit_useful_total,
        "within_hit_cases_noise_evidence_total": hit_noise_total,
        "within_hit_cases_unlabeled_evidence_total": hit_unlabeled_total,
        "useful_ratio_among_hit_evidence_slots": useful_ratio,
        "noise_ratio_among_hit_evidence_slots": noise_ratio,
        "unlabeled_ratio_among_hit_evidence_slots": unlabeled_ratio,
        "avg_useful_evidence_per_hit_case": (hit_useful_total / hit) if hit > 0 else 0.0,
        "avg_noise_evidence_per_hit_case": (hit_noise_total / hit) if hit > 0 else 0.0,
        "hit_cases_with_legacy_inferred_noise": legacy_infer_cases,
        "partition_overlap_ranks_fixed_total": overlap_fix_total,
        "avg_score": sum(scores) / len(scores),
        "score_distribution": score_dist,
        "high_quality_rate": score_dist.get(3, 0) / n,
        "completely_irrelevant_rate": score_dist.get(0, 0) / n,
        "avg_system_recall_at_k": avg_sys,
        "avg_system_recall_minus_llm_hit_rate": avg_sys - hit_rate,
        "failed_cases": sum(1 for r in run_records if r["llm_judge"]["score"] == -1),
    }


def _write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def _write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")


def _verdict_jsonl_row(record: dict[str, Any]) -> dict[str, Any]:
    v = record["llm_judge"]
    k = int(record.get("evidence_count", 0))
    row: dict[str, Any] = {
        "id": record["id"],
        "query": record["query"],
        "sufficient": v["sufficient"],
        "score": v["score"],
        "useful_ranks": v.get("useful_ranks", []),
        "noise_ranks": v.get("noise_ranks", []),
        "missing": v.get("missing", ""),
        "evidence_count": k,
        "judged_at": record["timestamp"],
    }
    if isinstance(v.get("score"), int) and v["score"] >= 0:
        cls = classify_evidence_slots(
            k,
            list(v.get("useful_ranks") or []),
            list(v.get("noise_ranks") or []),
        )
        row["useful_evidence_count"] = cls["useful_count"]
        row["noise_evidence_count"] = cls["noise_count"]
        row["unlabeled_evidence_count"] = cls["unlabeled_count"]
        row["legacy_inferred_noise"] = cls["inferred_noise_from_legacy_schema"]
    else:
        row["useful_evidence_count"] = 0
        row["noise_evidence_count"] = 0
        row["unlabeled_evidence_count"] = 0
        row["legacy_inferred_noise"] = False
    return row


def _configure_logging(*, verbose: bool) -> None:
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )
    # 避免每条 API 都打 httpx INFO，看起来像「卡在最后一条日志」
    noisy = ("httpx", "httpcore")
    for name in noisy:
        logging.getLogger(name).setLevel(logging.DEBUG if verbose else logging.WARNING)


def _build_arg_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description="RAG 检索质量 LLM-as-a-judge（OpenAI 兼容 API）",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    p.add_argument("--eval-file", type=str, default=_DEFAULT_REPORT, help="RagOfflineEvalRunner 输出的 JSON")
    p.add_argument("--api-key", type=str, default=os.getenv("DEEPSEEK_API_KEY"), help="API Key")
    p.add_argument("--base-url", type=str, default=os.getenv("DEEPSEEK_BASE_URL", _DEFAULT_BASE_URL))
    p.add_argument("--model", type=str, default=os.getenv("DEEPSEEK_MODEL", _DEFAULT_MODEL))
    p.add_argument("--output-dir", type=str, default=_DEFAULT_OUTPUT_DIR, help="输出目录")
    p.add_argument("--start", type=int, default=0, help="起始案例下标（含）")
    p.add_argument("--end", type=int, default=None, help="结束案例下标（不含）；默认到最后")
    p.add_argument(
        "--batch-size",
        type=int,
        default=_DEFAULT_BATCH_SIZE,
        help="每批合并请求的案例数；1 表示逐条请求（与旧行为一致）",
    )
    p.add_argument(
        "--delay",
        type=float,
        default=_REQUEST_DELAY_SEC,
        help="每批（或逐条模式下每条）完成后的额外等待（秒），减轻限流；可设 0",
    )
    p.add_argument(
        "--write-full-detail",
        action="store_true",
        help="额外写入含 retrieved_evidence 与系统指标的完整 JSON（体积大）",
    )
    p.add_argument("-v", "--verbose", action="store_true", help="DEBUG 日志")
    return p


def main(argv: list[str] | None = None) -> int:
    args = _build_arg_parser().parse_args(argv)
    _configure_logging(verbose=args.verbose)

    api_key = (args.api_key or os.getenv("DEEPSEEK_API_KEY") or "").strip()
    if not api_key:
        env_path = Path(__file__).resolve().parent / ".env"
        logger.error(
            "缺少 API Key：设置 DEEPSEEK_API_KEY 或使用 --api-key；可在 %s 配置",
            env_path,
        )
        return 1

    base_url = (args.base_url or "").strip() or _DEFAULT_BASE_URL
    cfg = JudgeClientConfig(
        api_key=api_key,
        base_url=base_url,
        model=args.model,
    )

    report_path = Path(args.eval_file)
    out_dir = Path(args.output_dir)
    logger.info("base_url=%s model=%s", base_url, cfg.model)
    logger.info("report=%s", report_path.resolve())

    try:
        cases, report_k = load_rag_eval_report(report_path)
    except (ValueError, OSError) as e:
        logger.error("%s", e)
        return 1

    end = args.end if args.end is not None else len(cases)
    cases = cases[args.start : end]
    logger.info("slice [%s:%s) count=%s", args.start, end, len(cases))

    runner = LlmJudgeRunner(cfg)
    t0 = time.perf_counter()
    records = runner.judge_all(cases, batch_size=args.batch_size, delay_sec=args.delay)
    elapsed = time.perf_counter() - t0

    stats = aggregate_metrics(records, report_top_k=report_k)
    ts = time.strftime("%Y%m%d_%H%M%S")
    verdicts_path = out_dir / f"eval_llm_verdicts_{ts}.jsonl"
    aggregate_path = out_dir / f"eval_llm_aggregate_{ts}.json"

    run_meta: dict[str, Any] = {
        "finished_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "elapsed_sec": round(elapsed, 3),
        "report_source": str(report_path.resolve()),
        "base_url": base_url,
        "model": cfg.model,
        "slice": {"start": args.start, "end": end, "count": len(records)},
        "batch_size": args.batch_size,
        "report_top_k": report_k,
        "verdicts_file": verdicts_path.name,
    }
    if args.write_full_detail:
        detail_path = out_dir / f"eval_detail_{ts}.json"
        full_records = [
            {
                "id": c.id,
                "query": c.query,
                "gold_docs": c.gold_docs,
                "system_recall": c.system_recall,
                "system_precision": c.system_precision,
                "retrieved_evidence": [asdict(ev) for ev in c.evidences],
                "llm_judge": r["llm_judge"],
                "timestamp": r["timestamp"],
            }
            for c, r in zip(cases, records)
        ]
        _write_json(detail_path, full_records)
        run_meta["full_detail_file"] = detail_path.name

    _write_jsonl(verdicts_path, [_verdict_jsonl_row(r) for r in records])
    _write_json(
        aggregate_path,
        {"run": run_meta, "metrics": stats},
    )

    logger.info(
        "done in %.1fs verdicts=%s aggregate=%s",
        elapsed,
        verdicts_path,
        aggregate_path,
    )

    if stats.get("error"):
        logger.error("汇总失败: %s", stats)
        return 1

    logger.info("--- metrics ---")
    logger.info(
        "llm_relevance_hit_rate@k (score>=1, k=%s): %.1f%%",
        stats.get("report_top_k", "?"),
        100.0 * stats["llm_relevance_hit_rate"],
    )
    logger.info(
        "within hit cases: useful=%s noise=%s slots=%s",
        stats.get("within_hit_cases_useful_evidence_total"),
        stats.get("within_hit_cases_noise_evidence_total"),
        stats.get("within_hit_cases_evidence_slots_total"),
    )
    logger.info(
        "useful_ratio / noise_ratio / unlabeled @hit slots: %.1f%% / %.1f%% / %.1f%%",
        100.0 * stats["useful_ratio_among_hit_evidence_slots"],
        100.0 * stats["noise_ratio_among_hit_evidence_slots"],
        100.0 * stats["unlabeled_ratio_among_hit_evidence_slots"],
    )
    logger.info("avg_system_recall_at_k:           %.1f%%", 100.0 * stats["avg_system_recall_at_k"])
    logger.info("sufficiency_rate:               %.1f%%", 100.0 * stats["sufficiency_rate"])
    logger.info("avg_score:                      %.2f / 3", stats["avg_score"])
    logger.info("failed_cases:                   %s", stats["failed_cases"])
    for s, c in sorted(stats["score_distribution"].items()):
        logger.info("  score %s: %s", s, c)

    return 0


if __name__ == "__main__":
    sys.exit(main())
