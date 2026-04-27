package com.tree.service;

import com.tree.dto.RankDto;

import java.util.List;

/**
 * 排行榜服务：今日榜、周榜、学院学年榜的查询，以及任务完成后写入 Redis。
 */
public interface RankService {

    /**
     * 获取今日排行榜：先读 Redis ZSet，未命中时从流水表按今日时间范围聚合并写入。
     */
    List<RankDto> getTodayRank();

    /**
     * 获取本周排行榜：先读 Redis ZSet，未命中时从流水表按本周时间范围聚合并写入。
     */
    List<RankDto> getWeekRank();

    /**
     * 获取当前登录学生所在学院的学年排行榜。
     */
    List<RankDto> getCollegeRank();

    /**
     * 今日排行榜（仅 DB 聚合，不读不写 Redis），用于与 {@link #getTodayRank()} 压测对比。
     */
    List<RankDto> getTodayRankFromDatabase();

    /**
     * 本周排行榜（仅 DB 聚合，不读不写 Redis）。
     */
    List<RankDto> getWeekRankFromDatabase();

    /**
     * 当前学生所在学院学年榜（仅 DB 查询，不读不写 Redis）。
     */
    List<RankDto> getCollegeRankFromDatabase();

    /**
     * 将本次加分写入排行榜 Redis（今日/周/学院学年），由完成任务的 Service 在事务提交后调用。
     *
     * @param member     学生 ID（String）
     * @param credit     本次获得的积分
     * @param todayKey   今日榜 Redis key
     * @param weekKey    周榜 Redis key
     * @param collegeKey 学院学年榜 Redis key，可为 null 或空
     */
    void applyRankToRedis(String member, int credit, String todayKey, String weekKey, String collegeKey);
}
