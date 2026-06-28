package com.enterpriseai.knowledge.config;

import com.enterpriseai.knowledge.domain.AppUser;
import com.enterpriseai.knowledge.domain.Role;
import com.enterpriseai.knowledge.repository.UserRepository;
import com.enterpriseai.knowledge.service.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdmin implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final WorkspaceService workspaces;
    private final AppProperties.BootstrapAdmin config;

    public BootstrapAdmin(
            UserRepository users,
            PasswordEncoder encoder,
            WorkspaceService workspaces,
            AppProperties properties
    ) {
        this.users = users;
        this.encoder = encoder;
        this.workspaces = workspaces;
        this.config = properties.bootstrapAdmin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (config.email() == null || config.email().isBlank()
                || config.password() == null || config.password().isBlank()) return;
        AppUser admin = users.findByEmailIgnoreCase(config.email()).orElseGet(() -> {
            log.info("Creating configured bootstrap administrator");
            return users.save(AppUser.builder()
                    .name(config.name())
                    .email(config.email().trim().toLowerCase())
                    .passwordHash(encoder.encode(config.password()))
                    .role(Role.ADMIN)
                    .build());
        });
        workspaces.ensureDefault(admin);
    }
}
