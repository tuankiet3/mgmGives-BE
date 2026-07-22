package com.mgmtp.gives.common;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    /**
     * Factory method to construct PageResponse from a Spring Page object
     * and already mapped DTO contents.
     */
    public static <S, T> PageResponse<T> of(Page<S> springPage, List<T> mappedContent) {
        return new PageResponse<>(
                mappedContent,
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast()
        );
    }
}
