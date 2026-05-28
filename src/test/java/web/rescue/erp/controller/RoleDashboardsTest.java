package web.rescue.erp.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.Role;
import web.rescue.erp.service.RescuerService;
import web.rescue.erp.service.UserService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleDashboardsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RescuerService rescuerService;

    @Test
    @WithMockUser(username = "0901111111", roles = {"CUSTOMER"})
    void customerDashboard_AsCustomer_ShouldReturnCustomerDashboardView() throws Exception {
        User customer = User.builder().phone("0901111111").role(Role.CUSTOMER).build();
        when(userService.findByPhone("0901111111")).thenReturn(Optional.of(customer));

        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/dashboard"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    @WithMockUser(username = "0901111111", roles = {"CUSTOMER"})
    void rescuerDashboard_AsCustomer_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/rescuer/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void rescuerDashboard_AsRescuer_ShouldReturnRescuerDashboardView() throws Exception {
        UUID userId = UUID.randomUUID();
        User rescuer = User.builder().userId(userId).phone("0902222222").role(Role.RESCUER).build();
        RescuerProfile profile = RescuerProfile.builder().rescuerId(userId).build();

        when(userService.findByPhone("0902222222")).thenReturn(Optional.of(rescuer));
        when(rescuerService.findById(userId)).thenReturn(Optional.of(profile));

        mockMvc.perform(get("/rescuer/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("rescuer/dashboard"))
                .andExpect(model().attributeExists("user"))
                .andExpect(model().attributeExists("profile"));
    }

    @Test
    @WithMockUser(username = "0902222222", roles = {"RESCUER"})
    void customerDashboard_AsRescuer_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().isForbidden());
    }
}
