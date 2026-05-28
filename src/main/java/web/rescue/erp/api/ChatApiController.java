package web.rescue.erp.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import web.rescue.erp.dto.ApiResponse;
import web.rescue.erp.entity.ChatMessage;
import web.rescue.erp.repository.ChatMessageRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
@Slf4j
public class ChatApiController {

    private final ChatMessageRepository chatMessageRepository;

    @GetMapping("/{id}/chat-history")
    public ResponseEntity<ApiResponse> getChatHistory(@PathVariable UUID id) {
        try {
            List<ChatMessage> history = chatMessageRepository.findByRescueRequestRequestIdOrderByCreatedAtAsc(id);
            List<Map<String, Object>> data = history.stream().map(msg -> {
                Map<String, Object> map = new HashMap<>();
                map.put("messageId", msg.getMessageId());
                map.put("senderPhone", msg.getSenderPhone());
                map.put("messageContent", msg.getMessageContent());
                map.put("createdAt", msg.getCreatedAt());
                return map;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.ok("Tải lịch sử tin nhắn thành công!", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
