package com.citybus.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "routes")
@Getter
@Setter
public class Route {
    @Id
    private UUID id;

    @Column(name = "route_number", nullable = false)
    private String routeNumber;

    @Column(name = "route_name", nullable = false)
    private String routeName;

    @Column(name = "frequency_priority", nullable = false)
    private int frequencyPriority;

    @Column(nullable = false)
    private boolean active;
}
