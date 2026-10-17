package com.dayo.capstone.cadence.common.email;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Resend email API: POST {base}/emails with a Bearer API key. */
public class ResendApiEmailSender extends HttpApiEmailSender {

    public static final String DEFAULT_BASE_URL = "https://api.resend.com";

    public ResendApiEmailSender(String baseUrl, String apiKey, String fromAddress, String fromName, Duration timeout) {
        super(baseUrl, apiKey, fromAddress, fromName, timeout);
    }

    @Override public String name() { return "resend-api"; }
    @Override protected String path() { return "/emails"; }
    @Override protected String authHeaderName() { return "Authorization"; }
    @Override protected String authHeaderValue() { return "Bearer " + apiKey; }

    @Override
    protected Map<String, Object> body(EmailMessage m) {
        return Map.of(
                "from", fromName + " <" + fromAddress + ">",
                "to", List.of(m.to()),
                "subject", m.subject(),
                "text", m.body());
    }
}
