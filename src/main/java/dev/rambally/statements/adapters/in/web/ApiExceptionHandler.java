package dev.rambally.statements.adapters.in.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import dev.rambally.statements.domain.exception.DomainException;
import dev.rambally.statements.domain.exception.DuplicateStatementException;
import dev.rambally.statements.domain.exception.DuplicateTokenHashException;
import dev.rambally.statements.domain.exception.ForbiddenException;
import dev.rambally.statements.domain.exception.IntegrityException;
import dev.rambally.statements.domain.exception.InvalidStatementException;
import dev.rambally.statements.domain.exception.LinkNotFoundException;
import dev.rambally.statements.domain.exception.LinkNotRedeemableException;
import dev.rambally.statements.domain.exception.StatementNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * One place that decides HTTP status for every failure, and one invariant: no error body ever contains
 * client input. Spring's defaults echo the request path in {@code instance} and in details such as
 * "No static resource /download/<token>"; on this service a path may carry a download token, so
 * {@code instance} is normalised to a fixed prefix and {@code detail} to a fixed phrase.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    static final URI API_INSTANCE = URI.create("/api");
    static final URI DOWNLOAD_INSTANCE = URI.create("/download");

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
        return switch (ex) {
            case InvalidStatementException e -> problem(statusFor(e.reason()), "Statement rejected: " + e.reason(), request);
            case DuplicateStatementException e -> problem(HttpStatus.CONFLICT,
                    "A statement for this customer, account and period already exists", request);
            case StatementNotFoundException e -> problem(HttpStatus.NOT_FOUND, "Statement not found", request);
            case LinkNotFoundException e -> problem(HttpStatus.NOT_FOUND, "Link not found", request);
            case ForbiddenException e -> problem(HttpStatus.FORBIDDEN, "Insufficient privileges", request);
            case LinkNotRedeemableException e -> problem(HttpStatus.NOT_FOUND, "Statement not available", request);
            case IntegrityException e -> {
                log.error("integrity failure surfaced to the API: {}", e.getMessage());
                yield problem(HttpStatus.INTERNAL_SERVER_ERROR, "Statement could not be processed", request);
            }
            case DuplicateTokenHashException e -> problem(HttpStatus.INTERNAL_SERVER_ERROR, "Please retry", request);
        };
    }

    /**
     * A body that claims to be multipart but can't be parsed is the client's mistake, not ours. On the public
     * download path it gets the same constant 404 as everything else there.
     */
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<?> handleBadMultipart(MultipartException ex, HttpServletRequest request) {
        if (DOWNLOAD_INSTANCE.equals(instanceFor(request.getRequestURI()))) {
            return DownloadProblem.notFound();
        }
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Invalid request", request));
    }

    /** The database is unreachable or overloaded: a capacity problem the client can retry, not a defect. */
    @ExceptionHandler({CannotGetJdbcConnectionException.class, TransientDataAccessException.class})
    ResponseEntity<ProblemDetail> handleDatabaseUnavailable(Exception ex, HttpServletRequest request) {
        log.warn("database unavailable: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Try again shortly", request));
    }

    /**
     * Anything not mapped above is a server-side defect. The full exception is logged at ERROR for operators;
     * the client gets a fixed 500 body that contains nothing from the exception or the request.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled {} while serving {} {}", ex.getClass().getSimpleName(), request.getMethod(),
                instanceFor(request.getRequestURI()), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Request failed", request);
    }

    @ExceptionHandler(InvalidPrincipalException.class)
    ResponseEntity<ProblemDetail> handleInvalidPrincipal(InvalidPrincipalException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problem(HttpStatus.UNAUTHORIZED, "Authentication required", request));
    }

    /**
     * Every Spring-handled exception (404, 405, 400, 413, ...) ends here with its final ProblemDetail body,
     * which is sanitised before it leaves. (handleExceptionInternal sees a null body for ErrorResponse
     * exceptions, so this is the hook that reliably observes the body.)
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail pd) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            String phrase = status != null ? status.getReasonPhrase() : "Request failed";
            pd.setTitle(phrase);
            pd.setDetail(phrase);
            pd.setInstance(instanceFor(request));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static HttpStatus statusFor(InvalidStatementException.Reason reason) {
        return switch (reason) {
            case NOT_PDF -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case TOO_LARGE -> HttpStatus.valueOf(413);
            case EMPTY, BAD_PERIOD -> HttpStatus.BAD_REQUEST;
        };
    }

    private static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setInstance(instanceFor(request.getRequestURI()));
        return pd;
    }

    private static URI instanceFor(WebRequest request) {
        if (request instanceof ServletWebRequest servlet) {
            return instanceFor(servlet.getRequest().getRequestURI());
        }
        return API_INSTANCE;
    }

    static URI instanceFor(String path) {
        return path != null && path.startsWith("/download") ? DOWNLOAD_INSTANCE : API_INSTANCE;
    }
}
