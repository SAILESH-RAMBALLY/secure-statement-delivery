package dev.rambally.statements.adapters.out.storage;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** The service is only ready if it can actually store a statement: the storage root must exist and be writable. */
public final class StorageHealthIndicator implements HealthIndicator {

    private final Path root;

    public StorageHealthIndicator(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Health health() {
        try {
            Files.createDirectories(root);
            Path probe = Files.createTempFile(root, ".health", ".tmp");
            Files.delete(probe);
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withDetail("reason", "statement storage is not writable").build();
        }
    }
}
