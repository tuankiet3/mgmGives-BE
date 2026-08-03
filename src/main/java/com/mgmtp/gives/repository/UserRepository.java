package com.mgmtp.gives.repository;

import com.mgmtp.gives.dto.notification.NotificationRecipient;
import com.mgmtp.gives.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.mgmtp.gives.enums.UserRole;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);

    long countByRole(UserRole role);

    boolean existsByEmail(String email);

    List<User> findAllByEmailIn(Collection<String> emails);

    @Query("""
        SELECT new com.mgmtp.gives.dto.notification.NotificationRecipient(
            u.id,
            u.email
        )
        FROM User u
        WHERE u.id = :userId
        """)
    Optional<NotificationRecipient> findNotificationRecipientById(@Param("userId") Long userId);
}
