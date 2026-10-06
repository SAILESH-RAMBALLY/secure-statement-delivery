package dev.rambally.statements.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;

import dev.rambally.statements.application.crypto.AesGcmEnvelopeCipher;
import dev.rambally.statements.application.fakes.FakeKeyProvider;
import dev.rambally.statements.application.fakes.FixedTokenGenerator;
import dev.rambally.statements.application.fakes.InMemoryDownloadLinkRepository;
import dev.rambally.statements.application.fakes.InMemoryStatementRepository;
import dev.rambally.statements.application.fakes.InMemoryStatementStorage;
import dev.rambally.statements.application.fakes.RecordingAuditLog;
import dev.rambally.statements.application.fakes.ThrowingAuditLog;
import dev.rambally.statements.application.port.in.RequestContext;
import dev.rambally.statements.application.port.in.StatementDownload;
import dev.rambally.statements.application.port.in.UploadStatementCommand;
import dev.rambally.statements.application.port.out.AuditLog;
import dev.rambally.statements.domain.AccountNumber;
import dev.rambally.statements.domain.AuditEvent;
import dev.rambally.statements.domain.CustomerId;
import dev.rambally.statements.domain.DownloadLink;
import dev.rambally.statements.domain.Fixtures;
import dev.rambally.statements.domain.LinkId;
import dev.rambally.statements.domain.LinkPolicy;
import dev.rambally.statements.domain.LinkToken;
import dev.rambally.statements.domain.RedemptionOutcome;
import dev.rambally.statements.domain.Statement;
import dev.rambally.statements.domain.StatementId;
import dev.rambally.statements.domain.StatementPeriod;
import dev.rambally.statements.domain.exception.LinkNotRedeemableException;
import dev.rambally.statements.support.TestPdfs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RedeemDownloadLinkServiceTest {

    private static final RequestContext CTX = new RequestContext("203.0.113.7", "curl/8");
    private static final LinkToken TOKEN = FixedTokenGenerator.tokenNumber(4);
    private static final LinkPolicy POLICY = new LinkPolicy(Duration.ofHours(1), 1);

    private final InMemoryStatementRepository statements = new InMemoryStatementRepository();
    private final InMemoryDownloadLinkRepository links = new InMemoryDownloadLinkRepository();
    private final InMemoryStatementStorage storage = new InMemoryStatementStorage();
    private final FakeKeyProvider keyProvider = new FakeKeyProvider();
    private final AesGcmEnvelopeCipher cipher = new AesGcmEnvelopeCipher();
    private final RecordingAuditLog audit = new RecordingAuditLog();
    private final Clock clock = Clock.fixed(Fixtures.NOW, ZoneOffset.UTC);

    private Statement statement;
    private byte[] pdf;

    private RedeemDownloadLinkService service(AuditLog auditLog, Clock at) {
        return new RedeemDownloadLinkService(links, statements, storage, keyProvider, cipher, auditLog, at);
    }

    private RedeemDownloadLinkService service() {
        return service(audit, clock);
    }

    @BeforeEach
    void seedStatementThroughTheRealUploadPath() {
        pdf = TestPdfs.minimal();
        UploadStatementService upload = new UploadStatementService(statements, storage, keyProvider, cipher,
                new StatementSizePolicy(1024 * 1024), clock);
        StatementId id = upload.upload(new UploadStatementCommand(new CustomerId("C-1001"), new AccountNumber("1234567890"),
                StatementPeriod.parse("2026-09"), pdf, new Principal(new CustomerId("ops-admin"), true)));
        statement = statements.findById(id).orElseThrow();
    }

    private DownloadLink issue(Consumer<DownloadLink> noop) {
        DownloadLink link = DownloadLink.issue(LinkId.newId(), statement, TOKEN.hash(), POLICY, Fixtures.NOW);
        links.save(link);
        return link;
    }

    private DownloadLink issue() {
        return issue(l -> { });
    }

    @Test
    void valid_token_returns_decrypted_pdf_with_derived_file_name_and_audits_SUCCESS() {
        DownloadLink link = issue();

        StatementDownload download = service().redeem(TOKEN.value(), CTX);

        assertThat(download.pdf()).isEqualTo(pdf);
        assertThat(download.fileName()).isEqualTo("statement-1234567890-2026-09.pdf");
        assertThat(links.findById(link.id()).orElseThrow().downloadCount()).isEqualTo(1);
        assertThat(audit.redemptionOutcomes()).containsExactly(RedemptionOutcome.SUCCESS);
        AuditEvent event = audit.events().getFirst();
        assertThat(event.linkId()).isEqualTo(link.id());
        assertThat(event.statementId()).isEqualTo(statement.id());
        assertThat(event.customerId()).isEqualTo(new CustomerId("C-1001"));
        assertThat(event.clientIp()).isEqualTo("203.0.113.7");
        assertThat(event.userAgent()).isEqualTo("curl/8");
        assertThat(event.tokenHashPrefix()).isEqualTo(TOKEN.hash().prefix());
    }

    @Test
    void unknown_token_audits_UNKNOWN_TOKEN_with_hash_prefix_and_no_ids() {
        assertThatThrownBy(() -> service().redeem(TOKEN.value(), CTX))
                .isInstanceOf(LinkNotRedeemableException.class)
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.UNKNOWN_TOKEN);

        AuditEvent event = audit.events().getFirst();
        assertThat(event.outcome()).isEqualTo(RedemptionOutcome.UNKNOWN_TOKEN);
        assertThat(event.tokenHashPrefix()).isEqualTo(TOKEN.hash().prefix());
        assertThat(event.linkId()).isNull();
        assertThat(event.clientIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void malformed_token_audits_MALFORMED_TOKEN_without_touching_the_repository() {
        assertThatThrownBy(() -> service().redeem("not-a-token", CTX))
                .isInstanceOf(LinkNotRedeemableException.class)
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.MALFORMED_TOKEN);
        assertThatThrownBy(() -> service().redeem(null, CTX))
                .isInstanceOf(LinkNotRedeemableException.class);

        assertThat(audit.redemptionOutcomes()).containsExactly(RedemptionOutcome.MALFORMED_TOKEN, RedemptionOutcome.MALFORMED_TOKEN);
        assertThat(audit.events().getFirst().tokenHashPrefix()).isNull();
    }

    @Test
    void expired_link_audits_EXPIRED_and_is_not_consumed() {
        DownloadLink link = issue();
        Clock later = Clock.fixed(link.expiresAt(), ZoneOffset.UTC);

        assertThatThrownBy(() -> service(audit, later).redeem(TOKEN.value(), CTX))
                .isInstanceOf(LinkNotRedeemableException.class)
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.EXPIRED);

        assertThat(links.findById(link.id()).orElseThrow().downloadCount()).isZero();
        assertThat(audit.redemptionOutcomes()).containsExactly(RedemptionOutcome.EXPIRED);
        assertThat(audit.events().getFirst().linkId()).isEqualTo(link.id());
    }

    @Test
    void revoked_link_audits_REVOKED() {
        DownloadLink link = issue();
        links.save(link.revoke(Fixtures.NOW.plusSeconds(1)));

        assertThatThrownBy(() -> service().redeem(TOKEN.value(), CTX))
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.REVOKED);
    }

    @Test
    void exhausted_link_audits_EXHAUSTED() {
        DownloadLink link = issue();
        links.save(link.withDownloadCount(1));

        assertThatThrownBy(() -> service().redeem(TOKEN.value(), CTX))
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.EXHAUSTED);
    }

    @Test
    void lost_race_when_try_consume_returns_false_audits_LOST_RACE() {
        issue();
        links.failNextConsume();

        assertThatThrownBy(() -> service().redeem(TOKEN.value(), CTX))
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.LOST_RACE);
    }

    @Test
    void integrity_failure_audits_INTEGRITY_FAILED_and_does_not_consume() {
        DownloadLink link = issue();
        storage.corrupt(statement.storageKey());

        assertThatThrownBy(() -> service().redeem(TOKEN.value(), CTX))
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.INTEGRITY_FAILED);

        assertThat(links.findById(link.id()).orElseThrow().downloadCount()).isZero();
    }

    @Test
    void missing_ciphertext_audits_STORAGE_MISSING_and_does_not_consume() {
        DownloadLink link = issue();
        storage.delete(statement.storageKey());

        assertThatThrownBy(() -> service().redeem(TOKEN.value(), CTX))
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.STORAGE_MISSING);

        assertThat(links.findById(link.id()).orElseThrow().downloadCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = RedemptionOutcome.class, names = {"SUCCESS"}, mode = EnumSource.Mode.EXCLUDE)
    void every_failure_is_the_same_exception_type(RedemptionOutcome outcome) {
        assertThat(new LinkNotRedeemableException(outcome)).isInstanceOf(LinkNotRedeemableException.class);
        assertThat(new LinkNotRedeemableException(outcome).outcome()).isEqualTo(outcome);
    }

    @Test
    void audit_log_failure_does_not_change_the_response_on_success_or_failure() {
        issue();
        RedeemDownloadLinkService service = service(new ThrowingAuditLog(), clock);

        assertThat(service.redeem(TOKEN.value(), CTX).pdf()).isEqualTo(pdf);
        assertThatThrownBy(() -> service.redeem(TOKEN.value(), CTX)).isInstanceOf(LinkNotRedeemableException.class);
    }

    @Test
    void a_single_use_link_can_be_redeemed_exactly_once_then_reports_EXHAUSTED() {
        issue();

        service().redeem(TOKEN.value(), CTX);

        assertThatThrownBy(() -> service().redeem(TOKEN.value(), CTX))
                .extracting(e -> ((LinkNotRedeemableException) e).outcome())
                .isEqualTo(RedemptionOutcome.EXHAUSTED);
        assertThat(audit.redemptionOutcomes()).isEqualTo(List.of(RedemptionOutcome.SUCCESS, RedemptionOutcome.EXHAUSTED));
    }

    @Test
    void request_context_tolerates_missing_ip_and_user_agent() {
        issue();

        service().redeem(TOKEN.value(), new RequestContext(null, null));

        assertThat(audit.events().getFirst().clientIp()).isNull();
    }

    @Test
    void clock_boundary_one_second_before_expiry_is_still_redeemable() {
        DownloadLink link = issue();
        Instant justBefore = link.expiresAt().minusSeconds(1);

        assertThat(service(audit, Clock.fixed(justBefore, ZoneOffset.UTC)).redeem(TOKEN.value(), CTX).pdf()).isEqualTo(pdf);
    }
}
