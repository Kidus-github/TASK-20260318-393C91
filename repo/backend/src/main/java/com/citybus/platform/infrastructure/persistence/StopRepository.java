package com.citybus.platform.infrastructure.persistence;

import com.citybus.platform.domain.Stop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StopRepository extends JpaRepository<Stop, UUID> {
    @Query("""
            select s from Stop s
            where s.active = true
              and (lower(s.stopName) like lower(concat('%', :query, '%'))
                   or lower(coalesce(s.keywordBlob, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(s.stopNamePinyin, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(s.stopInitials, '')) like lower(concat('%', :query, '%')))
            order by s.popularity desc
            """)
    List<Stop> search(@Param("query") String query);
}
