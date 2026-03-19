package com.tree.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.entity.Clazz;

public interface ClassService extends IService<Clazz> {

    /** 更新班级（id 必填，不存在则抛 NOT_FOUND） */
    void updateClass(Clazz clazz);
}