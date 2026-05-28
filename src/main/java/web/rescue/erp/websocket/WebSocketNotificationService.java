package web.rescue.erp.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import web.rescue.erp.entity.RescueRequest;
import web.rescue.erp.entity.enums.RequestStatus;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationService {

    private final RescueWebSocketHandler webSocketHandler;

    /**
     * Notify customer and rescuer (and other rescuers if needed) about a request update
     */
    public void notifyRequestUpdate(RescueRequest request) {
        if (request == null) return;

        String requestId = request.getRequestId().toString();
        String status = request.getStatus().name();
        String jsonMsg = String.format("{\"type\":\"REQUEST_UPDATE\",\"requestId\":\"%s\",\"status\":\"%s\"}", requestId, status);

        // Notify customer
        if (request.getCustomer() != null) {
            String customerPhone = request.getCustomer().getPhone();
            webSocketHandler.sendMessageToUser(customerPhone, jsonMsg);
            log.info("Notified customer ({}) about request status {}", customerPhone, status);
        }

        // Notify assigned rescuer
        if (request.getRescuer() != null) {
            String rescuerPhone = request.getRescuer().getPhone();
            webSocketHandler.sendMessageToUser(rescuerPhone, jsonMsg);
            log.info("Notified rescuer ({}) about request status {}", rescuerPhone, status);
        }

        // Notify all online rescuers to refresh their lists if request becomes PENDING
        // or transitions from PENDING to something else (e.g. ACCEPTED / CANCELED)
        if (request.getStatus() == RequestStatus.PENDING || status.equals("ACCEPTED") || status.equals("CANCELED")) {
            String incomingJson = "{\"type\":\"INCOMING_REQUESTS_UPDATE\"}";
            webSocketHandler.sendMessageToRole("RESCUER", incomingJson);
            log.info("Notified all rescuers to refresh incoming requests list");
        }
    }

    /**
     * Notify customer about the rescuer's real-time location update
     */
    public void notifyLocationUpdate(String customerPhone, BigDecimal lat, BigDecimal lng, String rescuerName) {
        String jsonMsg = String.format(
                "{\"type\":\"LOCATION_UPDATE\",\"lat\":%s,\"lng\":%s,\"rescuerName\":\"%s\"}",
                lat.toString(), lng.toString(), rescuerName
        );
        webSocketHandler.sendMessageToUser(customerPhone, jsonMsg);
        log.debug("Notified customer ({}) of location update: {}, {}", customerPhone, lat, lng);
    }

    /**
     * Notify rescuer about payment reminder for outstanding cash debt
     */
    public void notifyPaymentReminder(String rescuerPhone, String message) {
        String jsonMsg = String.format(
                "{\"type\":\"PAYMENT_REMINDER\",\"message\":\"%s\"}",
                message
        );
        webSocketHandler.sendMessageToUser(rescuerPhone, jsonMsg);
        log.info("Notified rescuer ({}) with payment reminder", rescuerPhone);
    }
}
