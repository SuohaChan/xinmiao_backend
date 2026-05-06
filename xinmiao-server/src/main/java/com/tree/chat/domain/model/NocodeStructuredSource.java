package com.tree.chat.domain.model;

/**
 * {@code classpath:nocode/} 下<strong>已约定专用切段</strong>的源文档；未列入此枚举的 txt 均走通用中文滑窗切块。
 */
public enum NocodeStructuredSource {

    /** 《学生手册》：连续不少于 5 个空行为段界，段内不再按字数二次切分 */
    STUDENT_HANDBOOK("学生手册.txt"),

    /** Markdown 小节（###）+ 列表项 */
    UNIVERSITY_PROFILE("university.txt");

    private final String fileName;

    NocodeStructuredSource(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }

    public boolean matches(String resourceFileName) {
        return fileName.equals(resourceFileName);
    }
}
