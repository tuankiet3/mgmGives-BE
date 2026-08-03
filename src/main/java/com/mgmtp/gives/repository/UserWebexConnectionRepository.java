package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.UserWebexConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserWebexConnectionRepository extends JpaRepository<UserWebexConnection, Long> {
    Optional<UserWebexConnection> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    void deleteByUserId(Long userId);
}
