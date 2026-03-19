package com.tree.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.entity.College;

public interface CollegeService extends IService<College> {

    /** 更新学院（id 必填，不存在则抛 NOT_FOUND） */
    void updateCollege(College college);
}