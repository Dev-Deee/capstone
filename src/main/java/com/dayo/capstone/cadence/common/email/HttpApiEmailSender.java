package com.dayo.capstone.cadence.common.email;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Email over a provider's HTTPS send API — the deployment default, since many
 * hosts block outbound SMTP. Subclasses supply the endpoint, auth header and
 * request body. Short connect and read timeouts; a 4xx (bad key, rejected
 * address) isn't retried, a 5xx or network error is.
 */
public abstract class HttpApiEmailSender implements EmailSender {

    protected final String apiKey;
    protected final String fromAddress;
    protected final String fromName;
    private final RestClient client;

    protected HttpApiEmailSender(String baseUrl, String apiKey, String fromAddress, String fromName, Duration timeout) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    protected abstract String path();

    protected abstract String authHeaderName();

    protected abstract String authHeaderValue();

    protected abstract Map<String, Object> body(EmailMessage m);

    @Override
    public void send(EmailMessage m) throws EmailSendException {
        try {
            client.post().uri(path())
                    .header(authHeaderName(), authHeaderValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body(m))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            // Status only — the response body can echo request details.
            throw new EmailSendException(name() + " rejected the email: HTTP " + status.value(),
                    status.is5xxServerError() || status.value() == 429, e);
        } catch (ResourceAccessException e) {
            throw new EmailSendException(name() + " unreachable or timed out", true, e);
        } catch (Exception e) {
            throw new EmailSendException(name() + " send failed: " + e.getClass().getSimpleName(), true, e);
        }
    }
}
