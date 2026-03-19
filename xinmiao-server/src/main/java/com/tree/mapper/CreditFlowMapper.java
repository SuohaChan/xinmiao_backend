package com.tree.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tree.dto.RankDto;
import com.tree.entity.CreditFlow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分流水表：每次加分写一条；查榜缺 key 时按时间范围聚合。
 */
public interface CreditFlowMapper extends BaseMapper<CreditFlow> {

    /**
     * 按时间范围聚合流水，得到各学生积分合计，按 total_score 降序（rank 由调用方赋值）。
     */
    List<RankDto> aggregateByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") int limit);
}
