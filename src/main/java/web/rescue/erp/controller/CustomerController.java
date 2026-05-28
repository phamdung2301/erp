package web.rescue.erp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import web.rescue.erp.entity.User;
import web.rescue.erp.service.UserService;
import web.rescue.erp.service.SystemSettingService;

@Controller
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final UserService userService;
    private final SystemSettingService systemSettingService;

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        model.addAttribute("user", user);
        model.addAttribute("adminBankName", systemSettingService.getAdminBankName());
        model.addAttribute("adminBankAccountNo", systemSettingService.getAdminBankAccountNo());
        model.addAttribute("adminBankAccountName", systemSettingService.getAdminBankAccountName());
        return "customer/dashboard";
    }
}
