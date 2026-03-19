package com.tree.context;


import com.tree.dto.StudentDto;

public class StudentHolder {
    private static final ThreadLocal<StudentDto> tl = new ThreadLocal<>();

    public static void setStudent(StudentDto studentDto) {
        tl.set(studentDto);
    }

    public static StudentDto getStudent() {
        return tl.get();
    }
    public static void removeStudent() {
        tl.remove();
    }
}
