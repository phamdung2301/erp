package web.rescue.erp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import web.rescue.erp.entity.Invoice;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.PayoutStatus;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.entity.enums.Role;
import web.rescue.erp.repository.InvoiceRepository;
import web.rescue.erp.repository.RescueRequestRepository;
import web.rescue.erp.service.PayoutService;
import web.rescue.erp.service.RescuerService;
import web.rescue.erp.service.SystemSettingService;
import web.rescue.erp.service.UserService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final RescuerService rescuerService;
    private final RescueRequestRepository rescueRequestRepository;
    private final PayoutService payoutService;
    private final SystemSettingService systemSettingService;
    private final InvoiceRepository invoiceRepository;

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        model.addAttribute("pendingPayouts", payoutService.getPendingCount());
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);

        // Stats
        model.addAttribute("totalUsers", userService.countTotal());
        model.addAttribute("totalCustomers", userService.countByRole(Role.CUSTOMER));
        model.addAttribute("totalRescuers", userService.countByRole(Role.RESCUER));
        model.addAttribute("pendingVerifications", rescuerService.countPendingVerification());
        model.addAttribute("onlineRescuers", rescuerService.countOnline());
        model.addAttribute("pendingRequests", rescueRequestRepository.countByStatus(RequestStatus.PENDING));

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String usersPage(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("users", userService.findAll());
        return "admin/users";
    }

    @GetMapping("/rescuer-verification")
    public String rescuerVerificationPage(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("pendingRescuers", rescuerService.findPendingVerification());
        return "admin/rescuer-verification";
    }

    @GetMapping("/cash-debts")
    public String cashDebtsPage(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("rescuersWithDebt", rescuerService.findRescuersWithCashDebt());
        return "admin/cash-debts";
    }

    @GetMapping("/payouts")
    public String payoutsPage(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        
        model.addAttribute("pendingPayoutRequests", payoutService.findByStatus(PayoutStatus.PENDING));
        model.addAttribute("approvedPayoutRequests", payoutService.findByStatus(PayoutStatus.APPROVED));
        model.addAttribute("rejectedPayoutRequests", payoutService.findByStatus(PayoutStatus.REJECTED));
        
        return "admin/payouts";
    }

    @GetMapping("/price-list")
    public String priceListPage(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        
        model.addAttribute("pricePuncture", systemSettingService.getPuncturePrice());
        model.addAttribute("priceBattery", systemSettingService.getBatteryPrice());
        model.addAttribute("priceChain", systemSettingService.getChainPrice());
        model.addAttribute("priceBrake", systemSettingService.getBrakePrice());
        model.addAttribute("priceEngine", systemSettingService.getEnginePrice());
        
        return "admin/price-list";
    }

    @GetMapping("/settings")
    public String settingsPage(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        
        model.addAttribute("systemFeePercent", systemSettingService.getSystemFeePercent());
        model.addAttribute("adminBankName", systemSettingService.getAdminBankName());
        model.addAttribute("adminBankAccountNo", systemSettingService.getAdminBankAccountNo());
        model.addAttribute("adminBankAccountName", systemSettingService.getAdminBankAccountName());
        
        return "admin/settings";
    }

    @GetMapping("/reports")
    public String reportsPage(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);

        List<Invoice> invoices = invoiceRepository.findAll();
        BigDecimal totalRevenue = invoices.stream()
                .filter(Invoice::isPaid)
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSystemFee = invoices.stream()
                .filter(Invoice::isPaid)
                .map(Invoice::getSystemFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RescueRequest> requests = rescueRequestRepository.findAll();
        long completedRequests = requests.stream().filter(r -> r.getStatus() == RequestStatus.COMPLETED).count();
        long canceledRequests = requests.stream().filter(r -> r.getStatus() == RequestStatus.CANCELED).count();
        double successRate = 0.0;
        if (completedRequests + canceledRequests > 0) {
            successRate = (double) completedRequests / (completedRequests + canceledRequests) * 100;
        }

        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalSystemFee", totalSystemFee);
        model.addAttribute("completedRequests", completedRequests);
        model.addAttribute("canceledRequests", canceledRequests);
        model.addAttribute("successRate", successRate);
        model.addAttribute("recentInvoices", invoices);

        // Group revenue and system fees by month (last 6 months)
        Map<String, BigDecimal> monthlyRevenue = new LinkedHashMap<>();
        Map<String, BigDecimal> monthlyFee = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            String label = "T" + ym.getMonthValue() + "/" + ym.getYear();
            monthlyRevenue.put(label, BigDecimal.ZERO);
            monthlyFee.put(label, BigDecimal.ZERO);
        }

        for (Invoice invoice : invoices) {
            if (invoice.isPaid() && invoice.getCreatedAt() != null) {
                LocalDateTime ca = invoice.getCreatedAt();
                String label = "T" + ca.getMonthValue() + "/" + ca.getYear();
                if (monthlyRevenue.containsKey(label)) {
                    monthlyRevenue.put(label, monthlyRevenue.get(label).add(invoice.getTotalAmount()));
                    monthlyFee.put(label, monthlyFee.get(label).add(invoice.getSystemFee()));
                }
            }
        }

        model.addAttribute("chartLabels", monthlyRevenue.keySet());
        model.addAttribute("chartRevenue", monthlyRevenue.values());
        model.addAttribute("chartFee", monthlyFee.values());

        // Count by issue type for pie chart
        long punctureCount = requests.stream().filter(r -> r.getAiDiagnosis() != null && r.getAiDiagnosis().contains("săm/lốp")).count();
        long batteryCount = requests.stream().filter(r -> r.getAiDiagnosis() != null && r.getAiDiagnosis().contains("Ắc quy")).count();
        long chainCount = requests.stream().filter(r -> r.getAiDiagnosis() != null && r.getAiDiagnosis().contains("xích")).count();
        long brakeCount = requests.stream().filter(r -> r.getAiDiagnosis() != null && r.getAiDiagnosis().contains("phanh")).count();
        long engineCount = requests.stream().filter(r -> r.getAiDiagnosis() != null && r.getAiDiagnosis().contains("động cơ")).count();
        long otherCount = requests.size() - punctureCount - batteryCount - chainCount - brakeCount - engineCount;
        if (otherCount < 0) otherCount = 0;

        model.addAttribute("punctureCount", punctureCount);
        model.addAttribute("batteryCount", batteryCount);
        model.addAttribute("chainCount", chainCount);
        model.addAttribute("brakeCount", brakeCount);
        model.addAttribute("engineCount", engineCount);
        model.addAttribute("otherCount", otherCount);

        return "admin/reports";
    }
}
