package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {
    @Query("""
            select r from Route r
            where r.active = true
              and (lower(r.routeNumber) like lower(concat('%', :query, '%'))
                   or lower(r.routeName) like lower(concat('%', :query, '%')))
            """)
    List<Route> search(@Param("query") String query);
}
