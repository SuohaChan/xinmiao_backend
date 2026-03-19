package com.tree.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.entity.College;
import com.tree.exception.BusinessException;
import com.tree.mapper.CollegeMapper;
import com.tree.result.ErrorCode;
import com.tree.service.CollegeService;
import org.springframework.stereotype.Service;

@Service
public class CollegeServiceImpl extends ServiceImpl<CollegeMapper, College> implements CollegeService {

    @Override
    public void updateCollege(College college) {
        if (college.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "id不能为空");
        }
        if (getById(college.getId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "学院不存在");
        }
        updateById(college);
    }
}