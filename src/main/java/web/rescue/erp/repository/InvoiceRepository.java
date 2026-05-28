package web.rescue.erp.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.rescue.erp.entity.Invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Override
    @EntityGraph(attributePaths = {"rescueRequest", "rescueRequest.customer", "rescueRequest.rescuer"})
    List<Invoice> findAll();

    Optional<Invoice> findByRescueRequestRequestId(UUID requestId);
}
