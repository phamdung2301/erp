package web.rescue.erp.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.rescue.erp.service.PayoutService;
import web.rescue.erp.service.RescuerService;
import web.rescue.erp.service.SystemSettingService;
import web.rescue.erp.service.UserService;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RescuerService rescuerService;

    @MockitoBean
    private PayoutService payoutService;

    @MockitoBean
    private SystemSettingService systemSettingService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void lockUser_AsAdmin_ShouldLockUserAndReturnOk() throws Exception {
        UUID userId = UUID.randomUUID();
        doNothing().when(userService).lockUser(userId);

        mockMvc.perform(put("/api/admin/users/{id}/lock", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã khóa tài khoản thành công!"));

        verify(userService, times(1)).lockUser(userId);
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void lockUser_AsCustomer_ShouldReturnForbidden() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(put("/api/admin/users/{id}/lock", userId))
                .andExpect(status().isForbidden());

        verify(userService, never()).lockUser(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void unlockUser_AsAdmin_ShouldUnlockUserAndReturnOk() throws Exception {
        UUID userId = UUID.randomUUID();
        doNothing().when(userService).unlockUser(userId);

        mockMvc.perform(put("/api/admin/users/{id}/unlock", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã mở khóa tài khoản thành công!"));

        verify(userService, times(1)).unlockUser(userId);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void verifyRescuer_AsAdmin_ShouldVerifyAndReturnOk() throws Exception {
        UUID rescuerId = UUID.randomUUID();
        doNothing().when(rescuerService).verifyRescuer(rescuerId);

        mockMvc.perform(put("/api/admin/rescuers/{id}/verify", rescuerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã duyệt hồ sơ đối tác thành công!"));

        verify(rescuerService, times(1)).verifyRescuer(rescuerId);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void rejectRescuer_AsAdmin_ShouldRejectAndReturnOk() throws Exception {
        UUID rescuerId = UUID.randomUUID();
        doNothing().when(rescuerService).rejectRescuer(rescuerId);

        mockMvc.perform(put("/api/admin/rescuers/{id}/reject", rescuerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã từ chối hồ sơ đối tác!"));

        verify(rescuerService, times(1)).rejectRescuer(rescuerId);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void approvePayout_AsAdmin_ShouldApproveAndReturnOk() throws Exception {
        UUID payoutId = UUID.randomUUID();
        when(payoutService.approvePayout(payoutId)).thenReturn(null);

        mockMvc.perform(post("/api/admin/payouts/{id}/approve", payoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Phê duyệt yêu cầu rút tiền thành công!"));

        verify(payoutService, times(1)).approvePayout(payoutId);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void rejectPayout_AsAdmin_ShouldRejectAndReturnOk() throws Exception {
        UUID payoutId = UUID.randomUUID();
        when(payoutService.rejectPayout(payoutId)).thenReturn(null);

        mockMvc.perform(post("/api/admin/payouts/{id}/reject", payoutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã từ chối yêu cầu rút tiền!"));

        verify(payoutService, times(1)).rejectPayout(payoutId);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updateSettings_AsAdmin_ShouldSaveSettingAndReturnOk() throws Exception {
        doNothing().when(systemSettingService).saveSetting(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/admin/settings/update")
                        .param("systemFeePercent", "15.0")
                        .param("adminBankName", "Vietcombank")
                        .param("adminBankAccountNo", "123456789")
                        .param("adminBankAccountName", "ADMIN DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật cài đặt hệ thống thành công!"));

        verify(systemSettingService, times(1)).saveSetting(eq("SYSTEM_FEE_PERCENT"), eq("15.0"), anyString());
        verify(systemSettingService, times(1)).saveSetting(eq("ADMIN_BANK_NAME"), eq("Vietcombank"), anyString());
        verify(systemSettingService, times(1)).saveSetting(eq("ADMIN_BANK_ACCOUNT_NO"), eq("123456789"), anyString());
        verify(systemSettingService, times(1)).saveSetting(eq("ADMIN_BANK_ACCOUNT_NAME"), eq("ADMIN DEPOSIT"), anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void updatePriceList_AsAdmin_ShouldSaveAllPricesAndReturnOk() throws Exception {
        doNothing().when(systemSettingService).saveSetting(anyString(), anyString(), anyString());

        mockMvc.perform(post("/api/admin/price-list/update")
                        .param("pricePuncture", "60000")
                        .param("priceBattery", "150000")
                        .param("priceChain", "120000")
                        .param("priceBrake", "90000")
                        .param("priceEngine", "200000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật bảng giá dịch vụ thành công!"));

        verify(systemSettingService, times(5)).saveSetting(anyString(), anyString(), anyString());
    }
}
