package com.dayo.capstone.cadence.notification;

import com.dayo.capstone.cadence.common.email.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The HTTPS email senders against a fake provider on localhost — nothing is
 * really sent. Covers the request each provider expects, error
 * classification (retry or not), and the timeout.
 */
class EmailTransportTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<JsonNode> body = new AtomicReference<>();
    private volatile int status = 201;
    private volatile long delayMs = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            String a = exchange.getRequestHeaders().getFirst("api-key");
            auth.set(a != null ? "api-key " + a : exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes()));
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) { }
            byte[] out = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private final EmailMessage message = new EmailMessage("ada@example.com", "Hello", "Body text");

    @Test
    void brevoSendsTheExpectedRequest() throws Exception {
        new BrevoApiEmailSender(base(), "brevo-key", "noreply@cadence.test", "Cadence", Duration.ofSeconds(2)).send(message);
        assertThat(path.get()).isEqualTo("/v3/smtp/email");
        assertThat(auth.get()).isEqualTo("api-key brevo-key");
        assertThat(body.get().at("/sender/email").asText()).isEqualTo("noreply@cadence.test");
        assertThat(body.get().at("/to/0/email").asText()).isEqualTo("ada@example.com");
        assertThat(body.get().get("subject").asText()).isEqualTo("Hello");
        assertThat(body.get().get("textContent").asText()).isEqualTo("Body text");
    }

    @Test
    void resendSendsTheExpectedRequest() throws Exception {
        new ResendApiEmailSender(base(), "re-key", "noreply@cadence.test", "Cadence", Duration.ofSeconds(2)).send(message);
        assertThat(path.get()).isEqualTo("/emails");
        assertThat(auth.get()).isEqualTo("Bearer re-key");
        assertThat(body.get().get("from").asText()).isEqualTo("Cadence <noreply@cadence.test>");
        assertThat(body.get().at("/to/0").asText()).isEqualTo("ada@example.com");
        assertThat(body.get().get("text").asText()).isEqualTo("Body text");
    }

    @Test
    void serverErrorsAreRetryableButRejectionsAreNot() {
        HttpApiEmailSender sender = new BrevoApiEmailSender(base(), "k", "a@b.test", "C", Duration.ofSeconds(2));
        status = 503;
        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(EmailSendException.class)
                .satisfies(e -> assertThat(((EmailSendException) e).isRetryable()).isTrue())
                .hasMessageContaining("503")
                .hasMessageNotContaining("k\"");
        status = 401;
        assertThatThrownBy(() -> sender.send(message))
                .satisfies(e -> assertThat(((EmailSendException) e).isRetryable()).isFalse())
                .hasMessageContaining("401");
    }

    @Test
    void aSlowProviderTimesOutQuickly() {
        delayMs = 3000;
        HttpApiEmailSender sender = new ResendApiEmailSender(base(), "k", "a@b.test", "C", Duration.ofMillis(300));
        long started = System.currentTimeMillis();
        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(EmailSendException.class)
                .satisfies(e -> assertThat(((EmailSendException) e).isRetryable()).isTrue());
        assertThat(System.currentTimeMillis() - started).isLessThan(2000);
    }

    @Test
    void anUnreachableProviderFailsFast() {
        HttpApiEmailSender sender = new BrevoApiEmailSender("http://127.0.0.1:1", "k", "a@b.test", "C", Duration.ofMillis(500));
        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(EmailSendException.class)
                .hasMessageContaining("brevo-api");
    }
}
