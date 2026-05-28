package web.rescue.erp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AiDiagnosticService {

    private final SystemSettingService systemSettingService;

    public record DiagnosticResult(
            String issueName,
            String severity,
            String recommendation,
            BigDecimal estimatedBasePrice
    ) {}

    public DiagnosticResult diagnose(String issueDesc) {
        if (issueDesc == null || issueDesc.trim().isEmpty()) {
            return new DiagnosticResult(
                    "Không xác định",
                    "Thấp",
                    "Vui lòng nhập mô tả sự cố để AI chẩn đoán.",
                    BigDecimal.ZERO
            );
        }

        String descLower = issueDesc.toLowerCase();

        // 1. Thủng săm lốp (Vá vỏ, xì hơi, bể bánh)
        if (containsAny(descLower, "lốp", "săm", "bánh", "xẹp", "thủng", "đinh", "xì hơi", "non hơi", "hết hơi", "cán đinh", "bể bánh", "bể vỏ", "vá vỏ", "ruột xe", "nổ lốp", "xẹp lốp", "xì lốp", "bể lốp")) {
            return new DiagnosticResult(
                    "Thủng săm/lốp xe",
                    "Trung bình",
                    "Vui lòng dừng xe sát lề đường bên phải, bật đèn khẩn cấp. Thợ cứu hộ lưu động sẽ đến vá săm/lốp hoặc thay thế dự phòng.",
                    systemSettingService.getPuncturePrice()
            );
        }

        // 2. Lỗi ắc quy/khởi động (Hết bình, không đề được, chết máy)
        if (containsAny(descLower, "ắc quy", "bình", "đề", "điện", "không lên", "hết điện", "chết máy", "hết bình", "yếu bình", "không đề được", "không có điện", "mất điện", "chập điện", "đứt cầu chì", "hỏng bugi", "ngập nước", "ngộp xăng")) {
            return new DiagnosticResult(
                    "Ắc quy yếu / Chết máy không khởi động được",
                    "Trung bình",
                    "Tắt bớt các thiết bị tiêu thụ điện. Thợ cứu hộ sẽ đến kiểm tra, câu bình kích nổ hoặc hỗ trợ thay ắc quy mới.",
                    systemSettingService.getBatteryPrice()
            );
        }

        // 3. Đứt xích (Tuột xích, kẹt sên, giãn sên)
        if (containsAny(descLower, "xích", "đứt xích", "truyền động", "sên", "tuột xích", "tuột sên", "kẹt xích", "kẹt sên", "giãn xích", "giãn sên", "đứt sên", "xích kêu")) {
            return new DiagnosticResult(
                    "Sự cố hệ thống xích/sên truyền động",
                    "Cao",
                    "Tránh dắt xe quá mạnh gây kẹt đĩa xích. Thợ cứu hộ sẽ hỗ trợ cắt xích, nối xích hoặc thay thế bộ sên xích mới tại chỗ.",
                    systemSettingService.getChainPrice()
            );
        }

        // 4. Hỏng phanh (Mất thắng, kẹt thắng, mòn má phanh)
        if (containsAny(descLower, "phanh", "thắng", "bó phanh", "đứt dây phanh", "mất thắng", "kẹt thắng", "bó thắng", "phanh không ăn", "thắng không ăn", "mòn má phanh", "đứt dây thắng")) {
            return new DiagnosticResult(
                    "Hỏng hệ thống phanh/thắng xe",
                    "Cao",
                    "Rất nguy hiểm nếu tiếp tục lưu thông. Vui lòng dừng xe hoàn toàn. Thợ sẽ đến kiểm tra má phanh, dây phanh hoặc dầu phanh.",
                    systemSettingService.getBrakePrice()
            );
        }

        // 5. Mặc định động cơ/khác
        return new DiagnosticResult(
                "Sự cố kỹ thuật động cơ tổng quát",
                "Cao",
                "Đỗ xe nơi an toàn. Hệ thống AI đề xuất điều phối thợ cứu hộ chuyên sâu đến kiểm tra bugi, xăng gió hoặc hệ thống cơ khí động cơ.",
                systemSettingService.getEnginePrice()
        );
    }

    private boolean containsAny(String source, String... keywords) {
        for (String kw : keywords) {
            if (source.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
