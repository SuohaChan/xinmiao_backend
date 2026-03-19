package com.tree.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tree.entity.StudentClass;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StudentClassMapper extends BaseMapper<StudentClass> {

    /** 查询所有非空学院名称（去重），用于学院榜兜底：从 DB 直接按学院聚合 */
    @Select("SELECT DISTINCT college FROM tb_student_class WHERE college IS NOT NULL AND college != ''")
    List<String> listDistinctColleges();
}