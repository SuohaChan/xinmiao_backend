package com.tree.dto;

import lombok.Data;

@Data
public class StudentDto {
    private Long id;
    private String nickName;
    private String avatar;
    private String type = "Student";
}
