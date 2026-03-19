package com.tree.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tree.dto.ClassTaskStatsDto;
import com.tree.dto.RankDto;
import com.tree.dto.TaskStatusItem;
import com.tree.entity.StudentTask;
import com.tree.entity.Task;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;


/**
* @author ljx
* @description 针对表【tb_student_task(学生和任务关系)】的数据库操作Mapper
* @createDate 2024-02-28 17:59:39
* @Entity xyz.rkgn.entity.StudentTask
*/
public interface StudentTaskMapper extends BaseMapper<StudentTask> {
    //批量插入
    void batchInsert(@Param("list") List<StudentTask> list);

    //查询未完成任务（按 collegeId/classId 匹配范围）
    List<Task> selectUnfinishedTasks(
            @Param("studentId") Long studentId,
            @Param("collegeId") Long collegeId,
            @Param("classId") Long classId
    );

    //查询学生对某个任务的完成状态（返回status值）
    @Select("SELECT status FROM tb_student_task " +
            "WHERE student_id = #{studentId} AND task_id = #{taskId}")
    Integer getTaskStatus(@Param("studentId") Long studentId, @Param("taskId") Long taskId);


    // 获取周排行榜
    List<RankDto> getWeekRank();

    // 获取学院排行榜（全量，不按时间）
    List<RankDto> getCollegeRank(@Param("college") String college);

    /** 学院学年榜：按学年时间范围（submit_time）汇总积分，用于定时写 Redis 与接口兜底 */
    List<RankDto> getCollegeRankByAcademicYear(
            @Param("college") String college,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /** 批量查询学生对一批任务的完成状态，避免 N+1 */
    List<TaskStatusItem> selectTaskStatusBatch(
            @Param("studentId") Long studentId,
            @Param("taskIds") List<Long> taskIds);

    /** 批量查询多任务下各班级完成情况（校级/院级/班级范围过滤在 SQL 内），避免 1+2N */
    List<ClassTaskStatsDto> selectClassStatsByTaskIds(@Param("taskIds") List<Long> taskIds);
}




