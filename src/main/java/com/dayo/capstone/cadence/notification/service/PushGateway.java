package com.dayo.capstone.cadence.notification.service;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/** Sends one already-encrypted message to a push service. Returns the HTTP status. Mocked in tests. */
public interface PushGateway {
    int send(URI endpoint, Map<String, String> headers, byte[] body) throws IOException, InterruptedException;
}
