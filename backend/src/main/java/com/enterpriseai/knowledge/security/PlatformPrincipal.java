package com.enterpriseai.knowledge.security;

import com.enterpriseai.knowledge.domain.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record PlatformPrincipal(
        UUID userId,
        String username,
        String password,
        Collection<? extends GrantedAuthority> authorities
) implements UserDetails {
    public static PlatformPrincipal from(AppUser user) {
        GrantedAuthority authority = () -> "ROLE_" + user.getRole().name();
        return new PlatformPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                List.of(authority));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
