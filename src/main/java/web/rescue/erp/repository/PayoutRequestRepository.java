package web.rescue.erp.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.rescue.erp.entity.PayoutRequest;
import web.rescue.erp.entity.enums.PayoutStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    @EntityGraph(attributePaths = {"rescuer"})
    List<PayoutRequest> findByStatusOrderByCreatedAtDesc(PayoutStatus status);

    @EntityGraph(attributePaths = {"rescuer"})
    List<PayoutRequest> findByRescuerUserIdOrderByCreatedAtDesc(UUID rescuerId);

    long countByStatus(PayoutStatus status);
}
