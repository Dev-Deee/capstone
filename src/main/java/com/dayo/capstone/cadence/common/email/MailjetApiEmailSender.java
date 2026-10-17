package com.dayo.capstone.cadence.common.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Mailjet Send API v3.1: POST {base}/v3.1/send with HTTP Basic auth
 * (API key : secret key). Mailjet reports each message's outcome inside the
 * response ("Status": "success" | "error"), so a 200 alone isn't trusted.
 */
public class MailjetApiEmailSender extends HttpApiEmailSender {

    public static final String DEFAULT_BASE_URL = "https://api.mailjet.com";

    private static final ObjectMapper JSON = new ObjectMapper();
    private final String apiSecret;

    public MailjetApiEmailSender(String baseUrl, String apiKey, String apiSecret,
                                 String fromAddress, String fromName, Duration timeout) {
        super(baseUrl, apiKey, fromAddress, fromName, timeout);
        this.apiSecret = apiSecret;
    }

    @Override public String name() { return "mailjet-api"; }
    @Override protected String path() { return "/v3.1/send"; }
    @Override protected String authHeaderName() { return "Authorization"; }

    @Override
    protected String authHeaderValue() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Map<String, Object> body(EmailMessage m) {
        return Map.of("Messages", List.of(Map.of(
                "From", Map.of("Email", fromAddress, "Name", fromName),
                "To", List.of(Map.of("Email", m.to())),
                "Subject", m.subject(),
                "TextPart", m.body())));
    }

    @Override
    protected void checkResponse(String responseBody) throws EmailSendException {
        if (responseBody == null || responseBody.isBlank()) return;
        try {
            JsonNode messages = JSON.readTree(responseBody).path("Messages");
            for (JsonNode message : messages) {
                String status = message.path("Status").asText("");
                if (!status.isEmpty() && !"success".equalsIgnoreCase(status)) {
                    // A message Mailjet refused (bad address, unverified sender…) won't pass on a retry.
                    throw new EmailSendException("mailjet-api rejected the email: status " + status, false, null);
                }
            }
        } catch (EmailSendException e) {
            throw e;
        } catch (Exception e) {
            // Unreadable body on a 2xx: the provider accepted the request; don't fail the send over it.
        }
    }
}
