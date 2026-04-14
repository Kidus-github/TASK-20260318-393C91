package com.citybus.platform.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface RouteStopRepository extends JpaRepository<com.citybus.platform.domain.Route, UUID> {
    @Query(value = "select count(*) > 0 from route_stops where route_id = :routeId and stop_id = :stopId", nativeQuery = true)
    boolean existsLink(@Param("routeId") UUID routeId, @Param("stopId") UUID stopId);

    @Query(value = "select count(*) from route_stops", nativeQuery = true)
    long countLinks();

    @Modifying
    @Transactional
    @Query(value = "insert into route_stops(route_id, stop_id) values (:routeId, :stopId)", nativeQuery = true)
    void insertLink(@Param("routeId") UUID routeId, @Param("stopId") UUID stopId);
}
