package com.cresensolutions.leaveservice.service.Impl;

import com.cresensolutions.leaveservice.model.UserProfile;
import com.cresensolutions.leaveservice.repository.UserProfileRepository;
import com.cresensolutions.leaveservice.service.CachedUserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CachedUserProfileServiceImpl implements CachedUserProfileService {

    private final UserProfileRepository userProfileRepository;

    @Override
    public Optional<UserProfile> findByUsername(String username) {
        log.debug("Fetching user profile for username: {}", username);
        return userProfileRepository.findByUserNameIgnoreCase(username);
    }

    @Override
    public Optional<UserProfile> findById(Long id) {
        log.debug("Fetching user profile for id: {}", id);
        return userProfileRepository.findById(id);
    }

    @Override
    public List<UserProfile> findAllActiveUsers() {
        log.debug("Fetching all active users");
        // Filter active users from all users
        return userProfileRepository.findAll().stream()
            .filter(UserProfile::isActive)
            .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<UserProfile> findByRole(String role) {
        log.debug("Fetching users by role: {}", role);
        return userProfileRepository.findActiveByRole(role);
    }

    @Override
    public List<UserProfile> findByManager(String managerUsername) {
        log.debug("Fetching users by manager: {}", managerUsername);
        return userProfileRepository.findActiveByManagerUsername(managerUsername);
    }

    @Override
    public UserProfile save(UserProfile userProfile) {
        log.info("Saving user profile and evicting caches: {}", userProfile.getUserName());
        return userProfileRepository.save(userProfile);
    }

    @Override
    public void deleteByUsername(String username) {
        log.info("Deleting user and evicting caches: {}", username);
        userProfileRepository.findByUserNameIgnoreCase(username)
            .ifPresent(userProfileRepository::delete);
    }

    @Override
    public void clearAllUserCaches() {
        log.info("Clearing all user profile caches");
    }
}
