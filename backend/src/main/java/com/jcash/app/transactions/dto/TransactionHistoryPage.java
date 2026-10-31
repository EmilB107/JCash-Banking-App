package com.jcash.app.transactions.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record TransactionHistoryPage(
        List<TransactionEntryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static TransactionHistoryPage from(Page<TransactionEntryResponse> page) {
        return new TransactionHistoryPage(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
