package com.jcash.app.accounts;

import com.jcash.app.accounts.dto.AccountResponse;
import com.jcash.app.accounts.dto.CashInRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/me")
    public AccountResponse currentAccount(Authentication authentication) {
        return toResponse(
                accountService.findAuthenticatedUserAccount(authentication.getName())
        );
    }

    @PostMapping("/me/cash-in")
    public AccountResponse cashIn(
            Authentication authentication,
            @Valid @RequestBody CashInRequest request) {

        return toResponse(
                accountService.cashIn(authentication.getName(), request.amount())
        );
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
