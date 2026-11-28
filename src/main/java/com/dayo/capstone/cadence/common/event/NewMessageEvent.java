package com.dayo.capstone.cadence.common.event;

import java.util.UUID;

/** A direct message was sent; its recipient should hear about it (collapsed per conversation). */
public class NewMessageEvent extends DomainEvent {

    private final Long recipientId;
    private final String senderName;
    private final UUID conversationPublicId;
    private final String preview;

    public NewMessageEvent(Long institutionId, Long senderId, Long recipientId, String senderName,
                           UUID conversationPublicId, String preview) {
        super(institutionId, senderId);
        this.recipientId = recipientId;
        this.senderName = senderName;
        this.conversationPublicId = conversationPublicId;
        this.preview = preview;
    }

    public Long recipientId() { return recipientId; }
    public String senderName() { return senderName; }
    public UUID conversationPublicId() { return conversationPublicId; }
    public String preview() { return preview; }
}
