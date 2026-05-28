package web.rescue.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    private String phone;
    private String password;
    private String confirmPassword;
    private String fullName;
    private String otp;
}
