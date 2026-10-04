package com.lzit.campus.common;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 分页返回结构
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private long page;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(long total, long page, long size, List<T> records) {
        PageResult<T> p = new PageResult<>();
        p.setTotal(total);
        p.setPage(page);
        p.setSize(size);
        p.setRecords(records);
        return p;
    }
}
