package web.rescue.erp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.rescue.erp.entity.SystemSetting;
import web.rescue.erp.repository.SystemSettingRepository;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    public String getSettingValue(String key, String defaultValue) {
        return systemSettingRepository.findById(key)
                .map(SystemSetting::getSettingValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void saveSetting(String key, String value, String description) {
        SystemSetting setting = systemSettingRepository.findById(key)
                .orElseGet(() -> SystemSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        if (description != null) {
            setting.setDescription(description);
        }
        systemSettingRepository.save(setting);
    }

    @Transactional
    public void saveSetting(String key, String value) {
        saveSetting(key, value, null);
    }

    public BigDecimal getSystemFeePercent() {
        String val = getSettingValue("SYSTEM_FEE_PERCENT", "10.0");
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return new BigDecimal("10.0");
        }
    }

    public BigDecimal getPuncturePrice() {
        String val = getSettingValue("PRICE_PUNCTURE", "60000.00");
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return new BigDecimal("60000.00");
        }
    }

    public BigDecimal getBatteryPrice() {
        String val = getSettingValue("PRICE_BATTERY", "150000.00");
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return new BigDecimal("150000.00");
        }
    }

    public BigDecimal getChainPrice() {
        String val = getSettingValue("PRICE_CHAIN", "120000.00");
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return new BigDecimal("120000.00");
        }
    }

    public BigDecimal getBrakePrice() {
        String val = getSettingValue("PRICE_BRAKE", "90000.00");
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return new BigDecimal("90000.00");
        }
    }

    public BigDecimal getEnginePrice() {
        String val = getSettingValue("PRICE_ENGINE", "200000.00");
        try {
            return new BigDecimal(val);
        } catch (Exception e) {
            return new BigDecimal("200000.00");
        }
    }

    public String getAdminBankName() {
        return getSettingValue("ADMIN_BANK_NAME", "MBBank");
    }

    public String getAdminBankAccountNo() {
        return getSettingValue("ADMIN_BANK_ACCOUNT_NO", "0900000000");
    }

    public String getAdminBankAccountName() {
        return getSettingValue("ADMIN_BANK_ACCOUNT_NAME", "E RESCUE ADMIN");
    }
}
