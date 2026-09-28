package com.cipherchat.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Newest first; callers reverse for display. {@code beforeId} null means "from the latest". */
    @Query("""
            select m from Message m join fetch m.sender left join fetch m.attachment
            where m.conversation.id = :conversationId and (:beforeId is null or m.id < :beforeId)
            order by m.id desc
            """)
    List<Message> findPage(@Param("conversationId") Long conversationId,
                           @Param("beforeId") Long beforeId,
                           Pageable pageable);

    boolean existsByAttachmentId(UUID attachmentId);
}
