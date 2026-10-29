package com.jcash.app.transfers;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferIdempotencyKeyRepository
        extends JpaRepository<TransferIdempotencyKey, UUID> {

    Optional<TransferIdempotencyKey> findBySenderAccountIdAndIdempotencyKey(
            UUID senderAccountId,
            String idempotencyKey);
}
