package dev.rambally.statements.adapters.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import dev.rambally.statements.application.contract.StatementStorageContract;
import dev.rambally.statements.application.port.out.StatementStorage;
import dev.rambally.statements.domain.StorageKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemStatementStorageTest extends StatementStorageContract {

    @TempDir
    Path root;

    private FilesystemStatementStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FilesystemStatementStorage(root);
    }

    @Override
    protected StatementStorage storage() {
        return storage;
    }

    @Test
    void creates_year_month_directories_and_leaves_no_temp_file_behind() throws IOException {
        StorageKey key = key();

        storage.write(key, new byte[] {1, 2, 3});

        Path file = root.resolve(key.value());
        assertThat(file).exists();
        assertThat(file.getParent()).isEqualTo(root.resolve("2026/10"));
        try (Stream<Path> all = Files.walk(root)) {
            assertThat(all.filter(Files::isRegularFile)).containsExactly(file);
        }
    }

    @Test
    void refuses_to_resolve_a_path_outside_the_root() {
        // StorageKey already forbids traversal; the adapter re-checks the resolved path as defence in depth.
        assertThatThrownBy(() -> storage.resolve("../../escape.bin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolve("/etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.resolve("2026/10/x.bin")).isEqualTo(root.resolve("2026/10/x.bin").normalize());
    }

    @Test
    void root_is_created_on_first_use_if_missing() {
        Path nested = root.resolve("deeper/still");

        new FilesystemStatementStorage(nested).write(key(), new byte[] {1});

        assertThat(nested).isDirectory();
    }
}
