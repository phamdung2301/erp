package web.rescue.erp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.Role;
import web.rescue.erp.entity.enums.UserStatus;
import web.rescue.erp.repository.UserRepository;

import org.springframework.context.annotation.Profile;

import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.repository.RescuerProfileRepository;
import java.math.BigDecimal;
import org.springframework.context.annotation.Profile;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RescuerProfileRepository rescuerProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Seed Admin account nếu chưa có
        if (userRepository.findByPhone("0900000000").isEmpty()) {
            User admin = User.builder()
                    .phone("0900000000")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .fullName("System Admin")
                    .email("admin@erescue.vn")
                    .role(Role.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(admin);
            log.info("✅ Admin account seeded: phone=0900000000, password=admin123");
        }

        // Seed demo Customer
        if (userRepository.findByPhone("0901111111").isEmpty()) {
            User customer = User.builder()
                    .phone("0901111111")
                    .passwordHash(passwordEncoder.encode("123456"))
                    .fullName("Nguyễn Văn Khách")
                    .email("customer@demo.vn")
                    .role(Role.CUSTOMER)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(customer);
            log.info("✅ Demo Customer seeded: phone=0901111111, password=123456");
        }

        // Seed demo Rescuer 1 (Trần Văn Thợ)
        if (userRepository.findByPhone("0902222222").isEmpty()) {
            User rescuerUser = User.builder()
                    .phone("0902222222")
                    .passwordHash(passwordEncoder.encode("123456"))
                    .fullName("Trần Văn Thợ")
                    .email("rescuer1@demo.vn")
                    .role(Role.RESCUER)
                    .status(UserStatus.ACTIVE)
                    .build();
            rescuerUser = userRepository.save(rescuerUser);

            RescuerProfile profile = RescuerProfile.builder()
                    .user(rescuerUser)
                    .verified(true)
                    .isOnline(true)
                    .currentLat(new BigDecimal("21.0305"))
                    .currentLng(new BigDecimal("105.8522"))
                    .walletBalance(new BigDecimal("500000.00"))
                    .specialty("Sửa săm lốp, ắc quy lưu động")
                    .serviceArea("Hoàn Kiếm, Ba Đình")
                    .build();
            rescuerProfileRepository.save(profile);
            log.info("✅ Demo Rescuer 1 (Trần Văn Thợ) seeded: phone=0902222222, verified, online");
        }

        // Seed demo Rescuer 2 (Nguyễn Văn Sửa)
        if (userRepository.findByPhone("0903333333").isEmpty()) {
            User rescuerUser2 = User.builder()
                    .phone("0903333333")
                    .passwordHash(passwordEncoder.encode("123456"))
                    .fullName("Nguyễn Văn Sửa")
                    .email("rescuer2@demo.vn")
                    .role(Role.RESCUER)
                    .status(UserStatus.ACTIVE)
                    .build();
            rescuerUser2 = userRepository.save(rescuerUser2);

            RescuerProfile profile2 = RescuerProfile.builder()
                    .user(rescuerUser2)
                    .verified(true)
                    .isOnline(true)
                    .currentLat(new BigDecimal("21.0260"))
                    .currentLng(new BigDecimal("105.8590"))
                    .walletBalance(new BigDecimal("350000.00"))
                    .specialty("Cứu hộ chết máy, xích xe máy")
                    .serviceArea("Hai Bà Trưng, Hoàn Kiếm")
                    .build();
            rescuerProfileRepository.save(profile2);
            log.info("✅ Demo Rescuer 2 (Nguyễn Văn Sửa) seeded: phone=0903333333, verified, online");
        }

        log.info("📊 Total users in database: {}", userRepository.count());
    }
}
