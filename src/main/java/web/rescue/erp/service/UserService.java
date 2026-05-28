package web.rescue.erp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.rescue.erp.dto.ProfileUpdateRequest;
import web.rescue.erp.dto.RegisterRequest;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.enums.Role;
import web.rescue.erp.entity.enums.UserStatus;
import web.rescue.erp.repository.UserRepository;
import web.rescue.erp.repository.RescuerProfileRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RescuerProfileRepository rescuerProfileRepository;
    private final PasswordEncoder passwordEncoder;

    private static final int MAX_FAILED_ATTEMPTS = 5;

    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Số điện thoại đã được đăng ký!");
        }

        User user = User.builder()
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User registerRescuer(RegisterRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Số điện thoại đã được đăng ký!");
        }

        User user = User.builder()
                .phone(request.getPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(Role.RESCUER)
                .status(UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);

        // Create rescuer profile
        RescuerProfile profile = RescuerProfile.builder()
                .user(user)
                .verified(false)
                .build();
        rescuerProfileRepository.save(profile);

        return user;
    }

    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }

    public Optional<User> findById(UUID userId) {
        return userRepository.findById(userId);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public List<User> findByRole(Role role) {
        return userRepository.findByRole(role);
    }

    @Transactional
    public void updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmail(request.getEmail());
        }
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null ||
                !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new RuntimeException("Mật khẩu hiện tại không đúng!");
            }
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        }

        userRepository.save(user);
    }

    @Transactional
    public void updateAvatar(UUID userId, String avatarPath) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        user.setAvatar(avatarPath);
        userRepository.save(user);
    }

    @Transactional
    public void toggleTwoFactor(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        user.setTwoFactorEnabled(!user.isTwoFactorEnabled());
        userRepository.save(user);
    }

    /**
     * Xử lý đăng nhập thất bại: tăng counter, khóa nếu >= 5 lần
     */
    @Transactional
    public void handleFailedLogin(String phone) {
        userRepository.findByPhone(phone).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setStatus(UserStatus.LOCKED);
                log.warn("Account locked for phone: {} after {} failed attempts", phone, attempts);
            }
            userRepository.save(user);
        });
    }

    /**
     * Reset failed attempts sau khi đăng nhập thành công
     */
    @Transactional
    public void resetFailedAttempts(String phone) {
        userRepository.findByPhone(phone).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        });
    }

    @Transactional
    public void lockUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        user.setStatus(UserStatus.LOCKED);
        userRepository.save(user);
    }

    @Transactional
    public void unlockUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
    }

    // --- Dashboard Stats ---
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    public long countByStatus(UserStatus status) {
        return userRepository.countByStatus(status);
    }

    public long countTotal() {
        return userRepository.count();
    }
}
