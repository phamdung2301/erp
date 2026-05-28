package web.rescue.erp.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import web.rescue.erp.dto.ApiResponse;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.service.AiDiagnosticService;
import web.rescue.erp.service.RescueRequestService;
import web.rescue.erp.service.UserService;
import web.rescue.erp.repository.RescuerProfileRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
@Slf4j
public class CustomerApiController {

    private final RescueRequestService rescueRequestService;
    private final UserService userService;
    private final AiDiagnosticService aiDiagnosticService;

    /**
     * Lấy các thợ cứu hộ xung quanh tọa độ GPS
     */
    @GetMapping("/nearby-rescuers")
    public ResponseEntity<ApiResponse> getNearbyRescuers(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng) {
        try {
            List<RescuerProfile> profiles = rescueRequestService.findNearbyRescuers(lat, lng);
            // Map sang DTO đơn giản tránh lazy loading exception hoặc lộ lọt thông tin nhạy cảm
            List<Map<String, Object>> data = profiles.stream().map(p -> {
                Map<String, Object> map = new HashMap<>();
                map.put("rescuerId", p.getRescuerId());
                map.put("fullName", p.getUser().getFullName());
                map.put("phone", p.getUser().getPhone());
                map.put("specialty", p.getSpecialty());
                map.put("lat", p.getCurrentLat());
                map.put("lng", p.getCurrentLng());
                map.put("verified", p.isVerified());
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.ok("Tải thợ cứu hộ thành công!", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Chẩn đoán AI xem trước sự cố
     */
    @PostMapping("/diagnose")
    public ResponseEntity<ApiResponse> diagnose(@RequestParam String issueDesc) {
        try {
            AiDiagnosticService.DiagnosticResult diag = aiDiagnosticService.diagnose(issueDesc);
            return ResponseEntity.ok(ApiResponse.ok("Chẩn đoán AI hoàn tất!", diag));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Tạo yêu cầu cứu hộ
     */
    @PostMapping("/request")
    public ResponseEntity<ApiResponse> createRequest(
            @RequestParam String issueDesc,
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam String address,
            Authentication authentication) {
        try {
            User customer = userService.findByPhone(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng!"));

            // Kiểm tra xem khách hàng có yêu cầu nào đang chờ xử lý không
            if (rescueRequestService.getActiveRequestForCustomer(customer.getUserId()).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Bạn đang có một yêu cầu cứu hộ chưa hoàn thành!"));
            }

            RescueRequest request = rescueRequestService.createRequest(customer, issueDesc, lat, lng, address);
            
            Map<String, Object> data = new HashMap<>();
            data.put("requestId", request.getRequestId());
            data.put("status", request.getStatus().name());
            
            return ResponseEntity.ok(ApiResponse.ok("Đã gửi yêu cầu cứu hộ thành công!", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Lấy yêu cầu cứu hộ đang hoạt động của khách hàng hiện tại
     */
    @GetMapping("/request/active")
    public ResponseEntity<ApiResponse> getActiveRequest(Authentication authentication) {
        try {
            User customer = userService.findByPhone(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng!"));

            Optional<RescueRequest> requestOpt = rescueRequestService.getActiveRequestForCustomer(customer.getUserId());
            if (requestOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.ok("Không có cuốc cứu hộ nào đang chạy.", null));
            }

            RescueRequest request = requestOpt.get();
            Map<String, Object> data = new HashMap<>();
            data.put("requestId", request.getRequestId());
            data.put("issueDesc", request.getIssueDesc());
            data.put("aiDiagnosis", request.getAiDiagnosis());
            data.put("status", request.getStatus().name());
            data.put("customerLat", request.getCustomerLat());
            data.put("customerLng", request.getCustomerLng());
            data.put("customerAddress", request.getCustomerAddress());
            data.put("customerPhone", request.getCustomer() != null ? request.getCustomer().getPhone() : null);
            data.put("etaMinutes", request.getEtaMinutes());
            data.put("roadDistance", request.getRoadDistance());
            data.put("basePrice", request.getBasePrice());
            data.put("extraPrice", request.getExtraPrice());
            data.put("otpCode", request.getOtpCode());
            data.put("paymentMethod", request.getPaymentMethod() != null ? request.getPaymentMethod().name() : null);
            data.put("createdAt", request.getCreatedAt());

            if (request.getRescuer() != null) {
                User rescuer = request.getRescuer();
                Map<String, Object> resMap = new HashMap<>();
                resMap.put("fullName", rescuer.getFullName());
                resMap.put("phone", rescuer.getPhone());
                resMap.put("avatar", rescuer.getAvatar());
                
                // Trích xuất tọa độ di chuyển của thợ
                RescuerProfile profile = rescuerProfileRepository_find(rescuer.getUserId());
                if (profile != null) {
                    resMap.put("lat", profile.getCurrentLat());
                    resMap.put("lng", profile.getCurrentLng());
                    resMap.put("specialty", profile.getSpecialty());
                }
                data.put("rescuer", resMap);
            }

            if (request.getInvoice() != null) {
                data.put("totalAmount", request.getInvoice().getTotalAmount());
                data.put("systemFee", request.getInvoice().getSystemFee());
                data.put("paid", request.getInvoice().isPaid());
            }

            return ResponseEntity.ok(ApiResponse.ok("Tải dữ liệu yêu cầu thành công!", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private final RescuerProfileRepository rescuerProfileRepository;

    private RescuerProfile rescuerProfileRepository_find(UUID id) {
        return rescuerProfileRepository.findById(id).orElse(null);
    }

    /**
     * Hủy yêu cầu cứu hộ
     */
    @PostMapping("/request/{id}/cancel")
    public ResponseEntity<ApiResponse> cancelRequest(@PathVariable UUID id) {
        try {
            rescueRequestService.cancelRequest(id);
            return ResponseEntity.ok(ApiResponse.ok("Đã hủy yêu cầu cứu hộ thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Giả lập phản hồi cứu hộ
     */
    @PostMapping("/request/{id}/simulate-step")
    public ResponseEntity<ApiResponse> simulateStep(
            @PathVariable UUID id,
            @RequestParam String action) {
        try {
            rescueRequestService.simulateRescuerAction(id, action);
            return ResponseEntity.ok(ApiResponse.ok("Gi giả lập bước phản hồi thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Cập nhật phương thức thanh toán cho cuốc cứu hộ đang hoạt động
     */
    @PostMapping("/request/{id}/payment-method")
    public ResponseEntity<ApiResponse> updatePaymentMethod(
            @PathVariable UUID id,
            @RequestParam String paymentMethod) {
        try {
            rescueRequestService.updatePaymentMethod(id, paymentMethod);
            return ResponseEntity.ok(ApiResponse.ok("Cập nhật phương thức thanh toán thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Đánh giá và thanh toán yêu cầu cứu hộ
     */
    @PostMapping("/request/{id}/pay-rate")
    public ResponseEntity<ApiResponse> payAndRate(
            @PathVariable UUID id,
            @RequestParam String paymentMethod,
            @RequestParam int rating,
            @RequestParam(required = false) String feedback) {
        try {
            rescueRequestService.payAndRateRequest(id, paymentMethod, rating, feedback);
            return ResponseEntity.ok(ApiResponse.ok("Thanh toán và đánh giá thợ cứu hộ thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
