package web.rescue.erp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiDiagnosticServiceTest {

    private AiDiagnosticService aiDiagnosticService;

    @Mock
    private SystemSettingService systemSettingService;

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingService.getPuncturePrice()).thenReturn(new BigDecimal("60000.00"));
        lenient().when(systemSettingService.getBatteryPrice()).thenReturn(new BigDecimal("150000.00"));
        lenient().when(systemSettingService.getChainPrice()).thenReturn(new BigDecimal("120000.00"));
        lenient().when(systemSettingService.getBrakePrice()).thenReturn(new BigDecimal("90000.00"));
        lenient().when(systemSettingService.getEnginePrice()).thenReturn(new BigDecimal("200000.00"));

        aiDiagnosticService = new AiDiagnosticService(systemSettingService);
    }

    @Test
    void diagnose_EmptyInput_ShouldReturnDefaultEmptyResult() {
        AiDiagnosticService.DiagnosticResult resultNull = aiDiagnosticService.diagnose(null);
        assertEquals("Không xác định", resultNull.issueName());
        assertEquals(BigDecimal.ZERO, resultNull.estimatedBasePrice());

        AiDiagnosticService.DiagnosticResult resultEmpty = aiDiagnosticService.diagnose("   ");
        assertEquals("Không xác định", resultEmpty.issueName());
        assertEquals(BigDecimal.ZERO, resultEmpty.estimatedBasePrice());
    }

    @Test
    void diagnose_TireIssues_ShouldDiagnoseTirePuncture() {
        AiDiagnosticService.DiagnosticResult result = aiDiagnosticService.diagnose("Xe tôi bị thủng lốp sau ở đường Lê Lợi");
        assertEquals("Thủng săm/lốp xe", result.issueName());
        assertEquals("Trung bình", result.severity());
        assertEquals(new BigDecimal("60000.00"), result.estimatedBasePrice());
    }

    @Test
    void diagnose_BatteryIssues_ShouldDiagnoseBatteryFailure() {
        AiDiagnosticService.DiagnosticResult result = aiDiagnosticService.diagnose("Hết bình ắc quy không đề được máy");
        assertEquals("Ắc quy yếu / Chết máy không khởi động được", result.issueName());
        assertEquals("Trung bình", result.severity());
        assertEquals(new BigDecimal("150000.00"), result.estimatedBasePrice());
    }

    @Test
    void diagnose_ChainIssues_ShouldDiagnoseChainIncident() {
        AiDiagnosticService.DiagnosticResult result = aiDiagnosticService.diagnose("Xe em bị đứt xích rồi");
        assertEquals("Sự cố hệ thống xích/sên truyền động", result.issueName());
        assertEquals("Cao", result.severity());
        assertEquals(new BigDecimal("120000.00"), result.estimatedBasePrice());
    }

    @Test
    void diagnose_BrakeIssues_ShouldDiagnoseBrakeFailure() {
        AiDiagnosticService.DiagnosticResult result = aiDiagnosticService.diagnose("Bị hỏng phanh xe trước nguy hiểm quá");
        assertEquals("Hỏng hệ thống phanh/thắng xe", result.issueName());
        assertEquals("Cao", result.severity());
        assertEquals(new BigDecimal("90000.00"), result.estimatedBasePrice());
    }

    @Test
    void diagnose_UnknownIssue_ShouldReturnEngineFallback() {
        AiDiagnosticService.DiagnosticResult result = aiDiagnosticService.diagnose("Xe bốc khói nghi ngút ở phần máy");
        assertEquals("Sự cố kỹ thuật động cơ tổng quát", result.issueName());
        assertEquals("Cao", result.severity());
        assertEquals(new BigDecimal("200000.00"), result.estimatedBasePrice());
    }
}
