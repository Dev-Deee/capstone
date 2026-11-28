package com.dayo.capstone.cadence.messaging.service;

import com.dayo.capstone.cadence.common.event.DomainEvents;
import com.dayo.capstone.cadence.common.event.NewMessageEvent;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.model.Institution;
import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.messaging.dto.ConversationResponse;
import com.dayo.capstone.cadence.messaging.dto.MessagePage;
import com.dayo.capstone.cadence.messaging.dto.MessageResponse;
import com.dayo.capstone.cadence.messaging.dto.PersonRef;
import com.dayo.capstone.cadence.messaging.model.Conversation;
import com.dayo.capstone.cadence.messaging.model.Message;
import com.dayo.capstone.cadence.messaging.repository.ConversationRepository;
import com.dayo.capstone.cadence.messaging.repository.MessageRepository;
import com.dayo.capstone.cadence.notification.service.NotificationService;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Direct 1:1 messages inside the institution the caller is working in. Who may
 * talk to whom is MessagingPolicy; here, a conversation is only ever visible to
 * its two participants and only in its own institution — anything else is
 * "not found". The web app polls (no WebSockets), so the read paths are kept
 * to a couple of queries each.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int PREVIEW = 200;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessagingPolicy policy;
    private final NotificationService notificationService;
    private final DomainEvents domainEvents;

    // ── Conversations ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(User me, Institution institution) {
        List<Conversation> conversations = conversationRepository.findMyConversations(institution.getId(), me.getId());
        Map<Long, Long> unread = unreadByConversation(me, institution);
        return conversations.stream()
                .map(c -> toResponse(c, me, unread.getOrDefault(c.getId(), 0L), institution))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User me, Institution institution) {
        return messageRepository.countUnread(institution.getId(), me.getId());
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(UUID publicId, User me, Institution institution) {
        Conversation c = mine(publicId, me, institution);
        return toResponse(c, me, unreadByConversation(me, institution).getOrDefault(c.getId(), 0L), institution);
    }

    /** The conversation with this person, created if there isn't one yet. */
    @Transactional
    public ConversationResponse start(UUID otherPublicId, User me, Institution institution) {
        User other = userRepository.findByPublicId(otherPublicId)
                .filter(u -> !MessagingPolicy.rolesIn(u, institution.getId()).isEmpty())
                .orElseThrow(() -> new ResourceNotFoundException("Person not found"));
        if (!policy.mayMessage(me, other, institution.getId())) {
            throw new AccessDeniedException("You can't message this person");
        }
        User a = me.getId() < other.getId() ? me : other;
        User b = a == me ? other : me;
        Conversation c = conversationRepository.findPair(institution.getId(), a.getId(), b.getId()).orElseGet(() -> {
            Conversation created = new Conversation();
            created.setInstitution(institution);
            created.setParticipantA(a);
            created.setParticipantB(b);
            Conversation saved = conversationRepository.save(created);
            log.info("Conversation started: publicId={}, institutionId={}", saved.getPublicId(), institution.getId());
            return saved;
        });
        return toResponse(c, me, unreadByConversation(me, institution).getOrDefault(c.getId(), 0L), institution);
    }

    // ── Messages ──────────────────────────────────────────────────────────

    /**
     * One page of a thread, oldest → newest. With {@code after}: only what came
     * after that message (the polling path). With {@code before}: the page of
     * older messages before it. With neither: the newest page.
     */
    @Transactional(readOnly = true)
    public MessagePage messages(UUID conversationPublicId, UUID before, UUID after, int size,
                                User me, Institution institution) {
        Conversation c = mine(conversationPublicId, me, institution);
        int limit = Math.min(Math.max(size, 1), 100);
        if (after != null) {
            Long afterId = cursor(after, c);
            List<Message> newer = messageRepository.findAfter(c.getId(), afterId, PageRequest.of(0, limit));
            return new MessagePage(newer.stream().map(m -> toResponse(m, me)).toList(), false);
        }
        List<Message> page = before != null
                ? messageRepository.findBefore(c.getId(), cursor(before, c), PageRequest.of(0, limit + 1))
                : messageRepository.findLatest(c.getId(), PageRequest.of(0, limit + 1));
        boolean hasMore = page.size() > limit;
        List<Message> items = new ArrayList<>(hasMore ? page.subList(0, limit) : page);
        Collections.reverse(items);
        return new MessagePage(items.stream().map(m -> toResponse(m, me)).toList(), hasMore);
    }

    @Transactional
    public MessageResponse send(UUID conversationPublicId, String body, User me, Institution institution) {
        Conversation c = mine(conversationPublicId, me, institution);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("Write a message first");
        if (text.length() > Message.MAX_BODY) throw new IllegalArgumentException("A message can be at most 4000 characters");
        User other = c.other(me);
        // Checked on every send, not just on start: a facilitator who stops
        // teaching a student's cohort can't keep messaging them.
        if (!policy.mayMessage(me, other, institution.getId())) {
            throw new AccessDeniedException("You can't message this person any more");
        }

        Message m = new Message();
        m.setConversation(c);
        m.setSender(me);
        m.setBody(text);
        Message saved = messageRepository.save(m);

        String preview = preview(text);
        c.setLastMessageAt(saved.getCreatedAt());
        c.setLastMessagePreview(preview);
        c.setLastMessageSenderId(me.getId());
        // The sender's read watermark is left alone: their own messages never count as
        // unread, and a reply sent before polling caught up mustn't hide what they missed.
        conversationRepository.save(c);

        domainEvents.publish(new NewMessageEvent(institution.getId(), me.getId(), other.getId(),
                me.getFullName(), c.getPublicId(), preview));
        return toResponse(saved, me);
    }

    /** Everything in this conversation up to now is read by me; its notifications too. */
    @Transactional
    public ConversationResponse markRead(UUID conversationPublicId, User me, Institution institution) {
        Conversation c = mine(conversationPublicId, me, institution);
        Long latest = messageRepository.findLatestId(c.getId());
        c.markReadBy(me, latest, LocalDateTime.now());
        conversationRepository.save(c);
        notificationService.markReadForLink(me.getId(), institution.getId(), c.getPublicId());
        return toResponse(c, me, 0L, institution);
    }

    // ── People ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PersonRef> contacts(String query, User me, Institution institution) {
        return policy.contacts(me, institution.getId(), query, 50).stream()
                .map(m -> person(m.getUser(), m.getRoles()))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Conversation mine(UUID publicId, User me, Institution institution) {
        return conversationRepository.findMine(publicId, institution.getId(), me.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private Long cursor(UUID messagePublicId, Conversation c) {
        return messageRepository.findInConversation(messagePublicId, c.getId())
                .map(Message::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));
    }

    private Map<Long, Long> unreadByConversation(User me, Institution institution) {
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : messageRepository.countUnreadByConversation(institution.getId(), me.getId())) {
            unread.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return unread;
    }

    private ConversationResponse toResponse(Conversation c, User me, long unread, Institution institution) {
        User other = c.other(me);
        return new ConversationResponse(
                c.getPublicId(),
                person(other, MessagingPolicy.rolesIn(other, institution.getId())),
                c.getLastMessagePreview(),
                c.getLastMessageAt(),
                me.getId().equals(c.getLastMessageSenderId()),
                unread,
                policy.mayMessage(me, other, institution.getId()));
    }

    private static MessageResponse toResponse(Message m, User me) {
        User sender = m.getSender();
        return new MessageResponse(m.getPublicId(), sender.getPublicId(), sender.getFullName(),
                sender.getId().equals(me.getId()), m.getBody(), m.getCreatedAt());
    }

    static PersonRef person(User u, Set<Role> roles) {
        return new PersonRef(u.getPublicId(), u.getFullName(),
                roles.stream().sorted(Comparator.reverseOrder()).map(Enum::name).toList(),
                u.getProfileImageUrl());
    }

    private static String preview(String text) {
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() <= PREVIEW ? flat : flat.substring(0, PREVIEW - 1) + "…";
    }
}
