package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.domain.AppUser;
import com.enterpriseai.knowledge.domain.Role;
import com.enterpriseai.knowledge.dto.AuthDtos.LoginRequest;
import com.enterpriseai.knowledge.dto.AuthDtos.SignupRequest;
import com.enterpriseai.knowledge.repository.UserRepository;
import com.enterpriseai.knowledge.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private UserRepository users;
    private PasswordEncoder encoder;
    private AuthenticationManager authenticationManager;
    private JwtService jwt;
    private WorkspaceService workspaces;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        jwt = mock(JwtService.class);
        workspaces = mock(WorkspaceService.class);
        service = new AuthService(users, encoder, authenticationManager, jwt, workspaces);
    }

    @Test
    void signupNormalizesIdentityHashesPasswordAndCreatesDefaultWorkspace() {
        when(encoder.encode("correct-horse")).thenReturn("bcrypt-hash");
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(jwt.createToken(any(AppUser.class))).thenReturn("signed-token");

        var response = service.signup(new SignupRequest(
                "  Ada Lovelace  ", "  ADA@Example.COM ", "correct-horse"));

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.user().email()).isEqualTo("ada@example.com");
        assertThat(response.user().name()).isEqualTo("Ada Lovelace");
        assertThat(response.user().role()).isEqualTo(Role.USER);
        verify(encoder).encode("correct-horse");
        verify(workspaces).ensureDefault(any(AppUser.class));
    }

    @Test
    void signupRejectsDuplicateEmailWithoutHashingPassword() {
        when(users.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(
                new SignupRequest("Ada", "ada@example.com", "correct-horse")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void loginAuthenticatesNormalizedEmailAndReturnsCurrentDatabaseRole() {
        AppUser admin = AppUser.builder()
                .id(UUID.randomUUID())
                .name("Admin")
                .email("admin@example.com")
                .role(Role.ADMIN)
                .build();
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(jwt.createToken(admin)).thenReturn("admin-token");

        var response = service.login(new LoginRequest(" ADMIN@example.com ", "password"));

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("admin@example.com", "password"));
        assertThat(response.user().role()).isEqualTo(Role.ADMIN);
        assertThat(response.token()).isEqualTo("admin-token");
    }
}
