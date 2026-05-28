package web.rescue.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rescuer_inventory")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RescuerInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "inventory_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescuer_id", nullable = false)
    private User rescuer;

    @Column(name = "part_name", nullable = false)
    private String partName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;
}
