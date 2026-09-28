package com.cipherchat.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
            select c from Conversation c
            join fetch c.userA join fetch c.userB
            where c.userA.id = :userId or c.userB.id = :userId
            order by coalesce(c.lastMessageAt, c.createdAt) desc
            """)
    List<Conversation> findAllForUser(@Param("userId") Long userId);

    @Query("select c from Conversation c where c.userA.id = :a and c.userB.id = :b")
    Optional<Conversation> findPair(@Param("a") Long lowerUserId, @Param("b") Long higherUserId);
}
