package dev.rambally.statements.application;

import java.time.Clock;
import java.time.Instant;

import dev.rambally.statements.application.crypto.AesGcmEnvelopeCipher;
import dev.rambally.statements.application.port.in.UploadStatementCommand;
import dev.rambally.statements.application.port.in.UploadStatementUseCase;
import dev.rambally.statements.application.port.out.KeyProvider;
import dev.rambally.statements.application.port.out.StatementRepository;
import dev.rambally.statements.application.port.out.StatementStorage;
import dev.rambally.statements.domain.PdfDocument;
import dev.rambally.statements.domain.Sha256;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StorageKey;
import dev.rambally.statements.domain.exception.DomainException;
import dev.rambally.statements.domain.exception.ForbiddenException;
import dev.rambally.statements.domain.exception.InvalidStatementException;

/**
 * Validate, encrypt, store, record. Storage is written before the row is inserted and deleted again if
 * the insert fails, so a statement row never points at a missing file.
 */
public final class UploadStatementService implements UploadStatementUseCase {

    private final StatementRepository statements;
    private final StatementStorage storage;
    private final KeyProvider keyProvider;
    private final AesGcmEnvelopeCipher cipher;
    private final StatementSizePolicy sizePolicy;
    private final Clock clock;

    public UploadStatementService(StatementRepository statements, StatementStorage storage, KeyProvider keyProvider,
            AesGcmEnvelopeCipher cipher, StatementSizePolicy sizePolicy, Clock clock) {
        this.statements = statements;
        this.storage = storage;
        this.keyProvider = keyProvider;
        this.cipher = cipher;
        this.sizePolicy = sizePolicy;
        this.clock = clock;
    }

    @Override
    public StatementId upload(UploadStatementCommand command) {
        if (!command.actor().admin()) {
            throw new ForbiddenException("uploading statements requires the ADMIN role");
        }
        PdfDocument pdf = new PdfDocument(command.pdfBytes());
        sizePolicy.check(pdf);
        Instant now = clock.instant();
        if (command.period().isAfterMonthOf(now)) {
            throw new InvalidStatementException(InvalidStatementException.Reason.BAD_PERIOD);
        }

        StatementId id = StatementId.newId();
        StorageKey key = StorageKey.of(id, now);
        AesGcmEnvelopeCipher.Sealed sealed = cipher.seal(pdf, id, keyProvider);
        storage.write(key, sealed.ciphertext());

        Statement statement = new Statement(id, command.customerId(), command.accountNumber(), command.period(),
                pdf.size(), Sha256.of(pdf.bytes()), key, sealed.envelope(), now);
        try {
            statements.save(statement);
        } catch (DomainException definite) {
            // The row was definitely not written (for example a duplicate), so the file must go.
            deleteQuietly(key, definite);
            throw definite;
        } catch (RuntimeException ambiguous) {
            // A dropped connection can fail after the database committed. Only delete the file if the row is
            // really absent; if we can't tell, keep the file, because an orphan is safer than a broken row.
            if (rowCommitted(id)) {
                return id;
            }
            deleteQuietly(key, ambiguous);
            throw ambiguous;
        }
        return id;
    }

    private boolean rowCommitted(StatementId id) {
        try {
            return statements.findById(id).isPresent();
        } catch (RuntimeException cannotTell) {
            return true;
        }
    }

    private void deleteQuietly(StorageKey key, RuntimeException original) {
        try {
            storage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            original.addSuppressed(cleanupFailure); // the original failure is what the caller must see
        }
    }
}
