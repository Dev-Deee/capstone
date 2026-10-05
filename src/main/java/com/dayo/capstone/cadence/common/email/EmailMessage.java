package com.dayo.capstone.cadence.common.email;

/** A plain-text email. The sender address and name come from configuration, not the caller. */
public record EmailMessage(String to, String subject, String body) {}
