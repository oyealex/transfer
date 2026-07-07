package com.ecommerce.common.dto;

import java.util.List;

/**
 * Generic paginated response DTO.
 * Used by all modules for list endpoints that support pagination.
 *
 * @param <T> the type of items in the page
 */
public class PageResponse<T> {

    private int page;
    private int size;
    private long total;
    private List<T> items;

    public PageResponse() {
    }

    public PageResponse(int page, int size, long total, List<T> items) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.items = items;
    }

    /**
     * Static factory method to create a PageResponse.
     *
     * @param page  the current page number (0-based)
     * @param size  the page size
     * @param total the total number of items
     * @param items the items on the current page
     * @param <T>   the item type
     * @return a new PageResponse instance
     */
    public static <T> PageResponse<T> of(int page, int size, long total, List<T> items) {
        return new PageResponse<>(page, size, total, items);
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }
}
