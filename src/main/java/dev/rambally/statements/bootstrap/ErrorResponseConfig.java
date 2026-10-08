package dev.rambally.statements.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import dev.rambally.statements.adapters.in.web.DownloadProblem;

import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

/**
 * The last two places a request path could leak back to the caller: Spring Boot's default error page, and
 * requests Spring Security's firewall rejects before they reach any controller (for example a ';' in the
 * path). Both now answer with the same fixed problem bodies as the rest of the API.
 */
@Configuration
public class ErrorResponseConfig {

    @Bean
    ErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
                Object status = super.getErrorAttributes(request, ErrorAttributeOptions.defaults()).get("status");
                HttpStatus resolved = status instanceof Integer code ? HttpStatus.resolve(code) : null;
                String phrase = resolved != null ? resolved.getReasonPhrase() : "Request failed";
                Object uri = request.getAttribute("jakarta.servlet.error.request_uri", RequestAttributes.SCOPE_REQUEST);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("type", "about:blank");
                body.put("title", phrase);
                body.put("status", status);
                body.put("detail", phrase);
                body.put("instance", String.valueOf(uri).startsWith("/download") ? "/download" : "/api");
                return body;
            }
        };
    }

    @Bean
    RequestRejectedHandler requestRejectedHandler() {
        return (request, response, rejected) -> {
            if (request.getRequestURI() != null && request.getRequestURI().startsWith("/download")) {
                write(response, HttpServletResponse.SC_NOT_FOUND, DownloadProblem.NOT_FOUND_BODY);
            } else {
                write(response, HttpServletResponse.SC_BAD_REQUEST, "{\"type\":\"about:blank\",\"title\":\"Bad Request\","
                        + "\"status\":400,\"detail\":\"Bad Request\",\"instance\":\"/api\"}");
            }
        };
    }

    private static void write(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(body);
    }
}
