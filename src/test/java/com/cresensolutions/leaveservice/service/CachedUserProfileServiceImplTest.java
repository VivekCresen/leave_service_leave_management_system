package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import com.cresensolutions.leaveservice.service.Impl.CachedUserProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachedUserProfileServiceImplTest {

    @Mock private UserProfileRepository userProfileRepository;

    @InjectMocks private CachedUserProfileServiceImpl service;

    private UserProfile activeUser;
    private UserProfile inactiveUser;

    @BeforeEach
    void setUp() throws Exception {
        activeUser   = buildUser(1L, "john", "EMPLOYEE", true);
        inactiveUser = buildUser(2L, "jane", "EMPLOYEE", false);
    }

    // ── findByUsername ────────────────────────────────────────────────────────

    @Test
    void findByUsername_found_returnsUser() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(activeUser));

        Optional<UserProfile> result = service.findByUsername("john");

        assertThat(result).contains(activeUser);
        verify(userProfileRepository).findByUserNameIgnoreCase("john");
    }

    @Test
    void findByUsername_notFound_returnsEmpty() {
        when(userProfileRepository.findByUserNameIgnoreCase("ghost")).thenReturn(Optional.empty());

        Optional<UserProfile> result = service.findByUsername("ghost");

        assertThat(result).isEmpty();
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsUser() {
        when(userProfileRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        Optional<UserProfile> result = service.findById(1L);

        assertThat(result).contains(activeUser);
        verify(userProfileRepository).findById(1L);
    }

    @Test
    void findById_notFound_returnsEmpty() {
        when(userProfileRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<UserProfile> result = service.findById(99L);

        assertThat(result).isEmpty();
    }

    // ── findAllActiveUsers ────────────────────────────────────────────────────

    @Test
    void findAllActiveUsers_mixedUsers_returnsOnlyActive() {
        when(userProfileRepository.findAll()).thenReturn(List.of(activeUser, inactiveUser));

        List<UserProfile> result = service.findAllActiveUsers();

        assertThat(result).containsExactly(activeUser);
        verify(userProfileRepository).findAll();
    }

    @Test
    void findAllActiveUsers_allActive_returnsAll() {
        UserProfile anotherActive = buildUser(3L, "bob", "MANAGER", true);
        when(userProfileRepository.findAll()).thenReturn(List.of(activeUser, anotherActive));

        List<UserProfile> result = service.findAllActiveUsers();

        assertThat(result).hasSize(2).containsExactlyInAnyOrder(activeUser, anotherActive);
    }

    @Test
    void findAllActiveUsers_noneActive_returnsEmpty() {
        when(userProfileRepository.findAll()).thenReturn(List.of(inactiveUser));

        List<UserProfile> result = service.findAllActiveUsers();

        assertThat(result).isEmpty();
    }

    @Test
    void findAllActiveUsers_emptyRepository_returnsEmpty() {
        when(userProfileRepository.findAll()).thenReturn(List.of());

        List<UserProfile> result = service.findAllActiveUsers();

        assertThat(result).isEmpty();
    }

    // ── findByRole ────────────────────────────────────────────────────────────

    @Test
    void findByRole_returnsMatchingUsers() {
        when(userProfileRepository.findActiveByRole("EMPLOYEE")).thenReturn(List.of(activeUser));

        List<UserProfile> result = service.findByRole("EMPLOYEE");

        assertThat(result).containsExactly(activeUser);
        verify(userProfileRepository).findActiveByRole("EMPLOYEE");
    }

    @Test
    void findByRole_noMatch_returnsEmpty() {
        when(userProfileRepository.findActiveByRole("ADMIN")).thenReturn(List.of());

        List<UserProfile> result = service.findByRole("ADMIN");

        assertThat(result).isEmpty();
    }

    // ── findByManager ─────────────────────────────────────────────────────────

    @Test
    void findByManager_returnsTeamMembers() {
        when(userProfileRepository.findActiveByManagerUsername("manager1")).thenReturn(List.of(activeUser));

        List<UserProfile> result = service.findByManager("manager1");

        assertThat(result).containsExactly(activeUser);
        verify(userProfileRepository).findActiveByManagerUsername("manager1");
    }

    @Test
    void findByManager_noTeam_returnsEmpty() {
        when(userProfileRepository.findActiveByManagerUsername("manager2")).thenReturn(List.of());

        List<UserProfile> result = service.findByManager("manager2");

        assertThat(result).isEmpty();
    }

    // ── save ──────────────────────────────────────────────────────────────────

    @Test
    void save_persistsAndReturnsUser() {
        when(userProfileRepository.save(activeUser)).thenReturn(activeUser);

        UserProfile result = service.save(activeUser);

        assertThat(result).isEqualTo(activeUser);
        verify(userProfileRepository).save(activeUser);
    }

    @Test
    void save_callsRepositorySaveExactlyOnce() {
        when(userProfileRepository.save(activeUser)).thenReturn(activeUser);

        service.save(activeUser);

        verify(userProfileRepository, times(1)).save(activeUser);
    }

    // ── deleteByUsername ──────────────────────────────────────────────────────

    @Test
    void deleteByUsername_userExists_deletesUser() {
        when(userProfileRepository.findByUserNameIgnoreCase("john")).thenReturn(Optional.of(activeUser));

        service.deleteByUsername("john");

        verify(userProfileRepository).findByUserNameIgnoreCase("john");
        verify(userProfileRepository).delete(activeUser);
    }

    @Test
    void deleteByUsername_userNotFound_doesNotDelete() {
        when(userProfileRepository.findByUserNameIgnoreCase("ghost")).thenReturn(Optional.empty());

        service.deleteByUsername("ghost");

        verify(userProfileRepository).findByUserNameIgnoreCase("ghost");
        verify(userProfileRepository, never()).delete(any());
    }

    // ── clearAllUserCaches ────────────────────────────────────────────────────

    @Test
    void clearAllUserCaches_doesNotThrow() {
        // No-op implementation — just verify it completes without error
        service.clearAllUserCaches();

        verifyNoInteractions(userProfileRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static UserProfile buildUser(Long id, String username, String role, boolean active) {
        UserProfile u = new UserProfile();
        setField(u, "id", id);
        setField(u, "userName", username);
        setField(u, "role", role);
        setField(u, "active", active);
        return u;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    f.set(target, value);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new RuntimeException("Field not found: " + name);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
