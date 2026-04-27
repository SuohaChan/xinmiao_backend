/*
  批量造积分流水（tb_credit_flow），用于压测排行榜「Redis vs 纯 DB 聚合」差异。
  前置：已有 tb_student(1..10)、tb_task(1..11)（与 02_data.sql 一致）。

  用法（MySQL 8+）：
    mysql -u root -p xinmiao < bulk_seed_credit_flow.sql

  可调：下方 SET @rows、日期范围。执行前可先：
    TRUNCATE TABLE tb_credit_flow;   -- 或 DELETE FROM tb_credit_flow;
*/

USE `xinmiao`;

SET NAMES utf8mb4;

/* ========= 参数：改这两个即可 ========= */
SET @rows = 50000;                    /* 插入行数，建议 1万～50万试压 */
SET @days_back = 60;                  /* occurred_at 落在最近多少天内（覆盖今日/周榜窗口） */

SET SESSION cte_max_recursion_depth = 10000000;

SET FOREIGN_KEY_CHECKS = 1;

/* 递归序列 1..@rows（MySQL 要求：INSERT INTO ... WITH RECURSIVE ... SELECT） */
INSERT INTO `tb_credit_flow` (`student_id`, `task_id`, `credit`, `college`, `occurred_at`)
WITH RECURSIVE seq AS (
  SELECT 1 AS i
  UNION ALL
  SELECT i + 1 FROM seq WHERE i < @rows
)
SELECT
  (s.i % 10) + 1 AS student_id,
  (s.i % 11) + 1 AS task_id,
  1 + (s.i % 25) AS credit,
  CASE
    WHEN (s.i % 10) + 1 <= 7 THEN '计算机科学与技术学院'
    ELSE '机电工程学院'
  END AS college,
  DATE_SUB(NOW(), INTERVAL (s.i % (@days_back * 24 * 60)) MINUTE)
FROM seq s;

SELECT COUNT(*) AS credit_flow_rows FROM `tb_credit_flow`;

ANALYZE TABLE `tb_credit_flow`;
