package web.rescue.erp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.rescue.erp.entity.PayoutRequest;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.PayoutStatus;
import web.rescue.erp.repository.PayoutRequestRepository;
import web.rescue.erp.repository.RescuerProfileRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @Mock
    private RescuerProfileRepository rescuerProfileRepository;

    @InjectMocks
    private PayoutService payoutService;

    @Test
    void createPayoutRequest_Valid_ShouldDeductBalanceAndSaveRequest() {
        UUID userId = UUID.randomUUID();
        User rescuer = User.builder().userId(userId).build();
        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(userId)
                .walletBalance(new BigDecimal("100000.00"))
                .build();

        when(rescuerProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutRequest result = payoutService.createPayoutRequest(rescuer, new BigDecimal("40000.00"));

        assertNotNull(result);
        assertEquals(new BigDecimal("60000.00"), profile.getWalletBalance());
        assertEquals(PayoutStatus.PENDING, result.getStatus());
        assertEquals(rescuer, result.getRescuer());
        verify(rescuerProfileRepository, times(1)).save(profile);
        verify(payoutRequestRepository, times(1)).save(any(PayoutRequest.class));
    }

    @Test
    void createPayoutRequest_WithBankDetails_ShouldSaveBankDetailsInProfileAndRequest() {
        UUID userId = UUID.randomUUID();
        User rescuer = User.builder().userId(userId).build();
        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(userId)
                .walletBalance(new BigDecimal("100000.00"))
                .build();

        when(rescuerProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutRequest result = payoutService.createPayoutRequest(
                rescuer, 
                new BigDecimal("40000.00"), 
                "MBBank", 
                "987654321", 
                "NGUYEN VAN THO"
        );

        assertNotNull(result);
        assertEquals(new BigDecimal("60000.00"), profile.getWalletBalance());
        assertEquals("MBBank", profile.getBankName());
        assertEquals("987654321", profile.getBankAccountNo());
        assertEquals("NGUYEN VAN THO", profile.getBankAccountName());
        assertEquals("MBBank", result.getBankName());
        assertEquals("987654321", result.getBankAccountNo());
        assertEquals("NGUYEN VAN THO", result.getBankAccountName());
        verify(rescuerProfileRepository, times(1)).save(profile);
        verify(payoutRequestRepository, times(1)).save(any(PayoutRequest.class));
    }

    @Test
    void createPayoutRequest_ZeroOrNegativeAmount_ShouldThrowException() {
        User rescuer = new User();
        assertThrows(RuntimeException.class, () -> payoutService.createPayoutRequest(rescuer, BigDecimal.ZERO));
        assertThrows(RuntimeException.class, () -> payoutService.createPayoutRequest(rescuer, new BigDecimal("-10")));
        verifyNoInteractions(rescuerProfileRepository, payoutRequestRepository);
    }

    @Test
    void createPayoutRequest_InsufficientBalance_ShouldThrowException() {
        UUID userId = UUID.randomUUID();
        User rescuer = User.builder().userId(userId).build();
        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(userId)
                .walletBalance(new BigDecimal("1000.00"))
                .build();

        when(rescuerProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        assertThrows(RuntimeException.class, () -> payoutService.createPayoutRequest(rescuer, new BigDecimal("2000.00")));
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void approvePayout_Pending_ShouldChangeStatusToApproved() {
        UUID payoutId = UUID.randomUUID();
        PayoutRequest request = PayoutRequest.builder()
                .payoutId(payoutId)
                .status(PayoutStatus.PENDING)
                .amount(new BigDecimal("5000.00"))
                .build();

        when(payoutRequestRepository.findById(payoutId)).thenReturn(Optional.of(request));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutRequest result = payoutService.approvePayout(payoutId);

        assertEquals(PayoutStatus.APPROVED, result.getStatus());
        assertNotNull(result.getProcessedAt());
        verify(payoutRequestRepository, times(1)).save(request);
    }

    @Test
    void approvePayout_NotPending_ShouldThrowException() {
        UUID payoutId = UUID.randomUUID();
        PayoutRequest request = PayoutRequest.builder()
                .payoutId(payoutId)
                .status(PayoutStatus.APPROVED)
                .build();

        when(payoutRequestRepository.findById(payoutId)).thenReturn(Optional.of(request));

        assertThrows(RuntimeException.class, () -> payoutService.approvePayout(payoutId));
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void rejectPayout_Pending_ShouldRefundAndChangeStatusToRejected() {
        UUID payoutId = UUID.randomUUID();
        UUID rescuerId = UUID.randomUUID();
        User rescuer = User.builder().userId(rescuerId).build();
        PayoutRequest request = PayoutRequest.builder()
                .payoutId(payoutId)
                .rescuer(rescuer)
                .status(PayoutStatus.PENDING)
                .amount(new BigDecimal("40000.00"))
                .build();

        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuerId)
                .walletBalance(new BigDecimal("10000.00"))
                .build();

        when(payoutRequestRepository.findById(payoutId)).thenReturn(Optional.of(request));
        when(rescuerProfileRepository.findById(rescuerId)).thenReturn(Optional.of(profile));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutRequest result = payoutService.rejectPayout(payoutId);

        assertEquals(PayoutStatus.REJECTED, result.getStatus());
        assertNotNull(result.getProcessedAt());
        assertEquals(new BigDecimal("50000.00"), profile.getWalletBalance());
        verify(rescuerProfileRepository, times(1)).save(profile);
        verify(payoutRequestRepository, times(1)).save(request);
    }

    @Test
    void findAll_ShouldReturnAll() {
        PayoutRequest request = new PayoutRequest();
        when(payoutRequestRepository.findAll()).thenReturn(Collections.singletonList(request));
        List<PayoutRequest> result = payoutService.findAll();
        assertEquals(1, result.size());
    }

    @Test
    void findByStatus_ShouldReturnList() {
        PayoutRequest request = new PayoutRequest();
        when(payoutRequestRepository.findByStatusOrderByCreatedAtDesc(PayoutStatus.PENDING))
                .thenReturn(Collections.singletonList(request));
        List<PayoutRequest> result = payoutService.findByStatus(PayoutStatus.PENDING);
        assertEquals(1, result.size());
    }

    @Test
    void getPendingCount_ShouldReturnCount() {
        when(payoutRequestRepository.countByStatus(PayoutStatus.PENDING)).thenReturn(4L);
        assertEquals(4L, payoutService.getPendingCount());
    }
}
