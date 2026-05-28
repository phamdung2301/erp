package web.rescue.erp.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.rescue.erp.entity.Invoice;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.service.AiDiagnosticService;
import web.rescue.erp.service.RescueRequestService;
import web.rescue.erp.service.UserService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RescueRequestService rescueRequestService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AiDiagnosticService aiDiagnosticService;

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void getNearbyRescuers_ShouldReturnOk() throws Exception {
        when(rescueRequestService.findNearbyRescuers(any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/customer/nearby-rescuers")
                        .param("lat", "21.0285")
                        .param("lng", "105.8542"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void diagnose_ShouldReturnOk() throws Exception {
        AiDiagnosticService.DiagnosticResult diag = new AiDiagnosticService.DiagnosticResult(
                "Lỗi xích", "Cao", "Thay xích", new BigDecimal("100000.00")
        );
        when(aiDiagnosticService.diagnose("đứt xích")).thenReturn(diag);

        mockMvc.perform(post("/api/customer/diagnose")
                        .param("issueDesc", "đứt xích"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.issueName").value("Lỗi xích"));
    }

    @Test
    @WithMockUser(username = "0901111111", roles = {"CUSTOMER"})
    void createRequest_ShouldSaveAndReturnOk() throws Exception {
        User customer = User.builder().userId(UUID.randomUUID()).phone("0901111111").build();
        RescueRequest req = RescueRequest.builder().requestId(UUID.randomUUID()).status(RequestStatus.PENDING).build();

        when(userService.findByPhone("0901111111")).thenReturn(Optional.of(customer));
        when(rescueRequestService.getActiveRequestForCustomer(customer.getUserId())).thenReturn(Optional.empty());
        when(rescueRequestService.createRequest(any(), any(), any(), any(), any())).thenReturn(req);

        mockMvc.perform(post("/api/customer/request")
                        .param("issueDesc", "thủng lốp")
                        .param("lat", "21.0285")
                        .param("lng", "105.8542")
                        .param("address", "Hà Nội"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "0901111111", roles = {"CUSTOMER"})
    void getActiveRequest_ShouldReturnRequestDetails() throws Exception {
        User customer = User.builder().userId(UUID.randomUUID()).phone("0901111111").build();
        RescueRequest req = RescueRequest.builder()
                .requestId(UUID.randomUUID())
                .issueDesc("thủng lốp")
                .status(RequestStatus.PENDING)
                .basePrice(new BigDecimal("60000.00"))
                .extraPrice(BigDecimal.ZERO)
                .otpCode("123456")
                .build();

        when(userService.findByPhone("0901111111")).thenReturn(Optional.of(customer));
        when(rescueRequestService.getActiveRequestForCustomer(customer.getUserId())).thenReturn(Optional.of(req));

        mockMvc.perform(get("/api/customer/request/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.otpCode").value("123456"))
                .andExpect(jsonPath("$.data.basePrice").value(60000.00));
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void cancelRequest_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(rescueRequestService).cancelRequest(requestId);

        mockMvc.perform(post("/api/customer/request/{id}/cancel", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void simulateStep_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(rescueRequestService).simulateRescuerAction(requestId, "ACCEPT");

        mockMvc.perform(post("/api/customer/request/{id}/simulate-step", requestId)
                        .param("action", "ACCEPT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void payAndRate_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(rescueRequestService).payAndRateRequest(requestId, "WALLET", 5, "Tốt");

        mockMvc.perform(post("/api/customer/request/{id}/pay-rate", requestId)
                        .param("paymentMethod", "WALLET")
                        .param("rating", "5")
                        .param("feedback", "Tốt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void updatePaymentMethod_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(rescueRequestService).updatePaymentMethod(requestId, "CASH");

        mockMvc.perform(post("/api/customer/request/{id}/payment-method", requestId)
                        .param("paymentMethod", "CASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật phương thức thanh toán thành công!"));

        verify(rescueRequestService, times(1)).updatePaymentMethod(requestId, "CASH");
    }
}
