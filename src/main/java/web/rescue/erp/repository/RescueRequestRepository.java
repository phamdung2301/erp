package web.rescue.erp.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.enums.RequestStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface RescueRequestRepository extends JpaRepository<RescueRequest, UUID> {

    @EntityGraph(attributePaths = {"customer", "rescuer", "invoice"})
    List<RescueRequest> findByCustomerUserIdOrderByCreatedAtDesc(UUID customerId);

    @EntityGraph(attributePaths = {"customer", "rescuer", "invoice"})
    List<RescueRequest> findByRescuerUserIdOrderByCreatedAtDesc(UUID rescuerId);

    @EntityGraph(attributePaths = {"customer", "rescuer", "invoice"})
    List<RescueRequest> findByStatus(RequestStatus status);

    @EntityGraph(attributePaths = {"customer", "rescuer", "invoice"})
    List<RescueRequest> findByStatusIn(List<RequestStatus> statuses);

    long countByStatus(RequestStatus status);
}
