package web.rescue.erp.entity;

import jakarta.persistence.*;
import lombok.*;
import web.rescue.erp.entity.enums.PayoutStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payout_requests")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class PayoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payout_id")
    private UUID payoutId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescuer_id", nullable = false)
    private User rescuer;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_no", length = 50)
    private String bankAccountNo;

    @Column(name = "bank_account_name", length = 100)
    private String bankAccountName;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
