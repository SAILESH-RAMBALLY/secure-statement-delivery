package dev.rambally.statements.adapters.in.web;

import dev.rambally.statements.application.Principal;
import dev.rambally.statements.application.port.in.RevokeDownloadLinkUseCase;
import dev.rambally.statements.domain.LinkId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/links")
@Tag(name = "Statements", description = "A customer's own statements and download links")
public class LinkController {

    private final RevokeDownloadLinkUseCase revoke;

    public LinkController(RevokeDownloadLinkUseCase revoke) {
        this.revoke = revoke;
    }

    @Operation(summary = "Revoke a download link", description = "Immediate and idempotent. The owner or an ADMIN may revoke.")
    @ApiResponse(responseCode = "204", description = "Revoked (or already revoked)")
    @ApiResponse(responseCode = "404", description = "Link unknown or not yours (indistinguishable)")
    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> revoke(@PathVariable("linkId") String linkId, Principal actor) {
        revoke.revoke(LinkId.of(linkId), actor);
        return ResponseEntity.noContent().build();
    }
}
