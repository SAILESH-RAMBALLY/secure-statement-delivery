package dev.rambally.statements.adapters.in.web;

import jakarta.servlet.http.HttpServletRequest;

import dev.rambally.statements.application.port.in.RedeemDownloadLinkUseCase;
import dev.rambally.statements.application.port.in.RequestContext;
import dev.rambally.statements.application.port.in.StatementDownload;
import dev.rambally.statements.domain.exception.LinkNotRedeemableException;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public capability URL. The token in the path is the only credential. This endpoint can answer
 * 200, 404, 405 or 503 and nothing else; every failure of any kind is the same 404 body. Refusals are
 * recorded in the audit table, not in logs; only infrastructure faults are logged, without the path.
 */
@RestController
@RequestMapping("/download")
@Tag(name = "Download", description = "Public, token-authenticated statement download")
public class DownloadController {

    private static final Logger log = LoggerFactory.getLogger(DownloadController.class);

    private final RedeemDownloadLinkUseCase redeem;

    public DownloadController(RedeemDownloadLinkUseCase redeem) {
        this.redeem = redeem;
    }

    @Operation(summary = "Download a statement with a link token",
            description = "Consumes one use of the link. Every failure (unknown, expired, revoked, exhausted, "
                    + "tampered) returns an identical 404; the real reason is recorded in the audit trail only.")
    @ApiResponse(responseCode = "200", description = "The PDF, as an attachment")
    @ApiResponse(responseCode = "404", description = "Statement not available (constant body)")
    @ApiResponse(responseCode = "503", description = "Too many downloads in flight; retry shortly")
    @GetMapping(path = "/{token}")
    public ResponseEntity<byte[]> download(@PathVariable("token") String token, HttpServletRequest request) {
        StatementDownload download = redeem.redeem(token,
                new RequestContext(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT)));
        byte[] pdf = download.pdf(); // transfer object: no copy
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Frame-Options", "DENY")
                .body(pdf);
    }

    /** Spring would route HEAD to the GET handler and burn a single-use link on a download manager's probe. */
    @Hidden
    @RequestMapping(path = "/{token}", method = RequestMethod.HEAD)
    public ResponseEntity<String> head() {
        return DownloadProblem.notFound();
    }

    @Hidden
    @RequestMapping(path = "/{token}", method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE,
            RequestMethod.PATCH, RequestMethod.OPTIONS})
    public ResponseEntity<String> otherMethods() {
        return DownloadProblem.methodNotAllowed();
    }

    /** Anything under /download that is not exactly /download/{token} gets the same constant 404. */
    @Hidden
    @RequestMapping("/**")
    public ResponseEntity<String> anythingElse() {
        return DownloadProblem.notFound();
    }

    /**
     * Every failure, expected or not, is the same answer on the wire. Expected refusals are already in the
     * audit trail; anything else is an infrastructure fault and is logged by class only, never with the path.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> anyFailure(Exception ex) {
        Throwable fault = ex instanceof LinkNotRedeemableException refused ? refused.getCause() : ex;
        if (fault != null) {
            log.error("download failed with {}", fault.getClass().getSimpleName(), fault);
        }
        return DownloadProblem.notFound();
    }
}
