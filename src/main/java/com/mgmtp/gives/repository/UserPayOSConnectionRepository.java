package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.UserPayOSConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPayOSConnectionRepository extends JpaRepository<UserPayOSConnection, Long> {
    Optional<UserPayOSConnection> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);

    java.util.List<UserPayOSConnection> findByUserIdIn(java.util.List<Long> userIds);
}
