package web.rescue.erp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import web.rescue.erp.entity.ChatMessage;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByRescueRequestRequestIdOrderByCreatedAtAsc(UUID requestId);
}
