package dev.rambally.statements.adapters.in.web;

import java.io.IOException;
import java.io.UncheckedIOException;

import dev.rambally.statements.adapters.in.web.dto.UploadResponse;
import dev.rambally.statements.application.Principal;
import dev.rambally.statements.application.port.in.UploadStatementCommand;
import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/statements")
@Tag(name = "Statements (admin)", description = "Back-office ingestion of statement PDFs")
public class AdminStatementController {

    private final UploadStatementUseCase upload;

    public AdminStatementController(UploadStatementUseCase upload) {
        this.upload = upload;
    }

    @Operation(summary = "Upload a customer's statement PDF",
            description = "Encrypts the PDF at rest and records it for the customer. Requires the ADMIN role. "
                    + "The uploaded file name is discarded; downloads use a server-derived name.")
    @ApiResponse(responseCode = "201", description = "Statement stored; the body carries its id")
    @ApiResponse(responseCode = "400", description = "Bad account number or period")
    @ApiResponse(responseCode = "403", description = "Caller lacks the ADMIN role")
    @ApiResponse(responseCode = "409", description = "A statement for this customer, account and period exists")
    @ApiResponse(responseCode = "413", description = "File larger than the configured maximum (10 MiB)")
    @ApiResponse(responseCode = "415", description = "File is not a PDF")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("customerId") String customerId,
            @RequestParam("accountNumber") String accountNumber,
            @RequestParam("period") String period,
            Principal actor) {
        UploadStatementCommand command = new UploadStatementCommand(
                RequestInputs.parse(() -> new CustomerId(customerId)),
                RequestInputs.parse(() -> new AccountNumber(accountNumber)),
                StatementPeriod.parse(period),
                bytesOf(file),
                actor);
        StatementId id = upload.upload(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResponse(id.toString()));
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read uploaded file", e);
        }
    }
}
