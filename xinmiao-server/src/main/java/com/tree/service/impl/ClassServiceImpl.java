package com.tree.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.entity.Clazz;
import com.tree.exception.BusinessException;
import com.tree.mapper.ClassMapper;
import com.tree.result.ErrorCode;
import com.tree.service.ClassService;
import org.springframework.stereotype.Service;

@Service
public class ClassServiceImpl extends ServiceImpl<ClassMapper, Clazz> implements ClassService {

    @Override
    public void updateClass(Clazz clazz) {
        if (clazz.getId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "id不能为空");
        }
        if (getById(clazz.getId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "班级不存在");
        }
        updateById(clazz);
    }
}