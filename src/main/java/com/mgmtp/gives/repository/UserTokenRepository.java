package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.entity.UserToken;
import com.mgmtp.gives.enums.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findByTokenHashAndType(String tokenHash, TokenType type);

    @Modifying
    @Query("""
                UPDATE UserToken ut
                SET ut.usedAt = CURRENT_TIMESTAMP
                WHERE ut.user = :user
                  AND ut.type = :type
                  AND ut.usedAt IS NULL
            """)
    int revokeAllByUserAndType(@Param("user") User user, @Param("type") TokenType type);
}
