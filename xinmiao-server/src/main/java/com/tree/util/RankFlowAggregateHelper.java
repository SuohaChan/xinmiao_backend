package com.tree.util;

import com.tree.dto.RankDto;
import com.tree.mapper.CreditFlowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 从积分流水表按时间范围聚合，供今日榜/周榜 key 缺失时懒加载回源。
 */
@Component
@RequiredArgsConstructor
public class RankFlowAggregateHelper {

    private static final int RANK_LIMIT = 200;

    private final CreditFlowMapper creditFlowMapper;

    /**
     * 按 [start, end] 时间范围从流水表聚合，返回带 rank 的 RankDto 列表。
     */
    public List<RankDto> aggregateRankDtoListFromFlow(LocalDateTime start, LocalDateTime end) {
        List<RankDto> list = creditFlowMapper.aggregateByTimeRange(start, end, RANK_LIMIT);
        if (list == null || list.isEmpty()) {
            return list != null ? list : List.of();
        }
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        return list;
    }
}
