package web.rescue.erp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.rescue.erp.entity.Invoice;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.RescuerProfile;
import web.rescue.erp.entity.RescuerInventory;
import web.rescue.erp.entity.User;
import web.rescue.erp.entity.enums.PaymentMethod;
import web.rescue.erp.entity.enums.RequestStatus;
import web.rescue.erp.repository.InvoiceRepository;
import web.rescue.erp.repository.RescueRequestRepository;
import web.rescue.erp.repository.RescuerProfileRepository;
import web.rescue.erp.repository.RescuerInventoryRepository;
import web.rescue.erp.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RescueRequestServiceTest {

    @Mock
    private RescueRequestRepository rescueRequestRepository;

    @Mock
    private RescuerProfileRepository rescuerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private RescuerInventoryRepository rescuerInventoryRepository;

    @Mock
    private AiDiagnosticService aiDiagnosticService;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private web.rescue.erp.websocket.WebSocketNotificationService webSocketNotificationService;

    @InjectMocks
    private RescueRequestService rescueRequestService;

    private User customer;
    private User rescuer;
    private RescueRequest request;

    @BeforeEach
    void setUp() {
        lenient().when(systemSettingService.getSystemFeePercent()).thenReturn(new BigDecimal("10.0"));

        customer = User.builder()
                .userId(UUID.randomUUID())
                .phone("0901111111")
                .fullName("Customer Demo")
                .build();

        rescuer = User.builder()
                .userId(UUID.randomUUID())
                .phone("0902222222")
                .fullName("Rescuer Demo")
                .build();

        request = RescueRequest.builder()
                .requestId(UUID.randomUUID())
                .customer(customer)
                .status(RequestStatus.PENDING)
                .basePrice(new BigDecimal("100000.00"))
                .extraPrice(BigDecimal.ZERO)
                .otpCode("123456")
                .build();
    }

    @Test
    void createRequest_ShouldCallAiAndSaveRequest() {
        String issue = "thủng lốp xe";
        BigDecimal lat = new BigDecimal("21.0285");
        BigDecimal lng = new BigDecimal("105.8542");
        String address = "Hoàn Kiếm, Hà Nội";

        AiDiagnosticService.DiagnosticResult diagResult = new AiDiagnosticService.DiagnosticResult(
                "Thủng lốp", "Trung bình", "Khuyến nghị vá lốp", new BigDecimal("60000.00")
        );
        when(aiDiagnosticService.diagnose(issue)).thenReturn(diagResult);
        when(rescueRequestRepository.save(any(RescueRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RescueRequest saved = rescueRequestService.createRequest(customer, issue, lat, lng, address);

        assertNotNull(saved);
        assertEquals(customer, saved.getCustomer());
        assertEquals(RequestStatus.PENDING, saved.getStatus());
        assertEquals(new BigDecimal("60000.00"), saved.getBasePrice());
        assertEquals(lat, saved.getCustomerLat());
        assertEquals(lng, saved.getCustomerLng());
        assertEquals(address, saved.getCustomerAddress());
        assertNotNull(saved.getOtpCode());
        assertEquals(6, saved.getOtpCode().length());
        
        verify(rescueRequestRepository, times(1)).save(any(RescueRequest.class));
    }

    @Test
    void getActiveRequestForCustomer_ShouldReturnActiveRequests() {
        UUID customerId = customer.getUserId();
        
        RescueRequest reqCompleted = RescueRequest.builder().status(RequestStatus.COMPLETED).build();
        RescueRequest reqPending = RescueRequest.builder().status(RequestStatus.PENDING).build();
        
        when(rescueRequestRepository.findByCustomerUserIdOrderByCreatedAtDesc(customerId))
                .thenReturn(List.of(reqCompleted, reqPending));

        Optional<RescueRequest> active = rescueRequestService.getActiveRequestForCustomer(customerId);

        assertTrue(active.isPresent());
        assertEquals(RequestStatus.PENDING, active.get().getStatus());
    }

    @Test
    void cancelRequest_ShouldSetStatusToCanceled() {
        UUID requestId = request.getRequestId();
        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        rescueRequestService.cancelRequest(requestId);

        assertEquals(RequestStatus.CANCELED, request.getStatus());
        verify(rescueRequestRepository, times(1)).save(request);
    }

    @Test
    void cancelRequest_AlreadyFinished_ShouldThrowException() {
        request.setStatus(RequestStatus.COMPLETED);
        UUID requestId = request.getRequestId();
        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThrows(RuntimeException.class, () -> {
            rescueRequestService.cancelRequest(requestId);
        });
        verify(rescueRequestRepository, never()).save(any());
    }

    @Test
    void simulateRescuerAction_Accept_ShouldAssignRescuerAndSetStatus() {
        UUID requestId = request.getRequestId();
        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(userRepository.findByPhone("0902222222")).thenReturn(Optional.of(rescuer));

        rescueRequestService.simulateRescuerAction(requestId, "ACCEPT");

        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertEquals(rescuer, request.getRescuer());
        verify(rescueRequestRepository, times(1)).save(request);
    }

    @Test
    void simulateRescuerAction_Complete_ShouldSetStatusAndGenerateInvoice() {
        request.setRescuer(rescuer);
        request.setStatus(RequestStatus.IN_PROGRESS);
        UUID requestId = request.getRequestId();

        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuer.getUserId())
                .walletBalance(new BigDecimal("50000.00"))
                .build();

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rescuerProfileRepository.findById(rescuer.getUserId())).thenReturn(Optional.of(profile));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        rescueRequestService.simulateRescuerAction(requestId, "COMPLETE");

        assertEquals(RequestStatus.COMPLETED, request.getStatus());
        assertNotNull(request.getInvoice());
        assertEquals(new BigDecimal("100000.00"), request.getInvoice().getTotalAmount());
        assertEquals(new BigDecimal("10000.00"), request.getInvoice().getSystemFee());
        assertTrue(request.getInvoice().isPaid());
        assertEquals(new BigDecimal("140000.00"), profile.getWalletBalance());
        verify(rescueRequestRepository, times(1)).save(request);
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void payAndRateRequest_ShouldPayInvoiceAndCreditRescuer() {
        UUID requestId = request.getRequestId();
        request.setRescuer(rescuer);
        request.setStatus(RequestStatus.COMPLETED);

        Invoice invoice = Invoice.builder()
                .totalAmount(new BigDecimal("100000.00"))
                .systemFee(new BigDecimal("10000.00"))
                .paid(false)
                .build();
        request.setInvoice(invoice);

        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuer.getUserId())
                .walletBalance(new BigDecimal("50000.00"))
                .build();

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rescuerProfileRepository.findById(rescuer.getUserId())).thenReturn(Optional.of(profile));

        rescueRequestService.payAndRateRequest(requestId, "WALLET", 5, "Rất tốt");

        assertTrue(invoice.isPaid());
        assertEquals(PaymentMethod.WALLET, invoice.getPaymentMethod());
        assertEquals(new BigDecimal("140000.00"), profile.getWalletBalance()); // 50000 + (100000 - 10000)
        
        verify(invoiceRepository, times(1)).save(invoice);
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void getNearbyPendingRequests_ShouldReturnFilteredRequests() {
        BigDecimal lat = new BigDecimal("21.0305");
        BigDecimal lng = new BigDecimal("105.8522");

        RescueRequest requestNearby = RescueRequest.builder()
                .status(RequestStatus.PENDING)
                .customerLat(new BigDecimal("21.0310"))
                .customerLng(new BigDecimal("105.8520"))
                .build();

        RescueRequest requestFar = RescueRequest.builder()
                .status(RequestStatus.PENDING)
                .customerLat(new BigDecimal("22.0305")) // far away
                .customerLng(new BigDecimal("105.8522"))
                .build();

        when(rescueRequestRepository.findByStatus(RequestStatus.PENDING))
                .thenReturn(List.of(requestNearby, requestFar));

        List<RescueRequest> result = rescueRequestService.getNearbyPendingRequests(lat, lng);

        assertEquals(1, result.size());
        assertEquals(requestNearby.getCustomerLat(), result.get(0).getCustomerLat());
    }

    @Test
    void getActiveRequestForRescuer_ShouldReturnActiveRequest() {
        UUID rescuerId = rescuer.getUserId();
        RescueRequest active = RescueRequest.builder().status(RequestStatus.ACCEPTED).build();
        RescueRequest completed = RescueRequest.builder().status(RequestStatus.COMPLETED).build();

        when(rescueRequestRepository.findByRescuerUserIdOrderByCreatedAtDesc(rescuerId))
                .thenReturn(List.of(completed, active));

        Optional<RescueRequest> result = rescueRequestService.getActiveRequestForRescuer(rescuerId);

        assertTrue(result.isPresent());
        assertEquals(RequestStatus.ACCEPTED, result.get().getStatus());
    }

    @Test
    void acceptRequestByRescuer_ShouldAssignRescuerAndChangeStatus() {
        UUID requestId = request.getRequestId();
        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        rescueRequestService.acceptRequestByRescuer(requestId, rescuer);

        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertEquals(rescuer, request.getRescuer());
        verify(rescueRequestRepository, times(1)).save(request);
    }

    @Test
    void acceptRequestByRescuer_AlreadyAccepted_ShouldThrowException() {
        request.setStatus(RequestStatus.ACCEPTED);
        UUID requestId = request.getRequestId();
        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThrows(RuntimeException.class, () -> {
            rescueRequestService.acceptRequestByRescuer(requestId, rescuer);
        });
    }

    @Test
    void updateRequestStatus_ShouldUpdateStatus() {
        UUID requestId = request.getRequestId();
        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        rescueRequestService.updateRequestStatus(requestId, RequestStatus.ARRIVED);

        assertEquals(RequestStatus.ARRIVED, request.getStatus());
        verify(rescueRequestRepository, times(1)).save(request);
    }

    @Test
    void addExtraPrice_ShouldSetExtraPrice() {
        UUID requestId = request.getRequestId();
        request.setStatus(RequestStatus.IN_PROGRESS);
        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        rescueRequestService.addExtraPrice(requestId, new BigDecimal("25000.00"));

        assertEquals(new BigDecimal("25000.00"), request.getExtraPrice());
        verify(rescueRequestRepository, times(1)).save(request);
    }

    @Test
    void completeRequestWithOtp_ShouldSucceedOnCorrectOtp() {
        UUID requestId = request.getRequestId();
        request.setStatus(RequestStatus.IN_PROGRESS);
        request.setOtpCode("654321");
        request.setBasePrice(new BigDecimal("100000.00"));
        request.setExtraPrice(new BigDecimal("20000.00"));

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        rescueRequestService.completeRequestWithOtp(requestId, "654321");

        assertTrue(request.getInvoice().isPaid());
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
        verify(rescueRequestRepository, times(1)).save(request);
    }

    @Test
    void completeRequestWithOtp_WithRescuer_ShouldCreditWallet() {
        UUID requestId = request.getRequestId();
        request.setStatus(RequestStatus.IN_PROGRESS);
        request.setOtpCode("654321");
        request.setBasePrice(new BigDecimal("100000.00"));
        request.setExtraPrice(new BigDecimal("20000.00"));
        request.setRescuer(rescuer);

        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuer.getUserId())
                .walletBalance(new BigDecimal("50000.00"))
                .build();

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rescuerProfileRepository.findById(rescuer.getUserId())).thenReturn(Optional.of(profile));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        rescueRequestService.completeRequestWithOtp(requestId, "654321");

        assertEquals(RequestStatus.COMPLETED, request.getStatus());
        assertNotNull(request.getInvoice());
        assertTrue(request.getInvoice().isPaid());
        assertEquals(new BigDecimal("158000.00"), profile.getWalletBalance());
        assertEquals(new BigDecimal("120000.00"), profile.getCashDebt());
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void completeRequestWithOtp_WithBankTransfer_ShouldNotAccrueCashDebt() {
        UUID requestId = request.getRequestId();
        request.setStatus(RequestStatus.IN_PROGRESS);
        request.setOtpCode("654321");
        request.setBasePrice(new BigDecimal("100000.00"));
        request.setExtraPrice(new BigDecimal("20000.00"));
        request.setRescuer(rescuer);
        request.setPaymentMethod(PaymentMethod.BANK_TRANSFER);

        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuer.getUserId())
                .walletBalance(new BigDecimal("50000.00"))
                .build();

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rescuerProfileRepository.findById(rescuer.getUserId())).thenReturn(Optional.of(profile));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        rescueRequestService.completeRequestWithOtp(requestId, "654321");

        assertEquals(RequestStatus.COMPLETED, request.getStatus());
        assertNotNull(request.getInvoice());
        assertTrue(request.getInvoice().isPaid());
        assertEquals(new BigDecimal("158000.00"), profile.getWalletBalance());
        assertEquals(BigDecimal.ZERO, profile.getCashDebt());
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void completeRequestWithOtp_ShouldThrowOnIncorrectOtp() {
        UUID requestId = request.getRequestId();
        request.setStatus(RequestStatus.IN_PROGRESS);
        request.setOtpCode("654321");

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThrows(RuntimeException.class, () -> {
            rescueRequestService.completeRequestWithOtp(requestId, "000000");
        });
    }

    @Test
    void cancelRequest_ShouldApplyCancellationFeeAndCreditRescuer_WhenCanceledWhileActive() {
        UUID requestId = request.getRequestId();
        request.setStatus(RequestStatus.ACCEPTED);
        request.setRescuer(rescuer);

        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuer.getUserId())
                .walletBalance(new BigDecimal("50000.00"))
                .build();

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rescuerProfileRepository.findById(rescuer.getUserId())).thenReturn(Optional.of(profile));

        rescueRequestService.cancelRequest(requestId);

        assertEquals(RequestStatus.CANCELED, request.getStatus());
        assertEquals(new BigDecimal("20000.00"), request.getCancellationFee());
        assertEquals(new BigDecimal("70000.00"), profile.getWalletBalance()); // 50k + 20k penalty
        verify(rescueRequestRepository, times(1)).save(request);
        verify(rescuerProfileRepository, times(1)).save(profile);
    }

    @Test
    void acceptRequestByRescuer_ShouldCalculateRoadDistanceAndETA() {
        UUID requestId = request.getRequestId();
        request.setCustomerLat(new BigDecimal("21.0285"));
        request.setCustomerLng(new BigDecimal("105.8542"));

        RescuerProfile profile = RescuerProfile.builder()
                .rescuerId(rescuer.getUserId())
                .currentLat(new BigDecimal("21.0305"))
                .currentLng(new BigDecimal("105.8522"))
                .build();

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rescuerProfileRepository.findById(rescuer.getUserId())).thenReturn(Optional.of(profile));

        rescueRequestService.acceptRequestByRescuer(requestId, rescuer);

        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertEquals(rescuer, request.getRescuer());
        assertNotNull(request.getRoadDistance());
        assertNotNull(request.getEtaMinutes());
        assertTrue(request.getRoadDistance().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(request.getEtaMinutes() >= 2);
        verify(rescueRequestRepository, times(1)).save(request);
    }

    @Test
    void getRescuerInventory_ShouldReturnInventoryList() {
        UUID rescuerId = rescuer.getUserId();
        RescuerInventory item = RescuerInventory.builder()
                .partName("Săm xe máy")
                .quantity(5)
                .unitPrice(new BigDecimal("80000.00"))
                .build();
        when(rescuerInventoryRepository.findByRescuerUserId(rescuerId)).thenReturn(List.of(item));

        List<RescuerInventory> result = rescueRequestService.getRescuerInventory(rescuerId);

        assertEquals(1, result.size());
        assertEquals("Săm xe máy", result.get(0).getPartName());
        assertEquals(5, result.get(0).getQuantity());
    }

    @Test
    void usePart_ShouldDeductInventoryAndAddExtraPrice() {
        UUID requestId = request.getRequestId();
        request.setStatus(RequestStatus.IN_PROGRESS);
        request.setRescuer(rescuer);

        RescuerInventory inventory = RescuerInventory.builder()
                .id(UUID.randomUUID())
                .rescuer(rescuer)
                .partName("Săm xe máy")
                .quantity(5)
                .unitPrice(new BigDecimal("80000.00"))
                .build();

        when(rescueRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(rescuerInventoryRepository.findByRescuerUserIdAndPartName(rescuer.getUserId(), "Săm xe máy"))
                .thenReturn(Optional.of(inventory));
        when(rescuerInventoryRepository.save(any(RescuerInventory.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rescueRequestRepository.save(any(RescueRequest.class))).thenAnswer(req -> req.getArgument(0));

        rescueRequestService.usePart(requestId, "Săm xe máy", rescuer);

        assertEquals(4, inventory.getQuantity());
        assertEquals(new BigDecimal("80000.00"), request.getExtraPrice());
        verify(rescuerInventoryRepository, times(1)).save(inventory);
        verify(rescueRequestRepository, times(1)).save(request);
        verify(webSocketNotificationService, times(1)).notifyRequestUpdate(request);
    }
}
