package com.dayo.capstone.cadence.notification.service;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** {@link PushGateway} over the JDK HTTP client, with short timeouts and no redirects. */
@Component
public class HttpPushGateway implements PushGateway {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public int send(URI endpoint, Map<String, String> headers, byte[] body) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(b::header);
        return http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
