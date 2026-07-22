package com.mgmtp.gives.security;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.entity.User;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepo;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User currentUser;
        try {
            currentUser = userRepo.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        } catch (UsernameNotFoundException ex) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, "User not found with email: " + email);
        }

        return new CustomUserDetails(currentUser);
    }
}
