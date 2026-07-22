package com.mgmtp.gives.repository;

import com.mgmtp.gives.entity.WebexOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebexOAuthStateRepository extends JpaRepository<WebexOAuthState, Long> {
    Optional<WebexOAuthState> findByState(String state);
}
