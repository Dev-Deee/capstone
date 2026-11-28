package com.dayo.capstone.cadence.notification.dto;

/** Whether push is configured on this server and, if so, the VAPID public key browsers subscribe with. */
public record PushPublicKeyResponse(boolean enabled, String publicKey) {}
