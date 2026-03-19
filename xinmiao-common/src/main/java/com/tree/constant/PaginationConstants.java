package com.tree.constant;

/**
 * 分页参数上限，防止 size 过大拖库或打满内存。
 */
public final class PaginationConstants {
    /** 单页最大条数 */
    public static final int MAX_PAGE_SIZE = 100;

    private PaginationConstants() {}
}
