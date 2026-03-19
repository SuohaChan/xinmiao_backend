package com.tree.context;


import com.tree.dto.CounselorDto;

/**
 * 咨询师信息持有者类
 * 用于在当前线程中存储和获取咨询师信息
 */
public class CounselorHolder {

    private static final ThreadLocal<CounselorDto> tl = new ThreadLocal<>();
    /**
     * 设置当前线程的咨询师信息
     * @param studentDto 咨询师信息对象
     */
    public static void setCounselor(CounselorDto studentDto) {
        tl.set(studentDto);
    }
    /**
     * 获取当前线程的咨询师信息
     * @return 咨询师信息对象，如果未设置则返回null
     */
    public static CounselorDto getCounselor() {
        return tl.get();
    }
    /**
     * 移除当前线程的咨询师信息
     */
    public static void removeCounselor() {
        tl.remove();
    }
}
