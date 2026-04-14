package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.ParsingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParsingTemplateRepository extends JpaRepository<ParsingTemplate, UUID> {
    List<ParsingTemplate> findByTemplateTypeOrderByUpdatedAtDesc(String templateType);
    List<ParsingTemplate> findByTemplateNameIgnoreCaseOrderByRevisionDesc(String templateName);
}
