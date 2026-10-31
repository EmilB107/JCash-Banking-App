package com.jcash.app.accounts;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByUserId(UUID userId);

    @Query("select account.id from Account account where account.user.email = :email")
    Optional<UUID> findIdByUserEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.user.id = :userId")
    Optional<Account> findByUserIdForUpdate(@Param("userId") UUID userId);
}
