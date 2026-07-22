package com.mgmtp.gives.security;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.enums.UserStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.util.CookieUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Autowired
    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        final String accessToken = CookieUtils.getCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE_NAME)
                .orElse(null);

        if (!StringUtils.hasText(accessToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.extractClaimsFromToken(accessToken);
            String userEmail = claims.getSubject();

            if (StringUtils.hasText(userEmail) && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (userDetails instanceof CustomUserDetails customUserDetails) {
                    validateUserStatus(customUserDetails, request.getServletPath());
                }

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute("jwt_exception", ex);
        }

        filterChain.doFilter(request, response);
    }

    private void validateUserStatus(CustomUserDetails customUserDetails, String path) {
        UserStatus status = customUserDetails.getUser().getStatus();
        if (UserStatus.BANNED.equals(status)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Your account has been banned.");
        } else if (UserStatus.INACTIVE.equals(status)) {
            if (!path.equals("/api/auth/resend-activation")
                    && !path.equals("/api/auth/me")
                    && !path.equals("/api/auth/logout")) {
                throw new AppException(ErrorCode.ACCOUNT_INACTIVE, "Your account is inactive. Please activate your account to proceed.");
            }
        }
    }
}
