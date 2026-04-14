package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.FieldDictionary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FieldDictionaryRepository extends JpaRepository<FieldDictionary, UUID> {
    List<FieldDictionary> findByDictionaryTypeOrderBySourceValueAsc(String dictionaryType);
}
