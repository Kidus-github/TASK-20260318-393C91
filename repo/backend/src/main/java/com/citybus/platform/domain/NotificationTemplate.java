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
@Table(name = "notification_templates")
@Getter
@Setter
public class NotificationTemplate {
    @Id
    private UUID id;

    @Column(name = "template_key", nullable = false, unique = true)
    private String templateKey;

    @Column(name = "title_template", nullable = false)
    private String titleTemplate;

    @Column(name = "content_template", nullable = false)
    private String contentTemplate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
