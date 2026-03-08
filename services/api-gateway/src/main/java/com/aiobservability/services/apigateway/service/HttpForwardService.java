package com.aiobservability.services.apigateway.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class HttpForwardService {
    private static final List<String> HOP_BY_HOP_HEADERS = List.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length", "expect"
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ResponseEntity<byte[]> forward(
            HttpMethod method,
            String targetUrl,
            Map<String, List<String>> incomingHeaders,
            byte[] body
    ) {
        HttpRequest.BodyPublisher bodyPublisher = body == null || body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(new URI(targetUrl));
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid route target URL", ex);
        }
        builder.method(method.name(), bodyPublisher);
        incomingHeaders.forEach((headerName, values) -> {
            if (isForwardableHeader(headerName)) {
                values.forEach(value -> builder.header(headerName, value));
            }
        });

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            HttpHeaders responseHeaders = new HttpHeaders();
            response.headers().map().forEach((headerName, values) -> {
                if (isForwardableHeader(headerName)) {
                    responseHeaders.put(headerName, values);
                }
            });
            return ResponseEntity
                    .status(response.statusCode())
                    .headers(responseHeaders)
                    .body(response.body());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ResponseEntity.status(502).body(("Upstream call failed: " + ex.getMessage()).getBytes());
        }
    }

    private boolean isForwardableHeader(String headerName) {
        return !HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase());
    }
}
