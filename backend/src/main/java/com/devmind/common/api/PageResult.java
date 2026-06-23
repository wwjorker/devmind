package com.devmind.common.api;

import java.util.List;

public class PageResult<T> {

    private long pageNo;
    private long pageSize;
    private long total;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long pageNo, long pageSize, long total, List<T> records) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        this.records = records;
    }

    public long getPageNo() {
        return pageNo;
    }

    public void setPageNo(long pageNo) {
        this.pageNo = pageNo;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}
