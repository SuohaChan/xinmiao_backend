package com.tree.service.impl;

import org.springframework.stereotype.Service;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.exception.BusinessException;
import com.tree.mapper.AppealMapper;
import com.tree.service.AppealService;
import com.tree.dto.AppealQueryDto;
import com.tree.dto.AppealSubmitDto;
import com.tree.dto.AppealUpdateDto;
import com.tree.entity.Appeal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import com.tree.result.ErrorCode;
import com.tree.utils.DateTimeUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AppealServiceImpl extends ServiceImpl<AppealMapper, Appeal> implements AppealService {
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitAppeal(AppealSubmitDto dto) {
        Appeal appeal = BeanUtil.copyProperties(dto, Appeal.class);
        appeal.setSubmitTime(LocalDateTime.now());
        this.save(appeal);
        return appeal.getId();  // 返回自增ID
    }

    @Override
    public List<Appeal> getUserAppeals(Long userId) {
        QueryWrapper<Appeal> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .orderByDesc("submit_time")
                .select("id",  "appeal_type", "appeal_title", "appeal_description",  "submit_time",  "status");
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<Appeal> getHandlerAppeals(Long handlerId) {
        QueryWrapper<Appeal> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("handler_id", handlerId)
                .orderByDesc("update_time");
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleAppeal(AppealUpdateDto dto) {
        Appeal appeal = this.getById(dto.getId());
        if (appeal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在");
        }

        if ("completed".equals(appeal.getStatus()) || "rejected".equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "该申诉已完成处理，无法再次操作");
        }

        // 更新申诉信息
        Appeal updateAppeal = BeanUtil.copyProperties(dto, Appeal.class);
        // 根据状态设置对应的时间
        LocalDateTime now = LocalDateTime.now();
        switch (dto.getStatus()) {
            case "accept":
                updateAppeal.setAcceptTime(now);
                break;
            case "processing":
                updateAppeal.setProcessingTime(now);
                break;
            case "complete":
                updateAppeal.setCompleteTime(now);
                break;
            case "rejected":
                updateAppeal.setCompleteTime(now);
                break;
            default:
                break;
        }
        return this.updateById(updateAppeal);
    }

    @Override
    public Page<Appeal> page(AppealQueryDto queryDTO, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<Appeal> queryWrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getId() != null && !queryDTO.getId().isEmpty()) {
            try {
                Long id = Long.valueOf(queryDTO.getId());
                queryWrapper.eq(Appeal::getId, id);
            } catch (NumberFormatException e) {
                log.warn("Invalid ID format: {}", queryDTO.getId());
            }
        }

        if (queryDTO.getAppealerId() != null) {
            queryWrapper.eq(Appeal::getUserId, queryDTO.getAppealerId());
        }

        if (queryDTO.getStatus() != null ){
            if(queryDTO.getStatus().equals("complete"))
                queryWrapper.eq(Appeal::getStatus, "completed").or(wrapper -> wrapper.eq(Appeal::getStatus, "rejected"));
            else
                queryWrapper.eq(Appeal::getStatus, queryDTO.getStatus());
        }

        LocalDateTime queryStart = DateTimeUtils.parse(queryDTO.getStartTime());
        if (queryStart != null) {
            queryWrapper.gt(Appeal::getUpdateTime, queryStart);
        }
        Page<Appeal> page = new Page<>(pageNum, pageSize);
        baseMapper.selectPage(page, queryWrapper);
        return page;
    }

    @Override
    public Appeal getByIdOrThrow(Long id) {
        Appeal appeal = getById(id);
        if (appeal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在");
        }
        return appeal;
    }

    @Override
    public Appeal getByIdForStudent(Long appealId, Long studentId) {
        Appeal appeal = getById(appealId);
        if (appeal == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在");
        }
        if (!studentId.equals(appeal.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限查看该申诉");
        }
        return appeal;
    }
}
