package com.citybus.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "field_dictionaries")
@Getter
@Setter
public class FieldDictionary {
    @Id
    private UUID id;

    @Column(name = "dictionary_type", nullable = false)
    private String dictionaryType;

    @Column(name = "source_value", nullable = false)
    private String sourceValue;

    @Column(name = "standardized_value", nullable = false)
    private String standardizedValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
