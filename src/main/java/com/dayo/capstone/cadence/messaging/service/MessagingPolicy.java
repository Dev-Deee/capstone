package com.dayo.capstone.cadence.messaging.service;

import com.dayo.capstone.cadence.institution.model.Membership;
import com.dayo.capstone.cadence.messaging.repository.ConversationRepository;
import com.dayo.capstone.cadence.user.enums.Role;
import com.dayo.capstone.cadence.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Who may start (and keep) a direct conversation with whom, inside one
 * institution. Any one rule is enough:
 *   - staff (facilitator, admin, super admin) ↔ staff;
 *   - a student ↔ an admin or super admin;
 *   - a student ↔ a facilitator actively teaching one of the student's active cohorts.
 * Students never message other students. Both people need an active
 * membership here and a live account; roles are read from that membership,
 * so roles held in another institution count for nothing.
 */
@Component
@RequiredArgsConstructor
public class MessagingPolicy {

    private static final Set<Role> STAFF = EnumSet.of(Role.FACILITATOR, Role.ADMIN, Role.SUPER_ADMIN);
    private static final Set<Role> ADMINS = EnumSet.of(Role.ADMIN, Role.SUPER_ADMIN);

    private final ConversationRepository conversationRepository;

    /** The person's roles in this institution, empty if they can't be reached here. */
    public static Set<Role> rolesIn(User user, Long institutionId) {
        if (user.isDeleted() || !user.isActive()) return Set.of();
        Optional<Membership> m = user.membershipIn(institutionId).filter(Membership::isActive);
        return m.<Set<Role>>map(x -> x.getRoles().isEmpty() ? Set.of() : EnumSet.copyOf(x.getRoles())).orElse(Set.of());
    }

    @Transactional(readOnly = true)
    public boolean mayMessage(User me, User other, Long institutionId) {
        if (me.getId().equals(other.getId())) return false;
        Set<Role> mine = rolesIn(me, institutionId);
        Set<Role> theirs = rolesIn(other, institutionId);
        if (mine.isEmpty() || theirs.isEmpty()) return false;
        if (any(mine, STAFF) && any(theirs, STAFF)) return true;
        if (mine.contains(Role.STUDENT) && any(theirs, ADMINS)) return true;
        if (theirs.contains(Role.STUDENT) && any(mine, ADMINS)) return true;
        if (mine.contains(Role.FACILITATOR) && theirs.contains(Role.STUDENT)
                && conversationRepository.countTeaching(institutionId, me.getId(), other.getId()) > 0) return true;
        return theirs.contains(Role.FACILITATOR) && mine.contains(Role.STUDENT)
                && conversationRepository.countTeaching(institutionId, other.getId(), me.getId()) > 0;
    }

    /** Everyone here I may message whose name matches {@code query}, at most {@code limit}. */
    @Transactional(readOnly = true)
    public List<Membership> contacts(User me, Long institutionId, String query, int limit) {
        Set<Role> mine = rolesIn(me, institutionId);
        if (mine.isEmpty()) return List.of();
        boolean staff = any(mine, STAFF);
        boolean admin = any(mine, ADMINS);
        Set<Long> myStudents = mine.contains(Role.FACILITATOR)
                ? new HashSet<>(conversationRepository.findStudentIdsTaughtBy(institutionId, me.getId())) : Set.of();
        Set<Long> myFacilitators = mine.contains(Role.STUDENT)
                ? new HashSet<>(conversationRepository.findFacilitatorIdsTeaching(institutionId, me.getId())) : Set.of();
        String pattern = "%" + (query == null ? "" : query.trim().toLowerCase()
                .replace("\\", "").replace("%", "").replace("_", "")) + "%";

        return conversationRepository.findMembersMatching(institutionId, me.getId(), pattern).stream()
                .filter(m -> {
                    Set<Role> theirs = m.getRoles();
                    if (staff && any(theirs, STAFF)) return true;
                    if (theirs.contains(Role.STUDENT) && (admin || myStudents.contains(m.getUser().getId()))) return true;
                    if (mine.contains(Role.STUDENT) && any(theirs, ADMINS)) return true;
                    return mine.contains(Role.STUDENT) && theirs.contains(Role.FACILITATOR)
                            && myFacilitators.contains(m.getUser().getId());
                })
                .limit(limit)
                .toList();
    }

    private static boolean any(Set<Role> roles, Set<Role> wanted) {
        for (Role r : roles) if (wanted.contains(r)) return true;
        return false;
    }
}
