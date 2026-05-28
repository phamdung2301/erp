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
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.repository.RescuerProfileRepository;
import web.rescue.erp.service.RescueRequestService;
import web.rescue.erp.service.RescuerService;
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
class RescuerApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RescuerService rescuerService;

    @MockitoBean
    private RescueRequestService rescueRequestService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RescuerProfileRepository rescuerProfileRepository;

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void toggleOnline_ShouldReturnOk() throws Exception {
        User rescuer = User.builder().userId(UUID.randomUUID()).phone("0902222222").build();
        when(userService.findByPhone("0902222222")).thenReturn(Optional.of(rescuer));
        doNothing().when(rescuerService).toggleOnline(rescuer.getUserId(), true);

        mockMvc.perform(post("/api/rescuer/toggle-online")
                        .param("online", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã bật trạng thái làm việc online!"));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void updateLocation_ShouldReturnOk() throws Exception {
        User rescuer = User.builder().userId(UUID.randomUUID()).phone("0902222222").build();
        when(userService.findByPhone("0902222222")).thenReturn(Optional.of(rescuer));
        doNothing().when(rescuerService).updateLocation(any(), any(), any());

        mockMvc.perform(post("/api/rescuer/location")
                        .param("lat", "21.0305")
                        .param("lng", "105.8522"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void getIncomingRequests_Online_ShouldReturnList() throws Exception {
        User rescuer = User.builder().userId(UUID.randomUUID()).phone("0902222222").build();
        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuer.getUserId())
                .isOnline(true)
                .currentLat(new BigDecimal("21.0305"))
                .currentLng(new BigDecimal("105.8522"))
                .build();
        User customer = User.builder().fullName("Nguyễn Văn Khách").phone("0901111111").build();
        RescueRequest req = RescueRequest.builder()
                .requestId(UUID.randomUUID())
                .issueDesc("thủng lốp")
                .aiDiagnosis("Thủng săm lốp\nVá săm lốp")
                .customerAddress("Hà Nội")
                .customer(customer)
                .basePrice(new BigDecimal("60000.00"))
                .customerLat(new BigDecimal("21.0300"))
                .customerLng(new BigDecimal("105.8520"))
                .build();

        when(userService.findByPhone("0902222222")).thenReturn(Optional.of(rescuer));
        when(rescuerProfileRepository.findById(rescuer.getUserId())).thenReturn(Optional.of(profile));
        when(rescueRequestService.getNearbyPendingRequests(any(), any())).thenReturn(Collections.singletonList(req));

        mockMvc.perform(get("/api/rescuer/incoming-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].customerName").value("Nguyễn Văn Khách"))
                .andExpect(jsonPath("$.data[0].issueDesc").value("thủng lốp"));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void getActiveRequest_ShouldReturnRequestDetails() throws Exception {
        User rescuer = User.builder().userId(UUID.randomUUID()).phone("0902222222").build();
        User customer = User.builder().fullName("Nguyễn Văn Khách").phone("0901111111").build();
        RescueRequest req = RescueRequest.builder()
                .requestId(UUID.randomUUID())
                .customer(customer)
                .issueDesc("thủng lốp")
                .aiDiagnosis("Thủng săm lốp")
                .status(RequestStatus.ACCEPTED)
                .customerLat(new BigDecimal("21.0300"))
                .customerLng(new BigDecimal("105.8520"))
                .customerAddress("Hà Nội")
                .basePrice(new BigDecimal("60000.00"))
                .extraPrice(BigDecimal.ZERO)
                .build();

        when(userService.findByPhone("0902222222")).thenReturn(Optional.of(rescuer));
        when(rescueRequestService.getActiveRequestForRescuer(rescuer.getUserId())).thenReturn(Optional.of(req));

        mockMvc.perform(get("/api/rescuer/request/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.customerName").value("Nguyễn Văn Khách"));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void acceptRequest_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        User rescuer = User.builder().userId(UUID.randomUUID()).phone("0902222222").build();
        RescuerProfile profile = RescuerProfile.builder().rescuerId(rescuer.getUserId()).isOnline(true).build();

        when(userService.findByPhone("0902222222")).thenReturn(Optional.of(rescuer));
        when(rescuerProfileRepository.findById(rescuer.getUserId())).thenReturn(Optional.of(profile));
        when(rescueRequestService.getActiveRequestForRescuer(rescuer.getUserId())).thenReturn(Optional.empty());
        doNothing().when(rescueRequestService).acceptRequestByRescuer(requestId, rescuer);

        mockMvc.perform(post("/api/rescuer/request/{id}/accept", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void arrive_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(rescueRequestService).updateRequestStatus(requestId, RequestStatus.ARRIVED);

        mockMvc.perform(post("/api/rescuer/request/{id}/arrive", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void startRepair_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(rescueRequestService).updateRequestStatus(requestId, RequestStatus.IN_PROGRESS);

        mockMvc.perform(post("/api/rescuer/request/{id}/start-repair", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void updateExtra_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(rescueRequestService).addExtraPrice(requestId, new BigDecimal("20000.00"));

        mockMvc.perform(post("/api/rescuer/request/{id}/update-extra", requestId)
                        .param("extraPrice", "20000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void complete_ShouldReturnOk() throws Exception {
        UUID requestId = UUID.randomUUID();
        doNothing().when(rescueRequestService).completeRequestWithOtp(requestId, "123456");

        mockMvc.perform(post("/api/rescuer/request/{id}/complete", requestId)
                        .param("otp", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void payout_ShouldReturnOk() throws Exception {
        User rescuer = User.builder().userId(UUID.randomUUID()).phone("0902222222").build();
        when(userService.findByPhone("0902222222")).thenReturn(Optional.of(rescuer));
        doNothing().when(rescuerService).payoutRequest(
                eq(rescuer.getUserId()), 
                eq(new BigDecimal("100000.00")), 
                eq("Vietcombank"), 
                eq("1234567890"), 
                eq("NGUYEN VAN THO")
        );

        mockMvc.perform(post("/api/rescuer/wallet/payout")
                        .param("amount", "100000.00")
                        .param("bankName", "Vietcombank")
                        .param("bankAccountNo", "1234567890")
                        .param("bankAccountName", "NGUYEN VAN THO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
