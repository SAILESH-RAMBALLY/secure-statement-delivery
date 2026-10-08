package dev.rambally.statements.adapters.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Status;

class StorageHealthIndicatorTest {

    @TempDir
    Path root;

    @Test
    void writable_storage_is_up() {
        assertThat(new StorageHealthIndicator(root.resolve("statements")).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void storage_that_cannot_be_written_is_down() throws Exception {
        Path readOnly = Files.createDirectory(root.resolve("locked"));
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assertThat(new StorageHealthIndicator(readOnly).health().getStatus()).isEqualTo(Status.DOWN);
        } finally {
            Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void a_root_that_is_a_file_is_down() throws Exception {
        Path file = Files.createFile(root.resolve("not-a-directory"));

        assertThat(new StorageHealthIndicator(file).health().getStatus()).isEqualTo(Status.DOWN);
    }
}
