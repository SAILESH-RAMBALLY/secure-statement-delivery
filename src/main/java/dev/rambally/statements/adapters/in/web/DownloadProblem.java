package dev.rambally.statements.adapters.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The fixed error bodies of the public download endpoint. They are literal strings, not serialised
 * objects, so every failure reason produces byte-identical output and nothing request-specific can leak.
 */
public final class DownloadProblem {

    public static final String NOT_FOUND_BODY = "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,"
            + "\"detail\":\"Statement not available\",\"instance\":\"/download\"}";
    public static final String METHOD_NOT_ALLOWED_BODY = "{\"type\":\"about:blank\",\"title\":\"Method Not Allowed\","
            + "\"status\":405,\"detail\":\"Method Not Allowed\",\"instance\":\"/download\"}";
    public static final String SERVICE_UNAVAILABLE_BODY = "{\"type\":\"about:blank\",\"title\":\"Service Unavailable\","
            + "\"status\":503,\"detail\":\"Try again shortly\",\"instance\":\"/download\"}";

    private DownloadProblem() {
    }

    public static ResponseEntity<String> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(NOT_FOUND_BODY);
    }

    public static ResponseEntity<String> methodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.ALLOW, "GET, HEAD")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(METHOD_NOT_ALLOWED_BODY);
    }
}
