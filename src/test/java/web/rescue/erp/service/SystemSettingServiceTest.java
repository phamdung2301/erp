package web.rescue.erp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.rescue.erp.entity.SystemSetting;
import web.rescue.erp.repository.SystemSettingRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;

    @InjectMocks
    private SystemSettingService systemSettingService;

    @Test
    void getSettingValue_KeyExists_ShouldReturnValue() {
        String key = "TEST_KEY";
        SystemSetting setting = SystemSetting.builder()
                .settingKey(key)
                .settingValue("Hello")
                .build();

        when(systemSettingRepository.findById(key)).thenReturn(Optional.of(setting));

        String value = systemSettingService.getSettingValue(key, "Default");

        assertEquals("Hello", value);
    }

    @Test
    void getSettingValue_KeyDoesNotExist_ShouldReturnDefaultValue() {
        String key = "MISSING_KEY";
        when(systemSettingRepository.findById(key)).thenReturn(Optional.empty());

        String value = systemSettingService.getSettingValue(key, "Default");

        assertEquals("Default", value);
    }

    @Test
    void saveSetting_NewKey_ShouldInsert() {
        String key = "NEW_KEY";
        String value = "123";
        String desc = "New Description";

        when(systemSettingRepository.findById(key)).thenReturn(Optional.empty());

        systemSettingService.saveSetting(key, value, desc);

        verify(systemSettingRepository, times(1)).save(argThat(s ->
                s.getSettingKey().equals(key) &&
                s.getSettingValue().equals(value) &&
                s.getDescription().equals(desc)
        ));
    }

    @Test
    void saveSetting_ExistingKey_ShouldUpdate() {
        String key = "EXISTING_KEY";
        SystemSetting existing = SystemSetting.builder()
                .settingKey(key)
                .settingValue("old")
                .description("old desc")
                .build();

        when(systemSettingRepository.findById(key)).thenReturn(Optional.of(existing));

        systemSettingService.saveSetting(key, "new");

        assertEquals("new", existing.getSettingValue());
        verify(systemSettingRepository, times(1)).save(existing);
    }

    @Test
    void getSystemFeePercent_Valid_ShouldReturnParsedValue() {
        SystemSetting setting = SystemSetting.builder()
                .settingKey("SYSTEM_FEE_PERCENT")
                .settingValue("15.5")
                .build();
        when(systemSettingRepository.findById("SYSTEM_FEE_PERCENT")).thenReturn(Optional.of(setting));

        BigDecimal fee = systemSettingService.getSystemFeePercent();

        assertEquals(new BigDecimal("15.5"), fee);
    }

    @Test
    void getSystemFeePercent_InvalidOrEmpty_ShouldReturnDefaultTen() {
        when(systemSettingRepository.findById("SYSTEM_FEE_PERCENT")).thenReturn(Optional.empty());
        assertEquals(new BigDecimal("10.0"), systemSettingService.getSystemFeePercent());

        SystemSetting setting = SystemSetting.builder()
                .settingKey("SYSTEM_FEE_PERCENT")
                .settingValue("NOT_A_NUMBER")
                .build();
        when(systemSettingRepository.findById("SYSTEM_FEE_PERCENT")).thenReturn(Optional.of(setting));
        assertEquals(new BigDecimal("10.0"), systemSettingService.getSystemFeePercent());
    }

    @Test
    void getPrices_ShouldReturnConfiguredOrDefaults() {
        // Test fallbacks
        when(systemSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        assertEquals(new BigDecimal("60000.00"), systemSettingService.getPuncturePrice());
        assertEquals(new BigDecimal("150000.00"), systemSettingService.getBatteryPrice());
        assertEquals(new BigDecimal("120000.00"), systemSettingService.getChainPrice());
        assertEquals(new BigDecimal("90000.00"), systemSettingService.getBrakePrice());
        assertEquals(new BigDecimal("200000.00"), systemSettingService.getEnginePrice());

        // Test configuration exists
        SystemSetting setting = SystemSetting.builder()
                .settingKey("PRICE_PUNCTURE")
                .settingValue("75000.00")
                .build();
        reset(systemSettingRepository);
        when(systemSettingRepository.findById("PRICE_PUNCTURE")).thenReturn(Optional.of(setting));
        assertEquals(new BigDecimal("75000.00"), systemSettingService.getPuncturePrice());
    }
}
