package com.citybus.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "stops")
@Getter
@Setter
public class Stop {
    @Id
    private UUID id;

    @Column(name = "stop_name", nullable = false)
    private String stopName;

    @Column(name = "stop_name_pinyin")
    private String stopNamePinyin;

    @Column(name = "stop_initials")
    private String stopInitials;

    @Column(name = "keyword_blob")
    private String keywordBlob;

    @Column
    private String address;

    @Column(nullable = false)
    private int popularity;

    @Column(nullable = false)
    private boolean active;
}
