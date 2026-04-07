package com.billiard.chat;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    @EntityGraph(attributePaths = {"customer", "customer.user", "customer.membershipTier"})
    Optional<ChatConversation> findByCustomer_Id(Long customerId);

    @Override
    @EntityGraph(attributePaths = {"customer", "customer.user", "customer.membershipTier"})
    Optional<ChatConversation> findById(Long id);

    @EntityGraph(attributePaths = {"customer", "customer.user", "customer.membershipTier"})
    List<ChatConversation> findAllByOrderByLastMessageAtDesc();
}
