package com.tree.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tree.entity.CourseSchedule;
import org.springframework.stereotype.Service;
import com.tree.mapper.CourseScheduleMapper;
import com.tree.service.StudentCourseService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentCourseImpl extends ServiceImpl<CourseScheduleMapper, CourseSchedule> implements StudentCourseService {
    // 根据学生 ID 获取对应课表数据
    public List<Map<String, Object>> getCourseScheduleByStudentId(Long studentId) {
        List<CourseSchedule> courseSchedules = this.lambdaQuery()
                .eq(CourseSchedule::getStudentId, studentId)
                .orderByAsc(CourseSchedule::getSortOrder) // 按时间排序
                .list();

        List<Map<String, Object>> schedule = new ArrayList<>();
        for (CourseSchedule courseSchedule : courseSchedules) {
            Map<String, Object> row = new HashMap<>();
            row.put("time", courseSchedule.getTimePeriod());
            row.put("monday", courseSchedule.getMonday());
            row.put("tuesday", courseSchedule.getTuesday());
            row.put("wednesday", courseSchedule.getWednesday());
            row.put("thursday", courseSchedule.getThursday());
            row.put("friday", courseSchedule.getFriday());
            schedule.add(row);
        }
        return schedule;
    }
}
