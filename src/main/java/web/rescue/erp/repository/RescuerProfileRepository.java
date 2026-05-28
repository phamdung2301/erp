package web.rescue.erp.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import web.rescue.erp.entity.RescuerProfile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface RescuerProfileRepository extends JpaRepository<RescuerProfile, UUID> {

    @EntityGraph(attributePaths = {"user"})
    List<RescuerProfile> findByVerifiedFalse();

    @EntityGraph(attributePaths = {"user"})
    List<RescuerProfile> findByVerifiedTrue();

    @EntityGraph(attributePaths = {"user"})
    List<RescuerProfile> findByIsOnlineTrue();

    @EntityGraph(attributePaths = {"user"})
    List<RescuerProfile> findByCashDebtGreaterThan(BigDecimal value);

    @Query("SELECT r FROM RescuerProfile r WHERE r.isOnline = true AND r.verified = true " +
           "AND ABS(r.currentLat - :lat) < :range AND ABS(r.currentLng - :lng) < :range " +
           "AND (6371 * ACOS(COS(RADIANS(:lat)) * COS(RADIANS(r.currentLat)) * COS(RADIANS(r.currentLng) - RADIANS(:lng)) + SIN(RADIANS(:lat)) * SIN(RADIANS(r.currentLat)))) < 5.0")
    List<RescuerProfile> findNearbyOnlineRescuers(
            @Param("lat") BigDecimal lat,
            @Param("lng") BigDecimal lng,
            @Param("range") BigDecimal range);

    long countByVerifiedFalse();

    long countByIsOnlineTrue();
}
