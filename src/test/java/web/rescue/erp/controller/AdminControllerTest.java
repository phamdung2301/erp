package web.rescue.erp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.entity.enums.Role;
import web.rescue.erp.repository.RescueRequestRepository;
import web.rescue.erp.service.RescuerService;
import web.rescue.erp.service.UserService;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RescuerService rescuerService;

    @MockitoBean
    private RescueRequestRepository rescueRequestRepository;

    @Test
    @WithMockUser(username = "0900000000", roles = {"ADMIN"})
    void dashboard_AsAdmin_ShouldReturnDashboardViewWithStats() throws Exception {
        User admin = User.builder().phone("0900000000").role(Role.ADMIN).build();
        when(userService.findByPhone("0900000000")).thenReturn(Optional.of(admin));
        when(userService.countTotal()).thenReturn(10L);
        when(userService.countByRole(Role.CUSTOMER)).thenReturn(7L);
        when(userService.countByRole(Role.RESCUER)).thenReturn(2L);
        when(rescuerService.countPendingVerification()).thenReturn(1L);
        when(rescuerService.countOnline()).thenReturn(1L);
        when(rescueRequestRepository.countByStatus(RequestStatus.PENDING)).thenReturn(0L);

        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(model().attribute("totalUsers", 10L))
                .andExpect(model().attribute("totalCustomers", 7L))
                .andExpect(model().attribute("totalRescuers", 2L))
                .andExpect(model().attribute("pendingVerifications", 1L))
                .andExpect(model().attribute("onlineRescuers", 1L))
                .andExpect(model().attribute("pendingRequests", 0L));
    }

    @Test
    @WithMockUser(username = "0901111111", roles = {"CUSTOMER"})
    void dashboard_AsCustomer_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "0900000000", roles = {"ADMIN"})
    void usersPage_AsAdmin_ShouldReturnUsersView() throws Exception {
        User admin = User.builder().phone("0900000000").role(Role.ADMIN).build();
        when(userService.findByPhone("0900000000")).thenReturn(Optional.of(admin));
        when(userService.findAll()).thenReturn(Collections.singletonList(admin));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(model().attributeExists("users"));
    }

    @Test
    @WithMockUser(username = "0900000000", roles = {"ADMIN"})
    void rescuerVerificationPage_AsAdmin_ShouldReturnVerificationView() throws Exception {
        User admin = User.builder().phone("0900000000").role(Role.ADMIN).build();
        when(userService.findByPhone("0900000000")).thenReturn(Optional.of(admin));
        when(rescuerService.findPendingVerification()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/admin/rescuer-verification"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/rescuer-verification"))
                .andExpect(model().attributeExists("pendingRescuers"));
    }

    @Test
    @WithMockUser(username = "0900000000", roles = {"ADMIN"})
    void payoutsPage_AsAdmin_ShouldReturnPayoutsView() throws Exception {
        User admin = User.builder().phone("0900000000").role(Role.ADMIN).build();
        when(userService.findByPhone("0900000000")).thenReturn(Optional.of(admin));

        mockMvc.perform(get("/admin/payouts"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/payouts"))
                .andExpect(model().attributeExists("pendingPayoutRequests"))
                .andExpect(model().attributeExists("approvedPayoutRequests"))
                .andExpect(model().attributeExists("rejectedPayoutRequests"));
    }
}
