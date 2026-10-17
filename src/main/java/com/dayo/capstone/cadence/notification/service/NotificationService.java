package com.dayo.capstone.cadence.notification.service;

import com.dayo.capstone.cadence.common.event.DomainEvent;
import com.dayo.capstone.cadence.common.exception.ResourceNotFoundException;
import com.dayo.capstone.cadence.institution.repository.MembershipRepository;
import com.dayo.capstone.cadence.notification.model.Notification;
import com.dayo.capstone.cadence.notification.model.NotificationChannel;
import com.dayo.capstone.cadence.notification.model.NotificationType;
import com.dayo.capstone.cadence.notification.repository.NotificationRepository;
import com.dayo.capstone.cadence.security.service.EmailService;
import com.dayo.capstone.cadence.user.model.User;
import com.dayo.capstone.cadence.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Turns a domain event into notifications (PRD Section 12) and serves a
 * user's inbox.
 *
 * Delivery rules:
 * - Only people who can be reached in the event's institution: an active
 *   membership there and an account that isn't deleted. Being removed from
 *   one institution doesn't affect another.
 * - Never the person who caused the event.
 * - Idempotent: one row per (event, recipient), enforced by a unique dedupe
 *   key, so a re-delivered or double-fired event notifies nobody twice.
 * - Rows go in with one JDBC batch insert (entity ids are IDENTITY, which
 *   stops Hibernate batching) — the path an institution-wide broadcast will
 *   use later (PRD 18.9).
 * - Email is extra, only for whitelisted types, and never blocks or replaces
 *   in-app delivery.
 */
@Slf4j
@Service
public class NotificationService {

    /** One notification to write, before recipient filtering. */
    public record Draft(Long recipientId, NotificationType type, String title, String body,
                        String linkRoute, UUID linkPublicId) {}

    private static final String INSERT_SQL = """
            INSERT INTO notification
              (public_id, created_at, recipient_id, institution_id, type, title, body,
               link_route, link_public_id, channel, read_at, dedupe_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
            ON CONFLICT DO NOTHING
            """;

    private final NotificationRepository repository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final EmailService emailService;
    private final Set<NotificationType> emailTypes;

    public NotificationService(NotificationRepository repository, MembershipRepository membershipRepository,
                               UserRepository userRepository, JdbcTemplate jdbc, EmailService emailService,
                               @Value("${app.notifications.email-types:ATTENDANCE_OVERRIDDEN}") String emailTypes) {
        this.repository = repository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.emailService = emailService;
        this.emailTypes = Arrays.stream(emailTypes.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(NotificationType::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Writes the drafts that pass the delivery rules. Returns how many were newly written. */
    @Transactional
    public int deliver(DomainEvent event, List<Draft> drafts) {
        List<Draft> wanted = drafts.stream()
                .filter(d -> d.recipientId() != null && !d.recipientId().equals(event.actorId()))
                .toList();
        if (wanted.isEmpty()) return 0;

        Set<Long> reachable = new HashSet<>(membershipRepository.findReachableUserIds(
                event.institutionId(), wanted.stream().map(Draft::recipientId).collect(Collectors.toSet())));
        List<Draft> toWrite = wanted.stream().filter(d -> reachable.contains(d.recipientId())).toList();
        if (toWrite.isEmpty()) return 0;

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int[][] results = jdbc.batchUpdate(INSERT_SQL, toWrite, 100, (ps, d) -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setTimestamp(2, now);
            ps.setLong(3, d.recipientId());
            ps.setLong(4, event.institutionId());
            ps.setString(5, d.type().name());
            ps.setString(6, d.title());
            ps.setString(7, d.body());
            ps.setString(8, d.linkRoute());
            ps.setObject(9, d.linkPublicId());
            ps.setString(10, NotificationChannel.IN_APP.name());
            ps.setString(11, dedupeKey(event, d.recipientId()));
        });

        List<Draft> written = new ArrayList<>();
        int i = 0;
        for (int[] batch : results) {
            for (int rows : batch) {
                // >0: inserted; 0: a duplicate skipped; SUCCESS_NO_INFO (-2): driver didn't say.
                if (rows != 0) written.add(toWrite.get(i));
                i++;
            }
        }
        log.info("Notifications delivered: eventId={}, type={}, written={}, skipped={}",
                event.eventId(), toWrite.get(0).type(), written.size(), drafts.size() - written.size());
        emailWhitelisted(written);
        return written.size();
    }

    private void emailWhitelisted(List<Draft> written) {
        List<Draft> forEmail = written.stream().filter(d -> emailTypes.contains(d.type())).toList();
        if (forEmail.isEmpty()) return;
        Map<Long, String> emails = userRepository.findAllById(forEmail.stream().map(Draft::recipientId).toList())
                .stream().filter(u -> u.getEmail() != null)
                .collect(Collectors.toMap(User::getId, User::getEmail));
        for (Draft d : forEmail) {
            String to = emails.get(d.recipientId());
            if (to != null) emailService.sendNotification(to, d.title(), d.body());
        }
    }

    /** The person page for {@code personPublicId}, as seen by a viewer with these roles here. */
    @Transactional(readOnly = true)
    public String personRoute(Long viewerId, Long institutionId, UUID personPublicId) {
        boolean owner = membershipRepository.findReachableRoles(viewerId, institutionId).contains(
                com.dayo.capstone.cadence.user.enums.Role.SUPER_ADMIN);
        return (owner ? "/dashboard/super-admin/users/" : "/dashboard/admin/people/") + personPublicId;
    }

    static String dedupeKey(DomainEvent event, Long recipientId) {
        return event.eventId() + ":" + recipientId;
    }

    // ── Inbox ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Notification> inbox(Long userId, Long institutionId, int page, int size) {
        return repository.findInbox(userId, institutionId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId, Long institutionId) {
        return repository.countUnread(userId, institutionId);
    }

    @Transactional
    public Notification markRead(UUID publicId, Long userId, Long institutionId) {
        Notification n = repository.findOwned(publicId, userId, institutionId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (n.getReadAt() == null) n.setReadAt(LocalDateTime.now());
        return n;
    }

    @Transactional
    public int markAllRead(Long userId, Long institutionId) {
        return repository.markAllRead(userId, institutionId, LocalDateTime.now());
    }
}
