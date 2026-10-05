package com.dayo.capstone.cadence.common.event;

import java.util.UUID;

/** Someone accepted an invite and joined the institution. The actor is the person who joined. */
public class InviteAcceptedEvent extends DomainEvent {

    private final Long inviterId;       // who sent it; null for invites with no recorded sender
    private final UUID memberPublicId;
    private final String memberName;
    private final String role;          // STUDENT / FACILITATOR / ADMIN / SUPER_ADMIN

    public InviteAcceptedEvent(Long institutionId, Long memberId, Long inviterId,
                               UUID memberPublicId, String memberName, String role) {
        super(institutionId, memberId);
        this.inviterId = inviterId;
        this.memberPublicId = memberPublicId;
        this.memberName = memberName;
        this.role = role;
    }

    public Long inviterId() { return inviterId; }
    public UUID memberPublicId() { return memberPublicId; }
    public String memberName() { return memberName; }
    public String role() { return role; }
}
