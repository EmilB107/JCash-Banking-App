package com.jcash.app.transactions;

import com.jcash.app.transactions.dto.TransactionEntryResponse;
import com.jcash.app.users.User;
import com.jcash.app.users.UserNotFoundException;
import com.jcash.app.users.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionHistoryService {

    private final TransactionEntryRepository transactionEntryRepository;
    private final UserRepository userRepository;

    public TransactionHistoryService(
            TransactionEntryRepository transactionEntryRepository,
            UserRepository userRepository) {
        this.transactionEntryRepository = transactionEntryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionEntryResponse> findAuthenticatedUserHistory(
            String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        return transactionEntryRepository
                .findByAccountUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(TransactionEntryResponse::from)
                .toList();
    }
}
