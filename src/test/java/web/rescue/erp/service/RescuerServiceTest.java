package web.rescue.erp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.repository.RescuerProfileRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import web.rescue.erp.websocket.WebSocketNotificationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescuerServiceTest {

    @Mock
    private RescuerProfileRepository rescuerProfileRepository;

    @Mock
    private PayoutService payoutService;

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    @InjectMocks
    private RescuerService rescuerService;

    @Test
    void findPendingVerification_ShouldReturnProfilesFromRepository() {
        RescuerProfile mockProfile = new RescuerProfile();
        when(rescuerProfileRepository.findByVerifiedFalse()).thenReturn(Collections.singletonList(mockProfile));

        List<RescuerProfile> result = rescuerService.findPendingVerification();

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(rescuerProfileRepository, times(1)).findByVerifiedFalse();
    }

    @Test
    void verifyRescuer_ProfileExists_ShouldSetVerifiedTrue() {
        UUID rescuerId = UUID.randomUUID();
        RescuerProfile profile = RescuerProfile.builder()
                .verified(false)
                .build();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        rescuerService.verifyRescuer(rescuerId);

        assertTrue(profile.isVerified());
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void rejectRescuer_ProfileExists_ShouldSetVerifiedFalse() {
        UUID rescuerId = UUID.randomUUID();
        RescuerProfile profile = RescuerProfile.builder()
                .verified(true)
                .build();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        rescuerService.rejectRescuer(rescuerId);

        assertFalse(profile.isVerified());
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void updateDocuments_ShouldUpdateUrlsAndResetVerifiedToFalse() {
        UUID rescuerId = UUID.randomUUID();
        RescuerProfile profile = RescuerProfile.builder()
                .verified(true)
                .licenseUrl("old_license.png")
                .idCardUrl("old_id.png")
                .build();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        rescuerService.updateDocuments(rescuerId, "new_license.png", "new_id.png");

        assertEquals("new_license.png", profile.getLicenseUrl());
        assertEquals("new_id.png", profile.getIdCardUrl());
        assertFalse(profile.isVerified());
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void countPendingVerification_ShouldReturnRepositoryCount() {
        when(rescuerProfileRepository.countByVerifiedFalse()).thenReturn(5L);
        assertEquals(5L, rescuerService.countPendingVerification());
    }

    @Test
    void countOnline_ShouldReturnRepositoryCount() {
        when(rescuerProfileRepository.countByIsOnlineTrue()).thenReturn(10L);
        assertEquals(10L, rescuerService.countOnline());
    }

    @Test
    void toggleOnline_Verified_ShouldSetOnline() {
        UUID rescuerId = UUID.randomUUID();
        RescuerProfile profile = RescuerProfile.builder()
                .verified(true)
                .isOnline(false)
                .build();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        rescuerService.toggleOnline(rescuerId, true);

        assertTrue(profile.isOnline());
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void toggleOnline_NotVerified_ShouldThrowException() {
        UUID rescuerId = UUID.randomUUID();
        RescuerProfile profile = RescuerProfile.builder()
                .verified(false)
                .isOnline(false)
                .build();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        assertThrows(RuntimeException.class, () -> {
            rescuerService.toggleOnline(rescuerId, true);
        });
        verify(rescuerProfileRepository, never()).save(any());
    }

    @Test
    void updateLocation_ShouldSetLatAndLng() {
        UUID rescuerId = UUID.randomUUID();
        RescuerProfile profile = new RescuerProfile();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        BigDecimal lat = new BigDecimal("21.0305");
        BigDecimal lng = new BigDecimal("105.8522");
        rescuerService.updateLocation(rescuerId, lat, lng);

        assertEquals(lat, profile.getCurrentLat());
        assertEquals(lng, profile.getCurrentLng());
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void payoutRequest_ShouldDelegateToPayoutService() {
        UUID rescuerId = UUID.randomUUID();
        User user = User.builder().userId(rescuerId).build();
        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuerId)
                .user(user)
                .build();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        BigDecimal amount = new BigDecimal("40000.00");
        rescuerService.payoutRequest(rescuerId, amount);

        verify(payoutService, times(1)).createPayoutRequest(user, amount);
    }

    @Test
    void payoutRequest_WithBankDetails_ShouldDelegateToPayoutService() {
        UUID rescuerId = UUID.randomUUID();
        User user = User.builder().userId(rescuerId).build();
        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuerId)
                .user(user)
                .build();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        BigDecimal amount = new BigDecimal("40000.00");
        rescuerService.payoutRequest(rescuerId, amount, "MBBank", "987654321", "NGUYEN VAN THO");

        verify(payoutService, times(1)).createPayoutRequest(user, amount, "MBBank", "987654321", "NGUYEN VAN THO");
    }

    @Test
    void sendPaymentReminder_ShouldSendNotification() {
        UUID rescuerId = UUID.randomUUID();
        User user = User.builder().userId(rescuerId).phone("0987654321").build();
        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuerId)
                .user(user)
                .cashDebt(new BigDecimal("150000.00"))
                .build();

        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));

        rescuerService.sendPaymentReminder(rescuerId);

        verify(webSocketNotificationService, times(1)).notifyPaymentReminder(
                eq("0987654321"),
                contains("150.000")
        );
    }
}

