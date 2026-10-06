package dev.rambally.statements.adapters.in.web;

import java.net.URI;
import java.util.List;

import dev.rambally.statements.adapters.in.web.dto.IssueLinkResponse;
import dev.rambally.statements.adapters.in.web.dto.StatementSummaryResponse;
import dev.rambally.statements.application.Principal;
import dev.rambally.statements.application.port.in.IssueDownloadLinkUseCase;
import dev.rambally.statements.application.port.in.IssueLinkCommand;
import dev.rambally.statements.application.port.in.IssuedLink;
import dev.rambally.statements.application.port.in.ListStatementsUseCase;
import dev.rambally.statements.domain.StatementId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/statements", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Statements", description = "A customer's own statements and download links")
public class StatementController {

    private final ListStatementsUseCase list;
    private final IssueDownloadLinkUseCase issue;

    public StatementController(ListStatementsUseCase list, IssueDownloadLinkUseCase issue) {
        this.list = list;
        this.issue = issue;
    }

    @Operation(summary = "List my statements", description = "Newest period first. Identity comes from the JWT subject; "
            + "a customerId query parameter is rejected rather than ignored.")
    @GetMapping
    public List<StatementSummaryResponse> list(Principal actor,
            @RequestParam(name = "customerId", required = false) String customerId) {
        if (customerId != null) {
            throw new IllegalArgumentException("customerId is derived from the token and cannot be supplied");
        }
        return list.listFor(actor).stream().map(StatementSummaryResponse::of).toList();
    }

    @Operation(summary = "Issue a download link for one of my statements",
            description = "Returns an absolute URL containing a single-use, time-limited token. This response is the "
                    + "only time the token is visible; store the URL, not the link id, if you intend to download.")
    @ApiResponse(responseCode = "201", description = "Link issued")
    @ApiResponse(responseCode = "404", description = "Statement unknown or not yours (indistinguishable)")
    @PostMapping("/{statementId}/links")
    public ResponseEntity<IssueLinkResponse> issueLink(@PathVariable("statementId") String statementId, Principal actor) {
        IssuedLink issued = issue.issue(new IssueLinkCommand(StatementId.of(statementId), actor));
        return ResponseEntity.created(URI.create("/api/links/" + issued.linkId()))
                .body(IssueLinkResponse.of(issued));
    }
}
