package com.jcash.app.transactions;

import com.jcash.app.transactions.dto.TransactionEntryResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionHistoryController {

    private final TransactionHistoryService transactionHistoryService;

    public TransactionHistoryController(
            TransactionHistoryService transactionHistoryService) {
        this.transactionHistoryService = transactionHistoryService;
    }

    @GetMapping("/me")
    public List<TransactionEntryResponse> currentUserHistory(
            Authentication authentication) {
        return transactionHistoryService.findAuthenticatedUserHistory(
                authentication.getName()
        );
    }
}
