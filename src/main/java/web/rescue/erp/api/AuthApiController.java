package web.rescue.erp.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.rescue.erp.dto.ApiResponse;
import web.rescue.erp.dto.ProfileUpdateRequest;
import web.rescue.erp.dto.RegisterRequest;
import web.rescue.erp.entity.User;
import web.rescue.erp.service.OtpService;
import web.rescue.erp.service.UserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final UserService userService;
    private final OtpService otpService;

    /**
     * Gửi OTP đăng ký
     */
    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse> sendOtp(@RequestParam String phone) {
        try {
            if (userService.findByPhone(phone).isPresent()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Số điện thoại đã được đăng ký!"));
            }
            String otp = otpService.generateOtp(phone);
            // Trong dev, trả OTP trực tiếp để test. Production sẽ gửi SMS.
            return ResponseEntity.ok(ApiResponse.ok("OTP đã được gửi đến " + phone, otp));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Xác nhận OTP
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse> verifyOtp(@RequestParam String phone,
                                                  @RequestParam String otp) {
        if (otpService.verifyOtp(phone, otp)) {
            return ResponseEntity.ok(ApiResponse.ok("Xác nhận OTP thành công!"));
        }
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Mã OTP không đúng hoặc đã hết hạn!"));
    }

    /**
     * Đăng ký tài khoản Customer
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@RequestBody RegisterRequest request) {
        try {
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Mật khẩu xác nhận không khớp!"));
            }
            User user = userService.register(request);
            return ResponseEntity.ok(ApiResponse.ok("Đăng ký thành công! Vui lòng đăng nhập.", user.getUserId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Đăng ký tài khoản Rescuer
     */
    @PostMapping("/register-rescuer")
    public ResponseEntity<ApiResponse> registerRescuer(@RequestBody RegisterRequest request) {
        try {
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Mật khẩu xác nhận không khớp!"));
            }
            User user = userService.registerRescuer(request);
            return ResponseEntity.ok(ApiResponse.ok("Đăng ký Đội cứu hộ thành công! Vui lòng chờ duyệt hồ sơ.", user.getUserId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Cập nhật profile
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse> updateProfile(@RequestBody ProfileUpdateRequest request,
                                                      Authentication authentication) {
        try {
            User user = userService.findByPhone(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            userService.updateProfile(user.getUserId(), request);
            return ResponseEntity.ok(ApiResponse.ok("Cập nhật thành tin thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Upload avatar
     */
    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                     Authentication authentication) {
        try {
            User user = userService.findByPhone(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String uploadDir = "uploads/avatars";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = user.getUserId() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());

            String avatarUrl = "/uploads/avatars/" + filename;
            userService.updateAvatar(user.getUserId(), avatarUrl);

            return ResponseEntity.ok(ApiResponse.ok("Upload avatar thành công!", avatarUrl));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Upload thất bại: " + e.getMessage()));
        }
    }

    /**
     * Toggle 2FA
     */
    @PostMapping("/toggle-2fa")
    public ResponseEntity<ApiResponse> toggleTwoFactor(Authentication authentication) {
        try {
            User user = userService.findByPhone(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            userService.toggleTwoFactor(user.getUserId());
            User updated = userService.findById(user.getUserId()).orElseThrow();
            String status = updated.isTwoFactorEnabled() ? "BẬT" : "TẮT";
            return ResponseEntity.ok(ApiResponse.ok("Bảo mật 2FA đã được " + status));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Lấy thông tin user hiện tại
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse> getCurrentUser(Authentication authentication) {
        try {
            User user = userService.findByPhone(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return ResponseEntity.ok(ApiResponse.ok("OK", user));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
