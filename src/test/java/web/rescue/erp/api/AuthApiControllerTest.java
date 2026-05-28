package web.rescue.erp.api;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.rescue.erp.dto.ProfileUpdateRequest;
import web.rescue.erp.dto.RegisterRequest;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.Role;
import web.rescue.erp.service.OtpService;
import web.rescue.erp.service.UserService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OtpService otpService;

    @Test
    void sendOtp_PhoneAlreadyRegistered_ShouldReturnBadRequest() throws Exception {
        String phone = "0987654321";
        when(userService.findByPhone(phone)).thenReturn(Optional.of(new User()));

        mockMvc.perform(post("/api/auth/send-otp")
                        .param("phone", phone))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Số điện thoại đã được đăng ký!"));
    }

    @Test
    void sendOtp_PhoneNotRegistered_ShouldReturnOkWithOtp() throws Exception {
        String phone = "0987654321";
        when(userService.findByPhone(phone)).thenReturn(Optional.empty());
        when(otpService.generateOtp(phone)).thenReturn("123456");

        mockMvc.perform(post("/api/auth/send-otp")
                        .param("phone", phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("123456"));
    }

    @Test
    void verifyOtp_CorrectOtp_ShouldReturnOk() throws Exception {
        String phone = "0987654321";
        String otp = "123456";
        when(otpService.verifyOtp(phone, otp)).thenReturn(true);

        mockMvc.perform(post("/api/auth/verify-otp")
                        .param("phone", phone)
                        .param("otp", otp))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Xác nhận OTP thành công!"));
    }

    @Test
    void verifyOtp_IncorrectOtp_ShouldReturnBadRequest() throws Exception {
        String phone = "0987654321";
        String otp = "123456";
        when(otpService.verifyOtp(phone, otp)).thenReturn(false);

        mockMvc.perform(post("/api/auth/verify-otp")
                        .param("phone", phone)
                        .param("otp", otp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void register_MatchingPassword_ShouldReturnOk() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("0987654321");
        request.setPassword("password123");
        request.setConfirmPassword("password123");
        request.setFullName("Test User");

        User savedUser = User.builder()
                .userId(UUID.randomUUID())
                .phone(request.getPhone())
                .build();
        when(userService.register(any(RegisterRequest.class))).thenReturn(savedUser);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng ký thành công! Vui lòng đăng nhập."));
    }

    @Test
    void register_MismatchPassword_ShouldReturnBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setPhone("0987654321");
        request.setPassword("password123");
        request.setConfirmPassword("password321");
        request.setFullName("Test User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mật khẩu xác nhận không khớp!"));
    }

    @Test
    @WithMockUser(username = "0987654321")
    void updateProfile_Authenticated_ShouldReturnOk() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setFullName("New Name");

        User user = User.builder()
                .userId(UUID.randomUUID())
                .phone("0987654321")
                .build();
        when(userService.findByPhone("0987654321")).thenReturn(Optional.of(user));

        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateProfile_Unauthenticated_ShouldReturnRedirectOrForbidden() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setFullName("New Name");

        // Unauthenticated access should be redirected to login because of SecurityFilterChain config
        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "0987654321")
    void toggleTwoFactor_Authenticated_ShouldReturnOk() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .phone("0987654321")
                .twoFactorEnabled(true)
                .build();
        
        when(userService.findByPhone("0987654321")).thenReturn(Optional.of(user));
        when(userService.findById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/toggle-2fa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Bảo mật 2FA đã được BẬT"));
    }
}
