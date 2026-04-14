package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.EmailConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailConfigurationRepository extends JpaRepository<EmailConfiguration, Long> {

    Optional<EmailConfiguration> findFirstByActiveTrueOrderByIdAsc();
}
