package web.rescue.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rescuer_profiles")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class RescuerProfile {

    @Id
    @Column(name = "rescuer_id")
    private UUID rescuerId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "rescuer_id")
    private User user;

    @Column(name = "license_url")
    private String licenseUrl;

    @Column(name = "id_card_url")
    private String idCardUrl;

    @Column(name = "is_online")
    @Builder.Default
    private boolean isOnline = false;

    @Column(name = "current_lat", precision = 10, scale = 8)
    private BigDecimal currentLat;

    @Column(name = "current_lng", precision = 11, scale = 8)
    private BigDecimal currentLng;

    @Column(name = "wallet_balance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal walletBalance = BigDecimal.ZERO;

    @Column(name = "cash_debt", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cashDebt = BigDecimal.ZERO;

    @Column(name = "verified")
    @Builder.Default
    private boolean verified = false;

    @Column(name = "specialty", length = 200)
    private String specialty;

    @Column(name = "service_area", length = 200)
    private String serviceArea;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_no", length = 50)
    private String bankAccountNo;

    @Column(name = "bank_account_name", length = 100)
    private String bankAccountName;
}
