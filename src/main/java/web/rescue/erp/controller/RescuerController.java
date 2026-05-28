package web.rescue.erp.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.repository.RescueRequestRepository;
import web.rescue.erp.service.RescuerService;
import web.rescue.erp.service.UserService;
import web.rescue.erp.service.SystemSettingService;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/rescuer")
@RequiredArgsConstructor
public class RescuerController {

    private final UserService userService;
    private final RescuerService rescuerService;
    private final SystemSettingService systemSettingService;
    private final RescueRequestRepository rescueRequestRepository;

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        User user = userService.findByPhone(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        RescuerProfile profile = rescuerService.findById(user.getUserId())
                .orElseThrow(() -> new RuntimeException("Rescuer profile not found"));
        
        List<RescueRequest> completedRequests = rescueRequestRepository.findByRescuerUserIdOrderByCreatedAtDesc(user.getUserId())
                .stream()
                .filter(r -> r.getStatus() == RequestStatus.COMPLETED)
                .collect(Collectors.toList());
        
        model.addAttribute("user", user);
        model.addAttribute("profile", profile);
        model.addAttribute("completedRequests", completedRequests);
        model.addAttribute("adminBankName", systemSettingService.getAdminBankName());
        model.addAttribute("adminBankAccountNo", systemSettingService.getAdminBankAccountNo());
        model.addAttribute("adminBankAccountName", systemSettingService.getAdminBankAccountName());
        return "rescuer/dashboard";
    }
}
