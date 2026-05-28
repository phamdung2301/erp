package web.rescue.erp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.repository.RescuerProfileRepository;
import web.rescue.erp.websocket.WebSocketNotificationService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RescuerService {

    private final RescuerProfileRepository rescuerProfileRepository;
    private final PayoutService payoutService;
    private final WebSocketNotificationService webSocketNotificationService;

    public List<RescuerProfile> findPendingVerification() {
        return rescuerProfileRepository.findByVerifiedFalse();
    }

    public Optional<RescuerProfile> findById(UUID rescuerId) {
        return rescuerProfileRepository.findById(rescuerId);
    }

    @Transactional
    public void verifyRescuer(UUID rescuerId) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        profile.setVerified(true);
        rescuerProfileRepository.save(profile);
    }

    @Transactional
    public void rejectRescuer(UUID rescuerId) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        profile.setVerified(false);
        rescuerProfileRepository.save(profile);
    }

    @Transactional
    public void updateDocuments(UUID rescuerId, String licenseUrl, String idCardUrl) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        if (licenseUrl != null) profile.setLicenseUrl(licenseUrl);
        if (idCardUrl != null) profile.setIdCardUrl(idCardUrl);
        profile.setVerified(false); // reset verification khi upload mới
        rescuerProfileRepository.save(profile);
    }

    @Transactional
    public void toggleOnline(UUID rescuerId, boolean online) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        if (online && !profile.isVerified()) {
            throw new RuntimeException("Tài khoản chưa được duyệt xác minh, không thể bật online!");
        }
        profile.setOnline(online);
        rescuerProfileRepository.save(profile);
    }

    @Transactional
    public void updateLocation(UUID rescuerId, BigDecimal lat, BigDecimal lng) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        profile.setCurrentLat(lat);
        profile.setCurrentLng(lng);
        rescuerProfileRepository.save(profile);
    }

    @Transactional
    public void payoutRequest(UUID rescuerId, BigDecimal amount) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        payoutService.createPayoutRequest(profile.getUser(), amount);
    }

    @Transactional
    public void payoutRequest(UUID rescuerId, BigDecimal amount, String bankName, String bankAccountNo, String bankAccountName) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        payoutService.createPayoutRequest(profile.getUser(), amount, bankName, bankAccountNo, bankAccountName);
    }

    public long countPendingVerification() {
        return rescuerProfileRepository.countByVerifiedFalse();
    }

    public long countOnline() {
        return rescuerProfileRepository.countByIsOnlineTrue();
    }

    public List<RescuerProfile> findRescuersWithCashDebt() {
        return rescuerProfileRepository.findByCashDebtGreaterThan(BigDecimal.ZERO);
    }

    @Transactional
    public void clearCashDebt(UUID rescuerId) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        profile.setCashDebt(BigDecimal.ZERO);
        rescuerProfileRepository.save(profile);
    }

    public void sendPaymentReminder(UUID rescuerId) {
        RescuerProfile profile = rescuerProfileRepository.findById(rescuerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
        String phone = profile.getUser().getPhone();
        
        BigDecimal debtVal = profile.getCashDebt() != null ? profile.getCashDebt() : BigDecimal.ZERO;
        String formattedDebt = java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMANY).format(debtVal);
        
        String message = String.format("Yêu cầu thanh toán: Bạn đang giữ %s ₫ tiền mặt cứu hộ. Vui lòng nộp lại cho Admin sớm nhất có thể.", formattedDebt);
        webSocketNotificationService.notifyPaymentReminder(phone, message);
    }
}
