package com.example.userauth.repository;

import com.example.userauth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("SELECT t FROM PasswordResetToken t WHERE t.userId = :userId AND t.used = false")
    List<PasswordResetToken> findActiveTokensByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.userId = :userId AND t.used = false")
    void invalidateActiveTokensForUser(@Param("userId") UUID userId);
}
