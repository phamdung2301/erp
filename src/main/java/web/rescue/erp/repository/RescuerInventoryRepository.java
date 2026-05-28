package web.rescue.erp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.rescue.erp.entity.RescuerInventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RescuerInventoryRepository extends JpaRepository<RescuerInventory, UUID> {
    List<RescuerInventory> findByRescuerUserId(UUID rescuerId);
    Optional<RescuerInventory> findByRescuerUserIdAndPartName(UUID rescuerId, String partName);
}
