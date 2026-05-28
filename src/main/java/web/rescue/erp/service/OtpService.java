package web.rescue.erp.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory OTP service. Trong production, nên dùng Redis + SMS gateway.
 */
@Service
@Slf4j
public class OtpService {

    private final Map<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private static final long OTP_VALIDITY_MS = 5 * 60 * 1000; // 5 phút

    /**
     * Tạo và lưu OTP 6 chữ số cho phone number
     */
    public String generateOtp(String phone) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        otpStore.put(phone, new OtpEntry(otp, System.currentTimeMillis()));
        log.info("OTP generated for {}: {}", phone, otp);
        // TODO: Gửi OTP qua SMS gateway thực tế
        return otp;
    }

    /**
     * Xác minh OTP
     */
    public boolean verifyOtp(String phone, String otp) {
        OtpEntry entry = otpStore.get(phone);
        if (entry == null) {
            return false;
        }
        // Kiểm tra hết hạn
        if (System.currentTimeMillis() - entry.createdAt > OTP_VALIDITY_MS) {
            otpStore.remove(phone);
            return false;
        }
        // Kiểm tra mã
        if (entry.otp.equals(otp)) {
            otpStore.remove(phone);
            return true;
        }
        return false;
    }

    /**
     * Tạo OTP cho hoàn thành chuyến cứu hộ
     */
    public String generateCompletionOtp(String requestId) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        otpStore.put("REQ_" + requestId, new OtpEntry(otp, System.currentTimeMillis()));
        log.info("Completion OTP for request {}: {}", requestId, otp);
        return otp;
    }

    public boolean verifyCompletionOtp(String requestId, String otp) {
        return verifyOtp("REQ_" + requestId, otp);
    }

    private record OtpEntry(String otp, long createdAt) {}
}
