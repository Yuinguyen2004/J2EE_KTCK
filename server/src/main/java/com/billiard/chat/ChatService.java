package com.billiard.chat;

import com.billiard.chat.dto.ChatConversationResponse;
import com.billiard.chat.dto.ChatConversationSummaryResponse;
import com.billiard.chat.dto.ChatMessageResponse;
import com.billiard.customers.AuthenticatedCustomerService;
import com.billiard.customers.Customer;
import com.billiard.users.User;
import com.billiard.users.UserRepository;
import com.billiard.users.UserRole;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {

    private final ChatConversationRepository chatConversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AuthenticatedCustomerService authenticatedCustomerService;
    private final UserRepository userRepository;
    private final ChatEvents chatEvents;

    public ChatService(
            ChatConversationRepository chatConversationRepository,
            ChatMessageRepository chatMessageRepository,
            AuthenticatedCustomerService authenticatedCustomerService,
            UserRepository userRepository,
            ChatEvents chatEvents
    ) {
        this.chatConversationRepository = chatConversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.authenticatedCustomerService = authenticatedCustomerService;
        this.userRepository = userRepository;
        this.chatEvents = chatEvents;
    }

    @Transactional(readOnly = true)
    public ChatConversationResponse getCustomerConversation(String customerEmail) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        return chatConversationRepository.findByCustomer_Id(customer.getId())
                .map(this::toDetailResponse)
                .orElseGet(() -> new ChatConversationResponse(
                        null,
                        customer.getId(),
                        customer.getUser().getFullName(),
                        customer.getUser().getEmail(),
                        null,
                        List.of()
                ));
    }

    @Transactional
    public ChatMessageResponse sendCustomerMessage(String customerEmail, String content) {
        Customer customer = authenticatedCustomerService.getRequiredCustomer(customerEmail);
        ChatConversation conversation = findOrCreateConversation(customer);
        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(customer.getUser());
        message.setSenderRole(ChatSenderRole.CUSTOMER);
        message.setContent(normalizeContent(content));
        ChatMessage savedMessage = chatMessageRepository.save(message);
        conversation.setLastMessageAt(savedMessage.getCreatedAt());
        chatConversationRepository.save(conversation);
        ChatMessageResponse response = toMessageResponse(savedMessage);
        chatEvents.messageSent(customer.getId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationSummaryResponse> listConversations() {
        return chatConversationRepository.findAllByOrderByLastMessageAtDesc().stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatConversationResponse getConversation(Long conversationId) {
        return toDetailResponse(findConversation(conversationId));
    }

    @Transactional
    public ChatMessageResponse sendStaffMessage(Long conversationId, String content, String staffEmail) {
        ChatConversation conversation = findConversation(conversationId);
        User staff = findStaff(staffEmail);

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setSender(staff);
        message.setSenderRole(ChatSenderRole.STAFF);
        message.setContent(normalizeContent(content));
        ChatMessage savedMessage = chatMessageRepository.save(message);
        conversation.setLastMessageAt(savedMessage.getCreatedAt());
        chatConversationRepository.save(conversation);
        ChatMessageResponse response = toMessageResponse(savedMessage);
        chatEvents.messageSent(conversation.getCustomer().getId(), response);
        return response;
    }

    private ChatConversation findOrCreateConversation(Customer customer) {
        return chatConversationRepository.findByCustomer_Id(customer.getId())
                .orElseGet(() -> {
                    ChatConversation conversation = new ChatConversation();
                    conversation.setCustomer(customer);
                    conversation.setLastMessageAt(Instant.now());
                    return chatConversationRepository.save(conversation);
                });
    }

    private ChatConversation findConversation(Long conversationId) {
        return chatConversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conversation not found"
                ));
    }

    private User findStaff(String staffEmail) {
        User user = userRepository.findByEmailIgnoreCase(staffEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff user not found"));
        if (!user.isActive() || (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.STAFF)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only staff can reply to chats");
        }
        return user;
    }

    private ChatConversationSummaryResponse toSummaryResponse(ChatConversation conversation) {
        List<ChatMessage> messages =
                chatMessageRepository.findAllByConversation_IdOrderByCreatedAtAsc(conversation.getId());
        ChatMessage latestMessage = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        Customer customer = conversation.getCustomer();
        return new ChatConversationSummaryResponse(
                conversation.getId(),
                customer.getId(),
                customer.getUser().getFullName(),
                customer.getUser().getEmail(),
                conversation.getLastMessageAt(),
                latestMessage == null ? null : latestMessage.getContent()
        );
    }

    private ChatConversationResponse toDetailResponse(ChatConversation conversation) {
        Customer customer = conversation.getCustomer();
        return new ChatConversationResponse(
                conversation.getId(),
                customer.getId(),
                customer.getUser().getFullName(),
                customer.getUser().getEmail(),
                conversation.getLastMessageAt(),
                chatMessageRepository.findAllByConversation_IdOrderByCreatedAtAsc(conversation.getId())
                        .stream()
                        .map(this::toMessageResponse)
                        .toList()
        );
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSenderRole(),
                message.getSender().getFullName(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message content is required");
        }
        return content.trim();
    }
}
