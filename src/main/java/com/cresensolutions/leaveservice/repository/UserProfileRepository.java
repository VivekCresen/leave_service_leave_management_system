package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Query(value = "SELECT * FROM user_schema.user_profile WHERE user_name = :userName LIMIT 1", nativeQuery = true)
    Optional<UserProfile> findByUserName(@Param("userName") String userName);

    @Query(value = "SELECT * FROM user_schema.user_profile WHERE LOWER(user_name) = LOWER(:userName) LIMIT 1", nativeQuery = true)
    Optional<UserProfile> findByUserNameIgnoreCase(@Param("userName") String userName);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM user_schema.user_profile WHERE id = :id)", nativeQuery = true)
    boolean existsById(@Param("id") Long id);

    @Query(value = "SELECT * FROM user_schema.user_profile WHERE LOWER(created_by) = LOWER(:managerUsername) AND active = true", nativeQuery = true)
    List<UserProfile> findActiveByManagerUsername(@Param("managerUsername") String managerUsername);

    @Query(value = "SELECT * FROM user_schema.user_profile WHERE LOWER(role) = LOWER(:role) AND active = true", nativeQuery = true)
    List<UserProfile> findActiveByRole(@Param("role") String role);

    @Query(value = "SELECT * FROM user_schema.user_profile WHERE active = true AND id != :excludeId", nativeQuery = true)
    List<UserProfile> findAllActiveExcept(@Param("excludeId") Long excludeId);
}
