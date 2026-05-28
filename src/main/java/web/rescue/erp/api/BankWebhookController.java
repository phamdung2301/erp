package web.rescue.erp.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import web.rescue.erp.dto.ApiResponse;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.repository.RescuerProfileRepository;
import web.rescue.erp.repository.UserRepository;
import web.rescue.erp.websocket.RescueWebSocketHandler;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/callback")
@RequiredArgsConstructor
@Slf4j
public class BankWebhookController {

    private final UserRepository userRepository;
    private final RescuerProfileRepository rescuerProfileRepository;
    private final RescueWebSocketHandler webSocketHandler;

    /**
     * Webhook Ngân hàng nhận callback chuyển tiền thanh toán nợ thợ cứu hộ
     */
    @PostMapping("/bank-transfer")
    @Transactional
    public ResponseEntity<ApiResponse> handleBankTransfer(@RequestBody Map<String, Object> payload) {
        log.info("Received Bank Webhook callback: {}", payload);
        try {
            if (!payload.containsKey("amount") || !payload.containsKey("content")) {
                throw new RuntimeException("Thiếu thông tin số tiền (amount) hoặc nội dung chuyển khoản (content)!");
            }

            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            String content = payload.get("content").toString().trim();

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Số tiền chuyển khoản phải lớn hơn 0!");
            }

            // Trích xuất số điện thoại từ nội dung chuyển khoản (Ví dụ: NOP TIEN 0902222222)
            Pattern pattern = Pattern.compile("(?i)NOP\\s+TIEN\\s+(\\d{9,11})");
            Matcher matcher = pattern.matcher(content);
            String phone = null;
            if (matcher.find()) {
                phone = matcher.group(1);
            }

            if (phone == null) {
                // Thử regex lỏng hơn tìm chuỗi số từ 9-11 số
                Pattern altPattern = Pattern.compile("(\\d{9,11})");
                Matcher altMatcher = altPattern.matcher(content);
                if (altMatcher.find()) {
                    phone = altMatcher.group(1);
                }
            }

            if (phone == null) {
                throw new RuntimeException("Nội dung chuyển khoản không hợp lệ, không tìm thấy số điện thoại thợ!");
            }

            // Tìm thợ cứu hộ
            String finalPhone = phone;
            User user = userRepository.findByPhone(phone)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thợ cứu hộ có số điện thoại: " + finalPhone));

            RescuerProfile profile = rescuerProfileRepository.findById(user.getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ cứu hộ!"));

            BigDecimal currentDebt = profile.getCashDebt() != null ? profile.getCashDebt() : BigDecimal.ZERO;
            if (currentDebt.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.ok(ApiResponse.ok("Thợ cứu hộ không có công nợ cần thanh toán. Giao dịch được ghi nhận!"));
            }

            BigDecimal excessAmount = BigDecimal.ZERO;
            BigDecimal newDebt = currentDebt.subtract(amount);

            if (newDebt.compareTo(BigDecimal.ZERO) < 0) {
                // Thợ nộp thừa tiền nợ -> Cộng phần thừa vào ví thợ, nợ về 0
                excessAmount = newDebt.abs();
                profile.setCashDebt(BigDecimal.ZERO);
                profile.setWalletBalance(profile.getWalletBalance().add(excessAmount));
                log.info("Rescuer {} paid excess {} VND. Added to wallet balance.", user.getFullName(), excessAmount);
            } else {
                profile.setCashDebt(newDebt);
            }

            rescuerProfileRepository.save(profile);
            log.info("Rescuer {} cash debt updated from {} to {}", user.getFullName(), currentDebt, profile.getCashDebt());

            // 1. Gửi thông báo WebSocket cho thợ cứu hộ
            String formattedAmount = java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMANY).format(amount);
            String formattedDebt = java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMANY).format(profile.getCashDebt());
            
            String rescuerMsg;
            if (profile.getCashDebt().compareTo(BigDecimal.ZERO) == 0) {
                rescuerMsg = String.format("🎉 Ngân hàng đã xác nhận thanh toán nộp %s ₫ thành công! Bạn đã sạch công nợ với hệ thống.", formattedAmount);
                if (excessAmount.compareTo(BigDecimal.ZERO) > 0) {
                    rescuerMsg += String.format(" Phần nộp dư %s ₫ đã được cộng vào ví tiền.", java.text.NumberFormat.getNumberInstance(java.util.Locale.GERMANY).format(excessAmount));
                }
            } else {
                rescuerMsg = String.format("✔ Ngân hàng đã xác nhận thanh toán %s ₫ nộp nợ. Công nợ còn lại của bạn là %s ₫.", formattedAmount, formattedDebt);
            }

            String rescuerSocketJson = String.format("{\"type\":\"PAYMENT_REMINDER\",\"message\":\"%s\"}", rescuerMsg);
            webSocketHandler.sendMessageToUser(phone, rescuerSocketJson);

            // 2. Gửi thông báo WebSocket cho Admin để cập nhật UI thời gian thực mà không cần F5
            Map<String, Object> adminData = new HashMap<>();
            adminData.put("type", "DEBT_CLEAR");
            adminData.put("rescuerId", user.getUserId().toString());
            adminData.put("rescuerName", user.getFullName());
            adminData.put("amountPaid", amount);
            adminData.put("remainingDebt", profile.getCashDebt());
            adminData.put("excessWallet", excessAmount);

            String adminJsonMsg = String.format(
                "{\"type\":\"DEBT_CLEAR\",\"rescuerId\":\"%s\",\"rescuerName\":\"%s\",\"amountPaid\":%s,\"remainingDebt\":%s,\"excessWallet\":%s}",
                user.getUserId().toString(),
                user.getFullName(),
                amount.toString(),
                profile.getCashDebt().toString(),
                excessAmount.toString()
            );
            webSocketHandler.sendMessageToRole("ADMIN", adminJsonMsg);

            return ResponseEntity.ok(ApiResponse.ok("Giao dịch callback webhook xử lý thành công!", adminData));

        } catch (Exception e) {
            log.error("Error processing bank transfer webhook", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Lỗi xử lý webhook: " + e.getMessage()));
        }
    }
}
