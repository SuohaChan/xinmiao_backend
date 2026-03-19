package com.tree.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tree.dto.CheckInStatusDto;
import com.tree.dto.CheckInWeekDto;
import com.tree.entity.CheckIn;

public interface CheckInService extends IService<CheckIn> {

    CheckInStatusDto checkInToday(Long studentId);

    CheckInWeekDto getWeekCheckStatus(Long studentId);
}
