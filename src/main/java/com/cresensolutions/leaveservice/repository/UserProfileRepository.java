package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Query(value = "SELECT * FROM user_profile WHERE user_name = :userName LIMIT 1", nativeQuery = true)
    Optional<UserProfile> findByUserName(@Param("userName") String userName);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM user_profile WHERE id = :id)", nativeQuery = true)
    boolean existsById(@Param("id") Long id);
}
