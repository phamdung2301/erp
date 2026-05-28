package web.rescue.erp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.rescue.erp.entity.PayoutRequest;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.PayoutStatus;
import web.rescue.erp.repository.PayoutRequestRepository;
import web.rescue.erp.repository.RescuerProfileRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRequestRepository payoutRequestRepository;
    private final RescuerProfileRepository rescuerProfileRepository;

    @Transactional
    public PayoutRequest createPayoutRequest(User rescuer, BigDecimal amount) {
        return createPayoutRequest(rescuer, amount, null, null, null);
    }

    @Transactional
    public PayoutRequest createPayoutRequest(User rescuer, BigDecimal amount, String bankName, String bankAccountNo, String bankAccountName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Số tiền rút phải lớn hơn 0!");
        }

        RescuerProfile profile = rescuerProfileRepository.findById(rescuer.getUserId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));

        if (profile.getWalletBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Số dư ví không đủ để thực hiện giao dịch!");
        }

        // Tạm trừ tiền khỏi ví
        profile.setWalletBalance(profile.getWalletBalance().subtract(amount));

        // Cập nhật cấu hình ngân hàng vào profile nếu có
        if (bankName != null && !bankName.trim().isEmpty()) {
            profile.setBankName(bankName.trim());
        }
        if (bankAccountNo != null && !bankAccountNo.trim().isEmpty()) {
            profile.setBankAccountNo(bankAccountNo.trim());
        }
        if (bankAccountName != null && !bankAccountName.trim().isEmpty()) {
            profile.setBankAccountName(bankAccountName.trim().toUpperCase());
        }
        rescuerProfileRepository.save(profile);

        PayoutRequest request = PayoutRequest.builder()
                .rescuer(rescuer)
                .amount(amount)
                .status(PayoutStatus.PENDING)
                .bankName(bankName != null && !bankName.trim().isEmpty() ? bankName.trim() : profile.getBankName())
                .bankAccountNo(bankAccountNo != null && !bankAccountNo.trim().isEmpty() ? bankAccountNo.trim() : profile.getBankAccountNo())
                .bankAccountName(bankAccountName != null && !bankAccountName.trim().isEmpty() ? bankAccountName.trim().toUpperCase() : profile.getBankAccountName())
                .build();

        return payoutRequestRepository.save(request);
    }

    @Transactional
    public PayoutRequest approvePayout(UUID payoutId) {
        PayoutRequest request = payoutRequestRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu rút tiền!"));

        if (request.getStatus() != PayoutStatus.PENDING) {
            throw new RuntimeException("Yêu cầu rút tiền không ở trạng thái chờ duyệt!");
        }

        request.setStatus(PayoutStatus.APPROVED);
        request.setProcessedAt(LocalDateTime.now());
        return payoutRequestRepository.save(request);
    }

    @Transactional
    public PayoutRequest rejectPayout(UUID payoutId) {
        PayoutRequest request = payoutRequestRepository.findById(payoutId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu rút tiền!"));

        if (request.getStatus() != PayoutStatus.PENDING) {
            throw new RuntimeException("Yêu cầu rút tiền không ở trạng thái chờ duyệt!");
        }

        // Hoàn tiền về ví thợ
        RescuerProfile profile = rescuerProfileRepository.findById(request.getRescuer().getUserId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));

        profile.setWalletBalance(profile.getWalletBalance().add(request.getAmount()));
        rescuerProfileRepository.save(profile);

        request.setStatus(PayoutStatus.REJECTED);
        request.setProcessedAt(LocalDateTime.now());
        return payoutRequestRepository.save(request);
    }

    public List<PayoutRequest> findAll() {
        return payoutRequestRepository.findAll();
    }

    public List<PayoutRequest> findByStatus(PayoutStatus status) {
        return payoutRequestRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public List<PayoutRequest> findByRescuer(UUID rescuerId) {
        return payoutRequestRepository.findByRescuerUserIdOrderByCreatedAtDesc(rescuerId);
    }

    public long getPendingCount() {
        return payoutRequestRepository.countByStatus(PayoutStatus.PENDING);
    }
}
