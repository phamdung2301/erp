package web.rescue.erp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.rescue.erp.entity.Invoice;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.PaymentMethod;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.entity.RescuerInventory;
import web.rescue.erp.repository.InvoiceRepository;
import web.rescue.erp.repository.RescueRequestRepository;
import web.rescue.erp.repository.RescuerProfileRepository;
import web.rescue.erp.repository.RescuerInventoryRepository;
import web.rescue.erp.repository.UserRepository;
import web.rescue.erp.websocket.WebSocketNotificationService;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RescueRequestService {

    private final RescueRequestRepository rescueRequestRepository;
    private final RescuerProfileRepository rescuerProfileRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final RescuerInventoryRepository rescuerInventoryRepository;
    private final AiDiagnosticService aiDiagnosticService;
    private final SystemSettingService systemSettingService;
    private final WebSocketNotificationService webSocketNotificationService;
    
    private final SecureRandom random = new SecureRandom();
    private final java.util.Map<UUID, Integer> otpFailedAttempts = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Tạo yêu cầu cứu hộ mới dựa trên chẩn đoán AI
     */
    @Transactional
    public RescueRequest createRequest(User customer, String issueDesc, BigDecimal lat, BigDecimal lng, String address) {
        // Chẩn đoán lỗi qua AI
        AiDiagnosticService.DiagnosticResult diag = aiDiagnosticService.diagnose(issueDesc);

        // Sinh mã OTP 6 chữ số để xác nhận hoàn thành
        String otp = String.format("%06d", random.nextInt(1_000_000));

        // Phụ phí đêm khuya (22:00 - 05:00)
        BigDecimal surcharge = BigDecimal.ZERO;
        BigDecimal surgeMultiplier = BigDecimal.ONE;
        java.time.LocalTime nowTime = java.time.LocalTime.now();
        if (nowTime.isAfter(java.time.LocalTime.of(22, 0)) || nowTime.isBefore(java.time.LocalTime.of(5, 0))) {
            surcharge = new BigDecimal("50000.00");
            surgeMultiplier = new BigDecimal("1.20");
        }

        BigDecimal estimatedBase = diag.estimatedBasePrice() != null ? diag.estimatedBasePrice() : BigDecimal.ZERO;
        BigDecimal finalBasePrice = estimatedBase.multiply(surgeMultiplier).add(surcharge).setScale(2, java.math.RoundingMode.HALF_UP);

        RescueRequest request = RescueRequest.builder()
                .customer(customer)
                .issueDesc(issueDesc)
                .aiDiagnosis(diag.issueName() + "\n" + diag.recommendation())
                .status(RequestStatus.PENDING)
                .customerLat(lat)
                .customerLng(lng)
                .customerAddress(address)
                .basePrice(finalBasePrice)
                .surcharge(surcharge)
                .surgeMultiplier(surgeMultiplier)
                .extraPrice(BigDecimal.ZERO)
                .otpCode(otp)
                .build();

        RescueRequest saved = rescueRequestRepository.save(request);
        webSocketNotificationService.notifyRequestUpdate(saved);
        return saved;
    }

    /**
     * Lấy yêu cầu cứu hộ đang hoạt động của khách hàng
     */
    public Optional<RescueRequest> getActiveRequestForCustomer(UUID customerId) {
        List<RescueRequest> list = rescueRequestRepository.findByCustomerUserIdOrderByCreatedAtDesc(customerId);
        return list.stream()
                .filter(r -> r.getStatus() != RequestStatus.COMPLETED && r.getStatus() != RequestStatus.CANCELED)
                .findFirst();
    }

    /**
     * Hủy yêu cầu cứu hộ
     */
    @Transactional
    public void cancelRequest(UUID requestId) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));
        
        if (request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CANCELED) {
            throw new RuntimeException("Yêu cầu đã kết thúc hoặc đã hủy trước đó!");
        }

        if (request.getStatus() == RequestStatus.ACCEPTED || request.getStatus() == RequestStatus.ARRIVED || request.getStatus() == RequestStatus.IN_PROGRESS) {
            // Thợ đã nhận cuốc và đang thực hiện -> Phạt hủy cuốc 20k đền bù thợ
            BigDecimal penalty = new BigDecimal("20000.00");
            request.setCancellationFee(penalty);
            
            if (request.getRescuer() != null) {
                RescuerProfile profile = rescuerProfileRepository.findById(request.getRescuer().getUserId()).orElse(null);
                if (profile != null) {
                    profile.setWalletBalance(profile.getWalletBalance().add(penalty));
                    rescuerProfileRepository.save(profile);
                    log.info("Credited 20k cancellation fee to rescuer {}", request.getRescuer().getFullName());
                }
            }
        }

        request.setStatus(RequestStatus.CANCELED);
        request.setOtpCode(null); // Bảo mật: Xóa mã OTP khi hủy cuốc xe
        RescueRequest saved = rescueRequestRepository.save(request);
        webSocketNotificationService.notifyRequestUpdate(saved);
        log.info("Request {} has been canceled by customer", requestId);
    }

    /**
     * Tìm thợ cứu hộ xung quanh
     */
    public List<RescuerProfile> findNearbyRescuers(BigDecimal lat, BigDecimal lng) {
        // Mặc định tìm trong bán kính khoảng 5km (~0.05 độ vĩ độ/kinh độ)
        BigDecimal range = new BigDecimal("0.05");
        return rescuerProfileRepository.findNearbyOnlineRescuers(lat, lng, range);
    }

    /**
     * Giả lập phản hồi của thợ cứu hộ (Simulation Tool)
     */
    @Transactional
    public void simulateRescuerAction(UUID requestId, String action) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));

        switch (action.toUpperCase()) {
            case "ACCEPT":
                if (request.getStatus() != RequestStatus.PENDING) {
                    throw new RuntimeException("Chỉ giả lập nhận cuốc khi trạng thái là PENDING!");
                }
                // Gán thợ cứu hộ demo đầu tiên đang online
                User rescuer = userRepository.findByPhone("0902222222")
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy thợ cứu hộ demo!"));
                request.setRescuer(rescuer);
                request.setStatus(RequestStatus.ACCEPTED);

                // Tự động tính toán Road Distance và ETA uốn lượn di chuyển đường bộ thực tế
                RescuerProfile prof = rescuerProfileRepository.findById(rescuer.getUserId()).orElse(null);
                if (prof != null && prof.getCurrentLat() != null && prof.getCurrentLng() != null 
                        && request.getCustomerLat() != null && request.getCustomerLng() != null) {
                    
                    double lat1 = prof.getCurrentLat().doubleValue();
                    double lon1 = prof.getCurrentLng().doubleValue();
                    double lat2 = request.getCustomerLat().doubleValue();
                    double lon2 = request.getCustomerLng().doubleValue();
                    
                    double R = 6371; // km
                    double dLat = Math.toRadians(lat2 - lat1);
                    double dLon = Math.toRadians(lon2 - lon1);
                    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                               Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                               Math.sin(dLon / 2) * Math.sin(dLon / 2);
                    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                    double distance = R * c; // chim bay (km)
                    
                    BigDecimal roadDistance = new BigDecimal(distance * 1.35).setScale(2, java.math.RoundingMode.HALF_UP);
                    int etaMinutes = (int) Math.round((roadDistance.doubleValue() / 30.0) * 60.0);
                    if (etaMinutes < 2) etaMinutes = 2; // Tối thiểu 2 phút
                    
                    request.setRoadDistance(roadDistance);
                    request.setEtaMinutes(etaMinutes);
                }

                log.info("Simulation: Rescuer accepted request {}", requestId);
                break;

            case "ARRIVE":
                if (request.getStatus() != RequestStatus.ACCEPTED) {
                    throw new RuntimeException("Chỉ giả lập đến nơi khi trạng thái là ACCEPTED!");
                }
                request.setStatus(RequestStatus.ARRIVED);
                log.info("Simulation: Rescuer arrived at customer location for request {}", requestId);
                break;

            case "START":
                if (request.getStatus() != RequestStatus.ARRIVED) {
                    throw new RuntimeException("Chỉ giả lập sửa chữa khi trạng thái là ARRIVED!");
                }
                request.setStatus(RequestStatus.IN_PROGRESS);
                log.info("Simulation: Rescuer started repair for request {}", requestId);
                break;

            case "COMPLETE":
                if (request.getStatus() != RequestStatus.IN_PROGRESS) {
                    throw new RuntimeException("Chỉ giả lập hoàn thành khi trạng thái là IN_PROGRESS!");
                }
                // Tạo hóa đơn tạm thời chờ thanh toán
                request.setStatus(RequestStatus.COMPLETED);
                
                BigDecimal totalAmount = request.getBasePrice().add(request.getExtraPrice());
                BigDecimal feePercent = systemSettingService.getSystemFeePercent();
                BigDecimal systemFee = totalAmount.multiply(feePercent).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                
                PaymentMethod chosenMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CASH;
                Invoice invoice = Invoice.builder()
                        .rescueRequest(request)
                        .totalAmount(totalAmount)
                        .systemFee(systemFee)
                        .paymentMethod(chosenMethod)
                        .paid(true)
                        .build();
                
                request.setInvoice(invoice);
                invoiceRepository.save(invoice);

                // Cộng tiền cho thợ
                if (request.getRescuer() != null) {
                    RescuerProfile profile = rescuerProfileRepository.findById(request.getRescuer().getUserId())
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
                    BigDecimal netAmount = totalAmount.subtract(systemFee);
                    profile.setWalletBalance(profile.getWalletBalance().add(netAmount));
                    if (chosenMethod == PaymentMethod.CASH) {
                        BigDecimal currentDebt = profile.getCashDebt() != null ? profile.getCashDebt() : BigDecimal.ZERO;
                        profile.setCashDebt(currentDebt.add(totalAmount));
                    }
                    rescuerProfileRepository.save(profile);
                }
                log.info("Simulation: Rescuer completed work for request {}. Invoice generated and paid.", requestId);
                break;

            default:
                throw new IllegalArgumentException("Hành động giả lập không hợp lệ: " + action);
        }
        RescueRequest saved = rescueRequestRepository.save(request);
        webSocketNotificationService.notifyRequestUpdate(saved);
    }

    /**
     * Thanh toán hóa đơn và đánh giá thợ cứu hộ
     */
    @Transactional
    public void payAndRateRequest(UUID requestId, String paymentMethodStr, int rating, String feedback) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));

        if (request.getStatus() != RequestStatus.COMPLETED) {
            throw new RuntimeException("Cuốc cứu hộ chưa được hoàn thành bởi thợ!");
        }

        Invoice invoice = request.getInvoice();
        if (invoice == null) {
            throw new RuntimeException("Không tìm thấy hóa đơn cho yêu cầu này!");
        }

        if (!invoice.isPaid()) {
            PaymentMethod method = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
            invoice.setPaymentMethod(method);
            invoice.setPaid(true);
            invoiceRepository.save(invoice);

            // Cộng tiền cho thợ (trừ phí hệ thống) nếu chưa cộng lúc OTP
            if (request.getRescuer() != null) {
                RescuerProfile profile = rescuerProfileRepository.findById(request.getRescuer().getUserId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
                BigDecimal netAmount = invoice.getTotalAmount().subtract(invoice.getSystemFee());
                profile.setWalletBalance(profile.getWalletBalance().add(netAmount));
                if (method == PaymentMethod.CASH) {
                    BigDecimal currentDebt = profile.getCashDebt() != null ? profile.getCashDebt() : BigDecimal.ZERO;
                    profile.setCashDebt(currentDebt.add(invoice.getTotalAmount()));
                }
                rescuerProfileRepository.save(profile);
            }
        }

        log.info("Paid invoice for request {} via {}. Rating: {} stars, Feedback: {}", 
                requestId, paymentMethodStr, rating, feedback);
        webSocketNotificationService.notifyRequestUpdate(request);
    }

    /**
     * Tìm các yêu cầu cứu hộ PENDING trong khoảng lân cận
     */
    public List<RescueRequest> getNearbyPendingRequests(BigDecimal lat, BigDecimal lng) {
        BigDecimal range = new BigDecimal("0.05"); // Khoảng 5km
        List<RescueRequest> pendings = rescueRequestRepository.findByStatus(RequestStatus.PENDING);
        return pendings.stream()
                .filter(r -> r.getCustomerLat() != null && r.getCustomerLng() != null)
                .filter(r -> r.getCustomerLat().subtract(lat).abs().compareTo(range) < 0)
                .filter(r -> r.getCustomerLng().subtract(lng).abs().compareTo(range) < 0)
                .toList();
    }

    /**
     * Lấy cuốc cứu hộ đang hoạt động của thợ cứu hộ
     */
    public Optional<RescueRequest> getActiveRequestForRescuer(UUID rescuerId) {
        List<RescueRequest> list = rescueRequestRepository.findByRescuerUserIdOrderByCreatedAtDesc(rescuerId);
        return list.stream()
                .filter(r -> r.getStatus() != RequestStatus.COMPLETED && r.getStatus() != RequestStatus.CANCELED)
                .findFirst();
    }

    /**
     * Thợ cứu hộ nhận cuốc xe
     */
    @Transactional
    public void acceptRequestByRescuer(UUID requestId, User rescuer) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new RuntimeException("Cuốc xe này đã được nhận hoặc đã bị hủy trước đó!");
        }
        request.setRescuer(rescuer);
        request.setStatus(RequestStatus.ACCEPTED);

        // Tự động tính toán Road Distance và ETA uốn lượn di chuyển đường bộ thực tế
        RescuerProfile profile = rescuerProfileRepository.findById(rescuer.getUserId()).orElse(null);
        if (profile != null && profile.getCurrentLat() != null && profile.getCurrentLng() != null 
                && request.getCustomerLat() != null && request.getCustomerLng() != null) {
            
            double lat1 = profile.getCurrentLat().doubleValue();
            double lon1 = profile.getCurrentLng().doubleValue();
            double lat2 = request.getCustomerLat().doubleValue();
            double lon2 = request.getCustomerLng().doubleValue();
            
            double R = 6371; // km
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                       Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                       Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            double distance = R * c; // chim bay (km)
            
            BigDecimal roadDistance = new BigDecimal(distance * 1.35).setScale(2, java.math.RoundingMode.HALF_UP);
            int etaMinutes = (int) Math.round((roadDistance.doubleValue() / 30.0) * 60.0);
            if (etaMinutes < 2) etaMinutes = 2; // Tối thiểu 2 phút
            
            request.setRoadDistance(roadDistance);
            request.setEtaMinutes(etaMinutes);
        }

        RescueRequest saved = rescueRequestRepository.save(request);
        webSocketNotificationService.notifyRequestUpdate(saved);
        log.info("Rescuer {} accepted request {}", rescuer.getUserId(), requestId);
    }

    /**
     * Cập nhật trạng thái cuốc cứu hộ
     */
    @Transactional
    public void updateRequestStatus(UUID requestId, RequestStatus status) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));
        if (request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CANCELED) {
            throw new RuntimeException("Yêu cầu đã hoàn thành hoặc đã bị hủy, không thể đổi trạng thái!");
        }
        request.setStatus(status);
        RescueRequest saved = rescueRequestRepository.save(request);
        webSocketNotificationService.notifyRequestUpdate(saved);
        log.info("Request {} updated status to {}", requestId, status);
    }

    /**
     * Cập nhật phương thức thanh toán
     */
    @Transactional
    public void updatePaymentMethod(UUID requestId, String paymentMethodStr) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));
        if (request.getStatus() == RequestStatus.COMPLETED || request.getStatus() == RequestStatus.CANCELED) {
            throw new RuntimeException("Yêu cầu đã hoàn thành hoặc đã bị hủy, không thể thay đổi phương thức thanh toán!");
        }
        PaymentMethod method = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());
        request.setPaymentMethod(method);
        RescueRequest saved = rescueRequestRepository.save(request);
        webSocketNotificationService.notifyRequestUpdate(saved);
        log.info("Request {} updated payment method to {}", requestId, method);
    }

    /**
     * Cập nhật chi phí phụ tùng phát sinh
     */
    @Transactional
    public void addExtraPrice(UUID requestId, BigDecimal extraPrice) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));
        if (request.getStatus() != RequestStatus.IN_PROGRESS) {
            throw new RuntimeException("Chỉ có thể cập nhật chi phí phụ tùng khi cuộc xe đang trong tiến trình sửa chữa!");
        }
        if (extraPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Chi phí phụ tùng không thể âm!");
        }
        request.setExtraPrice(extraPrice);
        RescueRequest saved = rescueRequestRepository.save(request);
        webSocketNotificationService.notifyRequestUpdate(saved);
        log.info("Added extra price {} to request {}", extraPrice, requestId);
    }

    /**
     * Hoàn thành cuốc xe bằng việc đối chiếu mã OTP từ khách hàng
     */
    @Transactional
    public void completeRequestWithOtp(UUID requestId, String otpCode) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));
        if (request.getStatus() != RequestStatus.IN_PROGRESS) {
            throw new RuntimeException("Chỉ có thể hoàn thành cuốc xe khi đang sửa chữa!");
        }

        // Kiểm tra số lần nhập sai OTP
        int failedAttempts = otpFailedAttempts.getOrDefault(requestId, 0);
        if (failedAttempts >= 3) {
            throw new RuntimeException("Mã OTP đã bị khóa do nhập sai quá 3 lần vì lý do bảo mật. Vui lòng liên hệ Admin để được hỗ trợ!");
        }

        if (!request.getOtpCode().equals(otpCode)) {
            failedAttempts++;
            otpFailedAttempts.put(requestId, failedAttempts);
            int remaining = 3 - failedAttempts;
            if (remaining <= 0) {
                throw new RuntimeException("Mã OTP đã bị khóa do nhập sai liên tiếp quá 3 lần!");
            } else {
                throw new RuntimeException("Mã OTP hoàn thành không chính xác! Bạn còn " + remaining + " lần thử.");
            }
        }

        // Xóa bộ nhớ thử sai khi nhập đúng
        otpFailedAttempts.remove(requestId);

        request.setStatus(RequestStatus.COMPLETED);
        request.setOtpCode(null); // Bảo mật: Xóa mã OTP sau khi sử dụng thành công

        BigDecimal totalAmount = request.getBasePrice().add(request.getExtraPrice());
        BigDecimal feePercent = systemSettingService.getSystemFeePercent();
        BigDecimal systemFee = totalAmount.multiply(feePercent).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);

        PaymentMethod chosenMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.CASH;
        Invoice invoice = Invoice.builder()
                .rescueRequest(request)
                .totalAmount(totalAmount)
                .systemFee(systemFee)
                .paymentMethod(chosenMethod)
                .paid(true)
                .build();

        request.setInvoice(invoice);
        invoiceRepository.save(invoice);

        // Cộng tiền cho thợ (trừ phí hệ thống) ngay lúc hoàn thành OTP
        if (request.getRescuer() != null) {
            RescuerProfile profile = rescuerProfileRepository.findById(request.getRescuer().getUserId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ thợ!"));
            BigDecimal netAmount = totalAmount.subtract(systemFee);
            profile.setWalletBalance(profile.getWalletBalance().add(netAmount));
            if (chosenMethod == PaymentMethod.CASH) {
                BigDecimal currentDebt = profile.getCashDebt() != null ? profile.getCashDebt() : BigDecimal.ZERO;
                profile.setCashDebt(currentDebt.add(totalAmount));
            }
            rescuerProfileRepository.save(profile);
        }

        RescueRequest saved = rescueRequestRepository.save(request);
        webSocketNotificationService.notifyRequestUpdate(saved);
        log.info("Request {} completed via OTP. Invoice created and paid. Rescuer wallet updated.", requestId);
    }

    /**
     * Lấy danh sách phụ tùng trong cốp đồ của thợ
     */
    public List<RescuerInventory> getRescuerInventory(UUID rescuerId) {
        return rescuerInventoryRepository.findByRescuerUserId(rescuerId);
    }

    /**
     * Sử dụng phụ tùng từ cốp đồ thợ di động
     */
    @Transactional
    public void usePart(UUID requestId, String partName, User rescuer) {
        RescueRequest request = rescueRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu cứu hộ!"));

        if (request.getStatus() != RequestStatus.IN_PROGRESS) {
            throw new RuntimeException("Chỉ có thể sử dụng phụ tùng khi cuốc xe đang trong tiến trình sửa chữa!");
        }

        if (request.getRescuer() == null || !request.getRescuer().getUserId().equals(rescuer.getUserId())) {
            throw new RuntimeException("Chỉ thợ được phân công mới có quyền sử dụng phụ tùng cho cuốc xe này!");
        }

        // Tìm phụ tùng trong kho
        RescuerInventory inventory = rescuerInventoryRepository.findByRescuerUserIdAndPartName(rescuer.getUserId(), partName)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phụ tùng '" + partName + "' trong cốp đồ của bạn!"));

        if (inventory.getQuantity() < 1) {
            throw new RuntimeException("Phụ tùng '" + partName + "' đã hết trong cốp đồ của bạn!");
        }

        // Trừ 1 trong tồn kho
        inventory.setQuantity(inventory.getQuantity() - 1);
        rescuerInventoryRepository.save(inventory);

        // Cộng giá vào extraPrice
        BigDecimal currentExtra = request.getExtraPrice() != null ? request.getExtraPrice() : BigDecimal.ZERO;
        request.setExtraPrice(currentExtra.add(inventory.getUnitPrice()));
        RescueRequest saved = rescueRequestRepository.save(request);

        // Gửi WebSocket thông báo cập nhật chi phí
        webSocketNotificationService.notifyRequestUpdate(saved);
        log.info("Rescuer {} used part '{}' (price: {}). Remaining qty: {}. Request extra price updated to {}", 
                rescuer.getUserId(), partName, inventory.getUnitPrice(), inventory.getQuantity(), request.getExtraPrice());
    }
}
