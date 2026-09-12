package com.amilingo.platform.common.config;

import com.amilingo.platform.common.config.security.AuthenticatedUser;
import com.amilingo.platform.common.util.JwtUtil;
import com.amilingo.platform.component.repository.UserRepository;
import com.amilingo.platform.module.user.entity.user.User;
import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    @Autowired
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable FilterChain filterChain) throws ServletException, IOException {
        String jwt = extractToken(request);
        if (jwt != null) {
            try {
                if (!jwtUtil.validateToken(jwt)) {
                    throw new SecurityException("JWT token validation failed (expired or invalid)");
                } else {
                    String username = jwtUtil.extractUserId(jwt);
                    Optional<User> userOpt = Optional.empty();

                    if (username != null) {
                        userOpt = userRepository.findUserById(Long.parseLong(username));
                    }
                    if (userOpt.isEmpty()) {
                        throw new SecurityException("JWT token valid but user not found: username="+ username);
                    } else {
                        User user = userOpt.get();
                        AuthenticatedUser authenticatedUser = AuthenticatedUser.builder()
                                .userId(user.getId())
                                .username(user.getUsername())
                                .password(user.getPasswordHash())
                                .role(null)
                                .build();

                        UsernamePasswordAuthenticationToken authenticationToken =
                                new UsernamePasswordAuthenticationToken(authenticatedUser, null, authenticatedUser.getAuthorities());
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                        log.debug("User authenticated: userId={}, username={}", user.getId(), user.getUsername());
                    }
                }
            } catch (Exception e) {
                log.error("JWT token processing failed", e);
            }
        }

        if (filterChain != null) {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 从 Authorization 头或 access_token cookie 中提取 JWT。
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 优先从 Authorization: Bearer xxx 头提取
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith(JwtUtil.BEARER_PREFIX)) {
            return authorizationHeader.substring(JwtUtil.BEARER_PREFIX.length());
        }

        // 2. 回退到 access_token cookie
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }
}