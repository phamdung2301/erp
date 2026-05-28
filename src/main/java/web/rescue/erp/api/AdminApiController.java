package web.rescue.erp.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.rescue.erp.dto.ApiResponse;
import web.rescue.erp.repository.InvoiceRepository;
import web.rescue.erp.service.PayoutService;
import web.rescue.erp.service.RescuerService;
import web.rescue.erp.service.SystemSettingService;
import web.rescue.erp.service.UserService;
import web.rescue.erp.entity.Invoice;
import web.rescue.erp.entity.RescueRequest;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final UserService userService;
    private final RescuerService rescuerService;
    private final PayoutService payoutService;
    private final SystemSettingService systemSettingService;
    private final InvoiceRepository invoiceRepository;

    /**
     * Khóa tài khoản user
     */
    @PutMapping("/users/{id}/lock")
    public ResponseEntity<ApiResponse> lockUser(@PathVariable UUID id) {
        try {
            userService.lockUser(id);
            return ResponseEntity.ok(ApiResponse.ok("Đã khóa tài khoản thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Mở khóa tài khoản user
     */
    @PutMapping("/users/{id}/unlock")
    public ResponseEntity<ApiResponse> unlockUser(@PathVariable UUID id) {
        try {
            userService.unlockUser(id);
            return ResponseEntity.ok(ApiResponse.ok("Đã mở khóa tài khoản thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Duyệt hồ sơ Rescuer
     */
    @PutMapping("/rescuers/{id}/verify")
    public ResponseEntity<ApiResponse> verifyRescuer(@PathVariable UUID id) {
        try {
            rescuerService.verifyRescuer(id);
            return ResponseEntity.ok(ApiResponse.ok("Đã duyệt hồ sơ đối tác thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/rescuers/{id}/reject")
    public ResponseEntity<ApiResponse> rejectRescuer(@PathVariable UUID id) {
        try {
            rescuerService.rejectRescuer(id);
            return ResponseEntity.ok(ApiResponse.ok("Đã từ chối hồ sơ đối tác!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Thu hồi/Xác nhận đã thu nợ tiền mặt từ thợ
     */
    @PutMapping("/rescuers/{id}/clear-cash-debt")
    public ResponseEntity<ApiResponse> clearCashDebt(@PathVariable UUID id) {
        try {
            rescuerService.clearCashDebt(id);
            return ResponseEntity.ok(ApiResponse.ok("Đã thu hồi và xóa công nợ tiền mặt thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Nhắc nhở thợ nộp lại tiền mặt (thông báo công nợ)
     */
    @PostMapping("/rescuers/{id}/remind-payment")
    public ResponseEntity<ApiResponse> remindPayment(@PathVariable UUID id) {
        try {
            rescuerService.sendPaymentReminder(id);
            return ResponseEntity.ok(ApiResponse.ok("Đã gửi thông báo nhắc nhở thanh toán đến thợ!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Phê duyệt rút tiền
     */
    @PostMapping("/payouts/{id}/approve")
    public ResponseEntity<ApiResponse> approvePayout(@PathVariable UUID id) {
        try {
            payoutService.approvePayout(id);
            return ResponseEntity.ok(ApiResponse.ok("Phê duyệt yêu cầu rút tiền thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Từ chối rút tiền
     */
    @PostMapping("/payouts/{id}/reject")
    public ResponseEntity<ApiResponse> rejectPayout(@PathVariable UUID id) {
        try {
            payoutService.rejectPayout(id);
            return ResponseEntity.ok(ApiResponse.ok("Đã từ chối yêu cầu rút tiền!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Cập nhật cài đặt chiết khấu hệ thống
     */
    @PostMapping("/settings/update")
    public ResponseEntity<ApiResponse> updateSettings(
            @RequestParam String systemFeePercent,
            @RequestParam(required = false) String adminBankName,
            @RequestParam(required = false) String adminBankAccountNo,
            @RequestParam(required = false) String adminBankAccountName) {
        try {
            BigDecimal fee = new BigDecimal(systemFeePercent);
            if (fee.compareTo(BigDecimal.ZERO) < 0 || fee.compareTo(new BigDecimal("100")) > 0) {
                throw new RuntimeException("Tỷ lệ phí chiết khấu hệ thống phải từ 0% đến 100%!");
            }
            systemSettingService.saveSetting("SYSTEM_FEE_PERCENT", systemFeePercent, "Tỷ lệ phí chiết khấu hệ thống (%)");
            if (adminBankName != null) {
                systemSettingService.saveSetting("ADMIN_BANK_NAME", adminBankName, "Tên ngân hàng Admin");
            }
            if (adminBankAccountNo != null) {
                systemSettingService.saveSetting("ADMIN_BANK_ACCOUNT_NO", adminBankAccountNo, "Số tài khoản Admin");
            }
            if (adminBankAccountName != null) {
                systemSettingService.saveSetting("ADMIN_BANK_ACCOUNT_NAME", adminBankAccountName, "Tên chủ tài khoản Admin");
            }
            return ResponseEntity.ok(ApiResponse.ok("Cập nhật cài đặt hệ thống thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Cập nhật đơn giá dịch vụ
     */
    @PostMapping("/price-list/update")
    public ResponseEntity<ApiResponse> updatePriceList(
            @RequestParam String pricePuncture,
            @RequestParam String priceBattery,
            @RequestParam String priceChain,
            @RequestParam String priceBrake,
            @RequestParam String priceEngine) {
        try {
            systemSettingService.saveSetting("PRICE_PUNCTURE", pricePuncture, "Đơn giá sửa vá săm lốp");
            systemSettingService.saveSetting("PRICE_BATTERY", priceBattery, "Đơn giá sửa/kích bình ắc quy");
            systemSettingService.saveSetting("PRICE_CHAIN", priceChain, "Đơn giá sửa xích sên");
            systemSettingService.saveSetting("PRICE_BRAKE", priceBrake, "Đơn giá sửa phanh/thắng");
            systemSettingService.saveSetting("PRICE_ENGINE", priceEngine, "Đơn giá sửa động cơ/khác");
            return ResponseEntity.ok(ApiResponse.ok("Cập nhật bảng giá dịch vụ thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Lấy thông tin chi tiết hóa đơn và cuốc cứu hộ liên quan
     */
    @GetMapping("/reports/invoices/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse> getInvoiceDetail(@PathVariable UUID id) {
        try {
            Invoice inv = invoiceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hóa đơn!"));
            
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("invoiceId", inv.getInvoiceId().toString());
            data.put("totalAmount", inv.getTotalAmount());
            data.put("systemFee", inv.getSystemFee());
            data.put("paymentMethod", inv.getPaymentMethod().name());
            data.put("paid", inv.isPaid());
            data.put("createdAt", inv.getCreatedAt() != null ? inv.getCreatedAt().toString() : "");
            
            RescueRequest req = inv.getRescueRequest();
            if (req != null) {
                data.put("requestId", req.getRequestId().toString());
                data.put("issueDesc", req.getIssueDesc());
                data.put("aiDiagnosis", req.getAiDiagnosis());
                data.put("basePrice", req.getBasePrice());
                data.put("extraPrice", req.getExtraPrice());
                data.put("customerAddress", req.getCustomerAddress());
                
                if (req.getCustomer() != null) {
                    data.put("customerName", req.getCustomer().getFullName());
                    data.put("customerPhone", req.getCustomer().getPhone());
                } else {
                    data.put("customerName", "N/A");
                    data.put("customerPhone", "N/A");
                }
                
                if (req.getRescuer() != null) {
                    data.put("rescuerName", req.getRescuer().getFullName());
                    data.put("rescuerPhone", req.getRescuer().getPhone());
                } else {
                    data.put("rescuerName", "Chưa nhận");
                    data.put("rescuerPhone", "N/A");
                }
            }
            
            return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết hóa đơn thành công", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Xuất danh sách hóa đơn ra file CSV cho kế toán
     */
    @GetMapping("/reports/export-csv")
    public void exportInvoicesToCsv(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"Bao_cao_hoa_don_eRescue_" + java.time.LocalDate.now() + ".csv\"");

        // BOM UTF-8 for Excel compatibility
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        java.io.PrintWriter writer = response.getWriter();
        writer.println("Mã Hóa Đơn,Mã Cuốc Xe,Tên Khách Hàng,Tên Thợ,Phương Thức,Tổng Tiền (VND),Phí Hệ Thống (VND),Trạng Thái,Thời Gian");

        java.util.List<web.rescue.erp.entity.Invoice> list = invoiceRepository.findAll();
        for (web.rescue.erp.entity.Invoice invoice : list) {
            String invoiceId = invoice.getInvoiceId().toString();
            String reqId = invoice.getRescueRequest().getRequestId().toString();
            String customerName = invoice.getRescueRequest().getCustomer().getFullName() != null ? invoice.getRescueRequest().getCustomer().getFullName() : "N/A";
            String rescuerName = (invoice.getRescueRequest().getRescuer() != null && invoice.getRescueRequest().getRescuer().getFullName() != null) ? invoice.getRescueRequest().getRescuer().getFullName() : "Chưa nhận";
            String paymentMethod = invoice.getPaymentMethod().name();
            String totalAmount = invoice.getTotalAmount().toString();
            String systemFee = invoice.getSystemFee().toString();
            String status = invoice.isPaid() ? "Đã trả" : "Chờ xử lý";
            String time = invoice.getCreatedAt() != null ? invoice.getCreatedAt().toString().replace('T', ' ') : "N/A";

            writer.println(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,\"%s\",\"%s\"",
                    invoiceId, reqId, customerName, rescuerName, paymentMethod, totalAmount, systemFee, status, time));
        }
        writer.flush();
    }
}
