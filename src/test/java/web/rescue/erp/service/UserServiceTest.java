package web.rescue.erp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import web.rescue.erp.dto.ProfileUpdateRequest;
import web.rescue.erp.dto.RegisterRequest;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.Role;
import web.rescue.erp.entity.enums.UserStatus;
import web.rescue.erp.repository.RescuerProfileRepository;
import web.rescue.erp.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RescuerProfileRepository rescuerProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setPhone("0987654321");
        registerRequest.setPassword("rawPassword");
        registerRequest.setConfirmPassword("rawPassword");
        registerRequest.setFullName("User Test");
    }

    @Test
    void register_PhoneAlreadyExists_ShouldThrowException() {
        when(userRepository.existsByPhone(registerRequest.getPhone())).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.register(registerRequest);
        });

        assertEquals("Số điện thoại đã được đăng ký!", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_NewPhone_ShouldSaveUserAsCustomerAndActive() {
        when(userRepository.existsByPhone(registerRequest.getPhone())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("hashedPassword");
        
        User mockSavedUser = User.builder()
                .userId(UUID.randomUUID())
                .phone(registerRequest.getPhone())
                .passwordHash("hashedPassword")
                .fullName(registerRequest.getFullName())
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(mockSavedUser);

        User registered = userService.register(registerRequest);

        assertNotNull(registered);
        assertEquals(Role.CUSTOMER, registered.getRole());
        assertEquals(UserStatus.ACTIVE, registered.getStatus());
        assertEquals("hashedPassword", registered.getPasswordHash());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(Role.CUSTOMER, savedUser.getRole());
        assertEquals(UserStatus.ACTIVE, savedUser.getStatus());
    }

    @Test
    void registerRescuer_NewPhone_ShouldSaveUserAndCreateUnverifiedRescuerProfile() {
        when(userRepository.existsByPhone(registerRequest.getPhone())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("hashedPassword");
        
        UUID newUserId = UUID.randomUUID();
        User mockSavedUser = User.builder()
                .userId(newUserId)
                .phone(registerRequest.getPhone())
                .passwordHash("hashedPassword")
                .fullName(registerRequest.getFullName())
                .role(Role.RESCUER)
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(mockSavedUser);

        User registered = userService.registerRescuer(registerRequest);

        assertNotNull(registered);
        assertEquals(Role.RESCUER, registered.getRole());

        ArgumentCaptor<RescuerProfile> profileCaptor = ArgumentCaptor.forClass(RescuerProfile.class);
        verify(rescuerProfileRepository).save(profileCaptor.capture());
        RescuerProfile savedProfile = profileCaptor.getValue();
        
        assertNotNull(savedProfile);
        assertEquals(registered, savedProfile.getUser());
        assertFalse(savedProfile.isVerified());
    }

    @Test
    void updateProfile_UserNotFound_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        ProfileUpdateRequest updateReq = new ProfileUpdateRequest();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userService.updateProfile(userId, updateReq);
        });
    }

    @Test
    void updateProfile_UpdateNameAndEmail_ShouldSaveUser() {
        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .userId(userId)
                .fullName("Old Name")
                .email("old@demo.com")
                .build();

        ProfileUpdateRequest updateReq = new ProfileUpdateRequest();
        updateReq.setFullName("New Name");
        updateReq.setEmail("new@demo.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        userService.updateProfile(userId, updateReq);

        assertEquals("New Name", existingUser.getFullName());
        assertEquals("new@demo.com", existingUser.getEmail());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateProfile_ChangePassword_CorrectCurrentPassword_ShouldUpdatePassword() {
        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .userId(userId)
                .passwordHash("oldHashedPassword")
                .build();

        ProfileUpdateRequest updateReq = new ProfileUpdateRequest();
        updateReq.setCurrentPassword("oldPassword");
        updateReq.setNewPassword("newPassword");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("oldPassword", "oldHashedPassword")).thenReturn(true);
        when(passwordEncoder.encode("newPassword")).thenReturn("newHashedPassword");

        userService.updateProfile(userId, updateReq);

        assertEquals("newHashedPassword", existingUser.getPasswordHash());
        verify(userRepository).save(existingUser);
    }

    @Test
    void updateProfile_ChangePassword_IncorrectCurrentPassword_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        User existingUser = User.builder()
                .userId(userId)
                .passwordHash("oldHashedPassword")
                .build();

        ProfileUpdateRequest updateReq = new ProfileUpdateRequest();
        updateReq.setCurrentPassword("wrongPassword");
        updateReq.setNewPassword("newPassword");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("wrongPassword", "oldHashedPassword")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userService.updateProfile(userId, updateReq);
        });

        assertEquals("Mật khẩu hiện tại không đúng!", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateAvatar_ShouldUpdateAvatarPath() {
        UUID userId = UUID.randomUUID();
        User existingUser = User.builder().userId(userId).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        userService.updateAvatar(userId, "/uploads/avatars/test.jpg");

        assertEquals("/uploads/avatars/test.jpg", existingUser.getAvatar());
        verify(userRepository).save(existingUser);
    }

    @Test
    void toggleTwoFactor_ShouldToggleValue() {
        UUID userId = UUID.randomUUID();
        User existingUser = User.builder().userId(userId).twoFactorEnabled(false).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        userService.toggleTwoFactor(userId);
        assertTrue(existingUser.isTwoFactorEnabled());

        userService.toggleTwoFactor(userId);
        assertFalse(existingUser.isTwoFactorEnabled());

        verify(userRepository, times(2)).save(existingUser);
    }

    @Test
    void handleFailedLogin_BelowMaxAttempts_ShouldIncrementCounter() {
        String phone = "0987654321";
        User user = User.builder()
                .phone(phone)
                .failedLoginAttempts(2)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        userService.handleFailedLogin(phone);

        assertEquals(3, user.getFailedLoginAttempts());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void handleFailedLogin_ReachedMaxAttempts_ShouldLockAccount() {
        String phone = "0987654321";
        User user = User.builder()
                .phone(phone)
                .failedLoginAttempts(4)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        userService.handleFailedLogin(phone);

        assertEquals(5, user.getFailedLoginAttempts());
        assertEquals(UserStatus.LOCKED, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void resetFailedAttempts_ShouldSetToZero() {
        String phone = "0987654321";
        User user = User.builder()
                .phone(phone)
                .failedLoginAttempts(3)
                .build();

        when(userRepository.findByPhone(phone)).thenReturn(Optional.of(user));

        userService.resetFailedAttempts(phone);

        assertEquals(0, user.getFailedLoginAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void lockUser_ShouldSetStatusToLocked() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().userId(userId).status(UserStatus.ACTIVE).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.lockUser(userId);

        assertEquals(UserStatus.LOCKED, user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void unlockUser_ShouldSetStatusToActiveAndResetAttempts() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .userId(userId)
                .status(UserStatus.LOCKED)
                .failedLoginAttempts(5)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.unlockUser(userId);

        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(0, user.getFailedLoginAttempts());
        verify(userRepository).save(user);
    }
}
