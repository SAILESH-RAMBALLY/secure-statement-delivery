package dev.rambally.statements.adapters.in.web;

import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns value-object construction from client input into an explicit 400. Only input parsed at the HTTP
 * edge goes through here; an IllegalArgumentException anywhere else is a server-side defect and is reported
 * as a 500, never blamed on the client.
 */
final class RequestInputs {

    private RequestInputs() {
    }

    static <T> T parse(Supplier<T> constructor) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request");
        }
    }

    static ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
