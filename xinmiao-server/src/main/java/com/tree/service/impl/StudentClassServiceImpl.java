package com.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.entity.StudentClass;
import org.springframework.stereotype.Service;
import com.tree.mapper.StudentClassMapper;
import com.tree.service.StudentClassService;

/**
 * @author SuohaChan
 * @data 2025/9/13
 */

@Service
public class StudentClassServiceImpl extends ServiceImpl<StudentClassMapper, StudentClass> implements StudentClassService {
    @Override
    public StudentClass searchClassByStudentId(Long id) {
        if (id == null) {
            return null;
        }
        LambdaQueryWrapper<StudentClass> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StudentClass::getStudentId, id);
        return baseMapper.selectOne(queryWrapper);
    }
}
