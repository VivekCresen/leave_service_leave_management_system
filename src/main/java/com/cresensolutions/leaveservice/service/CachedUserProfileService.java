package com.cresensolutions.leaveservice.service;

import com.cresensolutions.leaveservice.model.UserProfile;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.util.List;
import java.util.Optional;

public interface CachedUserProfileService {

    @Cacheable(value = "userByUsername", key = "#username.toLowerCase()")
    Optional<UserProfile> findByUsername(String username);

    @Cacheable(value = "userById", key = "#id")
    Optional<UserProfile> findById(Long id);

    @Cacheable(value = "activeUsers")
    List<UserProfile> findAllActiveUsers();

    @Cacheable(value = "usersByRole", key = "#role")
    List<UserProfile> findByRole(String role);

    @Cacheable(value = "usersByManager", key = "#managerUsername")
    List<UserProfile> findByManager(String managerUsername);

    @Caching(evict = {
        @CacheEvict(value = "userByUsername", key = "#userProfile.userName.toLowerCase()"),
        @CacheEvict(value = "userById", key = "#userProfile.id"),
        @CacheEvict(value = "activeUsers", allEntries = true),
        @CacheEvict(value = "usersByRole", allEntries = true),
        @CacheEvict(value = "usersByManager", allEntries = true)
    })
    UserProfile save(UserProfile userProfile);

    @Caching(evict = {
        @CacheEvict(value = "userByUsername", key = "#username.toLowerCase()"),
        @CacheEvict(value = "activeUsers", allEntries = true),
        @CacheEvict(value = "usersByRole", allEntries = true),
        @CacheEvict(value = "usersByManager", allEntries = true)
    })
    void deleteByUsername(String username);

    @Caching(evict = {
        @CacheEvict(value = "userByUsername", allEntries = true),
        @CacheEvict(value = "userById", allEntries = true),
        @CacheEvict(value = "activeUsers", allEntries = true),
        @CacheEvict(value = "usersByRole", allEntries = true),
        @CacheEvict(value = "usersByManager", allEntries = true)
    })
    void clearAllUserCaches();
}
