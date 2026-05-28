package web.rescue.erp.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import web.rescue.erp.dto.ApiResponse;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.RescuerInventory;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.repository.RescuerProfileRepository;
import web.rescue.erp.service.RescueRequestService;
import web.rescue.erp.service.RescuerService;
import web.rescue.erp.service.UserService;
import web.rescue.erp.websocket.WebSocketNotificationService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rescuer")
@RequiredArgsConstructor
@Slf4j
public class RescuerApiController {

    private final RescuerService rescuerService;
    private final RescueRequestService rescueRequestService;
    private final UserService userService;
    private final RescuerProfileRepository rescuerProfileRepository;
    private final WebSocketNotificationService webSocketNotificationService;

    private User getAuthenticatedUser(Authentication auth) {
        return userService.findByPhone(auth.getName())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin thợ cứu hộ đăng nhập!"));
    }

    private RescuerProfile getRescuerProfile(User user) {
        return rescuerProfileRepository.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ cứu hộ!"));
    }

    /**
     * Bật/tắt online
     */
    @PostMapping("/toggle-online")
    public ResponseEntity<ApiResponse> toggleOnline(@RequestParam boolean online, Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            rescuerService.toggleOnline(user.getUserId(), online);
            String msg = online ? "Đã bật trạng thái làm việc online!" : "Đã tắt trạng thái làm việc offline!";
            return ResponseEntity.ok(ApiResponse.ok(msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Cập nhật vị trí hiện tại
     */
    @PostMapping("/location")
    public ResponseEntity<ApiResponse> updateLocation(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            rescuerService.updateLocation(user.getUserId(), lat, lng);

            // Notify customer in real-time if there is an active request
            rescueRequestService.getActiveRequestForRescuer(user.getUserId()).ifPresent(req -> {
                webSocketNotificationService.notifyLocationUpdate(
                        req.getCustomer().getPhone(),
                        lat,
                        lng,
                        user.getFullName()
                );
            });

            return ResponseEntity.ok(ApiResponse.ok("Cập nhật vị trí thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Lấy các yêu cầu cứu hộ PENDING xung quanh
     */
    @GetMapping("/incoming-requests")
    public ResponseEntity<ApiResponse> getIncomingRequests(Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            RescuerProfile profile = getRescuerProfile(user);

            if (!profile.isOnline()) {
                return ResponseEntity.ok(ApiResponse.ok("Thợ đang offline. Hãy bật online để nhận cuốc xe.", List.of()));
            }

            if (profile.getCurrentLat() == null || profile.getCurrentLng() == null) {
                return ResponseEntity.ok(ApiResponse.ok("Không thể xác định vị trí của bạn để tìm cuốc xe.", List.of()));
            }

            List<RescueRequest> list = rescueRequestService.getNearbyPendingRequests(profile.getCurrentLat(), profile.getCurrentLng());
            
            List<Map<String, Object>> data = list.stream().map(r -> {
                Map<String, Object> map = new HashMap<>();
                map.put("requestId", r.getRequestId());
                map.put("issueDesc", r.getIssueDesc());
                map.put("aiDiagnosis", r.getAiDiagnosis());
                map.put("customerAddress", r.getCustomerAddress());
                map.put("customerName", r.getCustomer().getFullName());
                map.put("basePrice", r.getBasePrice());
                map.put("lat", r.getCustomerLat());
                map.put("lng", r.getCustomerLng());
                map.put("createdAt", r.getCreatedAt());
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.ok("Tải danh sách cuốc xe thành công!", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Lấy cuốc xe đang chạy của thợ
     */
    @GetMapping("/request/active")
    public ResponseEntity<ApiResponse> getActiveRequest(Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            var reqOpt = rescueRequestService.getActiveRequestForRescuer(user.getUserId());
            if (reqOpt.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.ok("Không có cuốc xe hoạt động.", null));
            }

            RescueRequest request = reqOpt.get();
            Map<String, Object> data = new HashMap<>();
            data.put("requestId", request.getRequestId());
            data.put("issueDesc", request.getIssueDesc());
            data.put("aiDiagnosis", request.getAiDiagnosis());
            data.put("status", request.getStatus().name());
            data.put("customerLat", request.getCustomerLat());
            data.put("customerLng", request.getCustomerLng());
            data.put("customerAddress", request.getCustomerAddress());
            data.put("customerName", request.getCustomer() != null ? request.getCustomer().getFullName() : null);
            data.put("customerPhone", request.getCustomer() != null ? request.getCustomer().getPhone() : null);
            data.put("rescuerPhone", user.getPhone());
            data.put("etaMinutes", request.getEtaMinutes());
            data.put("roadDistance", request.getRoadDistance());
            data.put("basePrice", request.getBasePrice());
            data.put("extraPrice", request.getExtraPrice());
            
            if (request.getInvoice() != null) {
                data.put("totalAmount", request.getInvoice().getTotalAmount());
                data.put("paid", request.getInvoice().isPaid());
            }

            return ResponseEntity.ok(ApiResponse.ok("Tải thông tin cuốc xe hiện tại thành công!", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Nhận cuốc xe
     */
    @PostMapping("/request/{id}/accept")
    public ResponseEntity<ApiResponse> acceptRequest(@PathVariable UUID id, Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            RescuerProfile profile = getRescuerProfile(user);
            if (!profile.isOnline()) {
                throw new RuntimeException("Bạn phải bật online để nhận cuốc xe!");
            }
            
            // Kiểm tra xem thợ có cuốc nào đang chạy chưa
            if (rescueRequestService.getActiveRequestForRescuer(user.getUserId()).isPresent()) {
                throw new RuntimeException("Bạn đang có một cuốc xe cứu hộ chưa hoàn thành!");
            }

            rescueRequestService.acceptRequestByRescuer(id, user);
            return ResponseEntity.ok(ApiResponse.ok("Đã nhận cuốc xe thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Đến nơi
     */
    @PostMapping("/request/{id}/arrive")
    public ResponseEntity<ApiResponse> arrive(@PathVariable UUID id) {
        try {
            rescueRequestService.updateRequestStatus(id, RequestStatus.ARRIVED);
            return ResponseEntity.ok(ApiResponse.ok("Xác nhận đã đến vị trí khách hàng thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Bắt đầu sửa
     */
    @PostMapping("/request/{id}/start-repair")
    public ResponseEntity<ApiResponse> startRepair(@PathVariable UUID id) {
        try {
            rescueRequestService.updateRequestStatus(id, RequestStatus.IN_PROGRESS);
            return ResponseEntity.ok(ApiResponse.ok("Đã bắt đầu tiến trình sửa chữa xe!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Cập nhật chi phí phát sinh phụ tùng
     */
    @PostMapping("/request/{id}/update-extra")
    public ResponseEntity<ApiResponse> updateExtra(
            @PathVariable UUID id,
            @RequestParam BigDecimal extraPrice) {
        try {
            rescueRequestService.addExtraPrice(id, extraPrice);
            return ResponseEntity.ok(ApiResponse.ok("Cập nhật phí phụ tùng thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Lấy danh sách phụ tùng trong cốp đồ của thợ
     */
    @GetMapping("/inventory")
    public ResponseEntity<ApiResponse> getInventory(Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            List<RescuerInventory> inventoryList = rescueRequestService.getRescuerInventory(user.getUserId());
            List<Map<String, Object>> data = inventoryList.stream().map(item -> {
                Map<String, Object> map = new HashMap<>();
                map.put("inventoryId", item.getId());
                map.put("partName", item.getPartName());
                map.put("quantity", item.getQuantity());
                map.put("unitPrice", item.getUnitPrice());
                return map;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.ok("Tải danh sách phụ tùng thành công!", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Sử dụng phụ tùng từ cốp đồ
     */
    @PostMapping("/request/{id}/use-part")
    public ResponseEntity<ApiResponse> usePart(
            @PathVariable UUID id,
            @RequestParam String partName,
            Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            rescueRequestService.usePart(id, partName, user);
            return ResponseEntity.ok(ApiResponse.ok("Sử dụng phụ tùng '" + partName + "' thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Hoàn thành cuốc xe bằng OTP
     */
    @PostMapping("/request/{id}/complete")
    public ResponseEntity<ApiResponse> complete(@PathVariable UUID id, @RequestParam String otp) {
        try {
            rescueRequestService.completeRequestWithOtp(id, otp);
            return ResponseEntity.ok(ApiResponse.ok("Xác nhận mã OTP chính xác. Cuốc xe đã hoàn tất!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Rút tiền từ ví thợ
     */
    @PostMapping("/wallet/payout")
    public ResponseEntity<ApiResponse> payout(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String bankName,
            @RequestParam(required = false) String bankAccountNo,
            @RequestParam(required = false) String bankAccountName,
            Authentication authentication) {
        try {
            User user = getAuthenticatedUser(authentication);
            rescuerService.payoutRequest(user.getUserId(), amount, bankName, bankAccountNo, bankAccountName);
            return ResponseEntity.ok(ApiResponse.ok("Gửi yêu cầu rút tiền thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
