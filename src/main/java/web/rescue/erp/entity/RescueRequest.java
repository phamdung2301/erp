package web.rescue.erp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rescue_requests")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RescueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescuer_id")
    private User rescuer;

    @Column(name = "issue_desc", columnDefinition = "TEXT")
    private String issueDesc;

    @Column(name = "ai_diagnosis", columnDefinition = "TEXT")
    private String aiDiagnosis;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @Column(name = "customer_lat", precision = 10, scale = 8)
    private BigDecimal customerLat;

    @Column(name = "customer_lng", precision = 11, scale = 8)
    private BigDecimal customerLng;

    @Column(name = "customer_address")
    private String customerAddress;

    @Column(name = "base_price", precision = 15, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "extra_price", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal extraPrice = BigDecimal.ZERO;

    @Column(name = "otp_code", length = 6)
    private String otpCode;

    @Column(name = "surcharge", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal surcharge = BigDecimal.ZERO;

    @Column(name = "surge_multiplier", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal surgeMultiplier = BigDecimal.ONE;

    @Column(name = "cancellation_fee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cancellationFee = BigDecimal.ZERO;

    @Column(name = "eta_minutes")
    private Integer etaMinutes;

    @Column(name = "road_distance", precision = 8, scale = 2)
    private BigDecimal roadDistance;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // --- Relationship ---
    @OneToOne(mappedBy = "rescueRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Invoice invoice;
}
