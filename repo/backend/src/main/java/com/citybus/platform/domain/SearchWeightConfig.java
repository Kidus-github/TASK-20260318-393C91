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
@Table(name = "search_weight_configs")
@Getter
@Setter
public class SearchWeightConfig {
    @Id
    private UUID id;

    @Column(name = "exact_weight", nullable = false)
    private int exactWeight;

    @Column(name = "prefix_weight", nullable = false)
    private int prefixWeight;

    @Column(name = "pinyin_weight", nullable = false)
    private int pinyinWeight;

    @Column(name = "popularity_weight", nullable = false)
    private int popularityWeight;

    @Column(name = "frequency_weight", nullable = false)
    private int frequencyWeight;

    @Column(nullable = false)
    private int revision;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
