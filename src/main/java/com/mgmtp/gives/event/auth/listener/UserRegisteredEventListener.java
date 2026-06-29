package com.mgmtp.gives.event.auth.listener;

import com.mgmtp.gives.event.auth.UserRegisteredEvent;
import com.mgmtp.gives.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        try {
            emailService.sendVerificationEmail(
                    event.email(),
                    event.fullName(),
                    event.rawToken()
            );
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", event.email(), e);
        }
    }
}