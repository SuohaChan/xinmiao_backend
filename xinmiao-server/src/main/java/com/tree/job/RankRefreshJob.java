package com.tree.job;

import com.tree.dto.RankDto;
import com.tree.mapper.StudentClassMapper;
import com.tree.mapper.StudentTaskMapper;
import com.tree.util.RankCacheHelper;
import com.tree.util.RankFlowAggregateHelper;
import com.tree.utils.RankKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 排行榜定时任务：
 * - 今日/周榜：completeTask 事务提交后写 Redis；定时从流水表重建，补偿漏写或漂移（双源一致）。
 * - 学院学年榜：从 DB 按学年时间范围汇总，写入各学院 ZSet。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankRefreshJob {

    private final RankCacheHelper rankCacheHelper;
    private final RankFlowAggregateHelper rankFlowAggregateHelper;
    private final StudentClassMapper studentClassMapper;
    private final StudentTaskMapper studentTaskMapper;

    /** 每 10 分钟从流水表重建今日榜，补偿 Redis 漏写或漂移 */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void refreshTodayRankFromFlow() {
        try {
            LocalDateTime start = RankKeyUtils.todayStart();
            LocalDateTime end = LocalDateTime.now();
            List<RankDto> list = rankFlowAggregateHelper.aggregateRankDtoListFromFlow(start, end);
            String key = RankKeyUtils.todayRankKey();
            rankCacheHelper.buildRealRankZSet(key, list, RankKeyUtils.todayTtlDays());
            log.debug("已从流水补偿今日榜 key={} size={}", key, list.size());
        } catch (Exception e) {
            log.error("从流水补偿今日榜失败", e);
        }
    }

    /** 每 10 分钟从流水表重建本周榜，补偿 Redis 漏写或漂移 */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void refreshWeekRankFromFlow() {
        try {
            LocalDateTime start = RankKeyUtils.weekStart();
            LocalDateTime end = LocalDateTime.now();
            List<RankDto> list = rankFlowAggregateHelper.aggregateRankDtoListFromFlow(start, end);
            String key = RankKeyUtils.weekRankKey();
            rankCacheHelper.buildRealRankZSet(key, list, RankKeyUtils.weekTtlDays());
            log.debug("已从流水补偿周榜 key={} size={}", key, list.size());
        } catch (Exception e) {
            log.error("从流水补偿周榜失败", e);
        }
    }

    /** 每天 1 点刷新学院学年榜：从 DB 按学年时间范围（submit_time）汇总各学院积分，写入 Redis ZSet */
    @Scheduled(cron = "0 0 1 * * ?")
    public void refreshCollegeRankFromTotal() {
        try {
            List<String> colleges = studentClassMapper.listDistinctColleges();
            if (colleges == null || colleges.isEmpty()) {
                log.info("无学院数据，跳过学院榜刷新");
                return;
            }
            LocalDateTime yearStart = RankKeyUtils.academicYearStart();
            LocalDateTime endTime = LocalDateTime.now();
            for (String college : colleges) {
                List<RankDto> list = studentTaskMapper.getCollegeRankByAcademicYear(college, yearStart, endTime);
                String key = RankKeyUtils.collegeYearRankKey(college);
                rankCacheHelper.buildRealRankZSet(key, list != null ? list : Collections.emptyList(), RankKeyUtils.collegeYearTtlDays());
            }
            log.info("已刷新学院学年榜（DB 按学年时间范围汇总） colleges={}", colleges.size());
        } catch (Exception e) {
            log.error("刷新学院学年榜失败", e);
        }
    }
}
