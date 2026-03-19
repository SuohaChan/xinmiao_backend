package com.tree.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.entity.StudentClass;

/**
 * @author SuohaChan
 * @data 2025/9/13
 */


public interface StudentClassService  extends IService<StudentClass> {
    StudentClass searchClassByStudentId(Long id);
}
