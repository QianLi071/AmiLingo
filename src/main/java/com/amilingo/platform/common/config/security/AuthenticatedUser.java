package com.amilingo.platform.common.config.security;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser implements UserDetails {
    private Long userId;
    private String username;
    private String password;
    private String role;
    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role != null
                ? List.of(new SimpleGrantedAuthority(role))
                : Collections.emptyList();
    }
    @Override
    public String getPassword() {
        return password;
    }

    @Override
    @NonNull
    public String getUsername() {
        return username;
    }

    @Override
    @NotNull
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @NotNull
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @NotNull
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @NotNull
    public boolean isEnabled() {
        return true;
    }
}
