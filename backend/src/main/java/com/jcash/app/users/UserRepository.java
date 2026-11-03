package com.jcash.app.users;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    boolean existsByMobileNumber(String mobileNumber);
    Optional<User> findByMobileNumber(String mobileNumber);

    // "Taken by someone else": when editing a profile, the user's own email and
    // mobile number must not count as duplicates.
    boolean existsByEmailAndIdNot(String email, UUID id);
    boolean existsByMobileNumberAndIdNot(String mobileNumber, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.mobileNumber = :mobileNumber")
    Optional<User> findByMobileNumberForUpdate(@Param("mobileNumber") String mobileNumber);
}
