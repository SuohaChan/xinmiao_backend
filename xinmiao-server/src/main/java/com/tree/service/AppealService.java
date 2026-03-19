package com.tree.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.dto.AppealQueryDto;
import com.tree.dto.AppealSubmitDto;
import com.tree.dto.AppealUpdateDto;
import com.tree.entity.Appeal;

import java.util.List;

public interface AppealService extends IService<Appeal> {

    Long submitAppeal(AppealSubmitDto dto);

    List<Appeal> getUserAppeals(Long userId);

    List<Appeal> getHandlerAppeals(Long handlerId);

    boolean handleAppeal(AppealUpdateDto dto);

    Page<Appeal> page(AppealQueryDto queryDTO, Integer pageNum, Integer pageSize);

    /** 按 id 查询申诉，不存在则抛 NOT_FOUND（辅导员端用） */
    Appeal getByIdOrThrow(Long id);

    /** 按 id 查询申诉并校验归属，不存在 NOT_FOUND，非本人 FORBIDDEN（学生端用） */
    Appeal getByIdForStudent(Long appealId, Long studentId);
}
