package com.enterpriseai.knowledge.service;

import com.enterpriseai.knowledge.domain.AppUser;
import com.enterpriseai.knowledge.domain.Role;
import com.enterpriseai.knowledge.domain.Workspace;
import com.enterpriseai.knowledge.dto.WorkspaceDtos.CreateWorkspaceRequest;
import com.enterpriseai.knowledge.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WorkspaceServiceTest {
    @Test
    void normalUserCannotAccessAnotherUsersWorkspace() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        WorkspaceService service = new WorkspaceService(repository);
        AppUser requester = user(Role.USER);
        AppUser otherOwner = user(Role.USER);
        Workspace foreign = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Foreign")
                .owner(otherOwner)
                .build();
        when(repository.findWithOwnerById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.requireAccessible(requester, foreign.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Workspace not found");
    }

    @Test
    void administratorCanAccessAnotherUsersWorkspace() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        WorkspaceService service = new WorkspaceService(repository);
        AppUser admin = user(Role.ADMIN);
        Workspace foreign = Workspace.builder()
                .id(UUID.randomUUID())
                .name("Foreign")
                .owner(user(Role.USER))
                .build();
        when(repository.findWithOwnerById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThat(service.requireAccessible(admin, foreign.getId())).isSameAs(foreign);
    }

    @Test
    void normalUserListsOnlyOwnedWorkspaces() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        WorkspaceService service = new WorkspaceService(repository);
        AppUser owner = user(Role.USER);
        Workspace owned = workspace(owner, "Owned");
        when(repository.findAllByOwnerIdOrderByCreatedAtAsc(owner.getId()))
                .thenReturn(List.of(owned));

        var result = service.listAccessible(owner);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(owned.getId());
            assertThat(item.ownerId()).isEqualTo(owner.getId());
        });
        verify(repository, never()).findAllByOrderByCreatedAtAsc();
    }

    @Test
    void createRejectsDuplicateWorkspaceNameIgnoringCase() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        WorkspaceService service = new WorkspaceService(repository);
        AppUser owner = user(Role.USER);
        when(repository.existsByOwnerIdAndNameIgnoreCase(owner.getId(), "Finance"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(owner, new CreateWorkspaceRequest(" Finance ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
        verify(repository, never()).save(any());
    }

    @Test
    void ensureDefaultDoesNotCreateDuplicateWorkspace() {
        WorkspaceRepository repository = mock(WorkspaceRepository.class);
        WorkspaceService service = new WorkspaceService(repository);
        AppUser owner = user(Role.USER);
        Workspace existing = workspace(owner, "Existing");
        when(repository.findAllByOwnerIdOrderByCreatedAtAsc(owner.getId()))
                .thenReturn(List.of(existing));

        assertThat(service.ensureDefault(owner)).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    private Workspace workspace(AppUser owner, String name) {
        return Workspace.builder()
                .id(UUID.randomUUID())
                .name(name)
                .owner(owner)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private AppUser user(Role role) {
        return AppUser.builder()
                .id(UUID.randomUUID())
                .name("User")
                .email(UUID.randomUUID() + "@example.com")
                .role(role)
                .build();
    }
}
