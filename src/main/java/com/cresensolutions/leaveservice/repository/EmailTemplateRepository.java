package com.cresensolutions.leaveservice.repository;

import com.cresensolutions.leaveservice.model.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByTemplateTypeAndActiveTrue(String templateType);
}
