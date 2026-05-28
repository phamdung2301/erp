package web.rescue.erp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.Role;
import web.rescue.erp.entity.enums.UserStatus;
import web.rescue.erp.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsername_UserExistsAndActive_ShouldReturnUserDetails() {
        String phone = "0987654321";
        User user = User.builder()
                .phone(phone)
                .passwordHash("hashedPassword")
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(phone);

        assertNotNull(userDetails);
        assertEquals(phone, userDetails.getUsername());
        assertEquals("hashedPassword", userDetails.getPassword());
        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.isAccountNonLocked());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_CUSTOMER")));

        verify(userRepository, times(1)).findByPhone(phone);
    }

    @Test
    void loadUserByUsername_UserExistsAndLocked_ShouldReturnUserDetailsWithDisabledStatus() {
        String phone = "0987654321";
        User user = User.builder()
                .phone(phone)
                .passwordHash("hashedPassword")
                .role(Role.RESCUER)
                .status(UserStatus.LOCKED)
                .build();

        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(phone);

        assertNotNull(userDetails);
        assertEquals(phone, userDetails.getUsername());
        assertFalse(userDetails.isEnabled());
        assertFalse(userDetails.isAccountNonLocked());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_RESCUER")));
    }

    @Test
    void loadUserByUsername_UserDoesNotExist_ShouldThrowUsernameNotFoundException() {
        String phone = "0987654321";
        when(userRepository.findByPhone(phone)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            customUserDetailsService.loadUserByUsername(phone);
        });

        verify(userRepository, times(1)).findByPhone(phone);
    }
}
