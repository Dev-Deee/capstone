package com.dayo.capstone.cadence.common.email;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Brevo transactional email API: POST {base}/v3/smtp/email with an api-key header. */
public class BrevoApiEmailSender extends HttpApiEmailSender {

    public static final String DEFAULT_BASE_URL = "https://api.brevo.com";

    public BrevoApiEmailSender(String baseUrl, String apiKey, String fromAddress, String fromName, Duration timeout) {
        super(baseUrl, apiKey, fromAddress, fromName, timeout);
    }

    @Override public String name() { return "brevo-api"; }
    @Override protected String path() { return "/v3/smtp/email"; }
    @Override protected String authHeaderName() { return "api-key"; }
    @Override protected String authHeaderValue() { return apiKey; }

    @Override
    protected Map<String, Object> body(EmailMessage m) {
        return Map.of(
                "sender", Map.of("name", fromName, "email", fromAddress),
                "to", List.of(Map.of("email", m.to())),
                "subject", m.subject(),
                "textContent", m.body());
    }
}
