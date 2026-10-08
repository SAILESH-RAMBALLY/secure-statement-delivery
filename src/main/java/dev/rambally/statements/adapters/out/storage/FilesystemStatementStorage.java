package dev.rambally.statements.adapters.out.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import dev.rambally.statements.application.port.out.StatementStorage;
import dev.rambally.statements.domain.StorageKey;

/**
 * Ciphertext files under a root directory (a Docker volume in the container). Writes go to a temp file
 * then an atomic move, so a crash never leaves a half-written statement at its final path.
 */
public final class FilesystemStatementStorage implements StatementStorage {

    private final Path root;

    public FilesystemStatementStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void write(StorageKey key, byte[] ciphertext) {
        Path target = resolve(key.value());
        try {
            Files.createDirectories(target.getParent());
            Path tmp = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".", ".tmp");
            try {
                try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                    ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer); // a single write may be partial
                    }
                    channel.force(true); // data on disk before the rename becomes visible
                }
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                syncDirectory(target.getParent()); // make the rename itself durable
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not store statement ciphertext", e);
        }
    }

    @Override
    public Optional<byte[]> read(StorageKey key, long expectedLength) {
        Path path = resolve(key.value());
        try {
            if (!Files.isRegularFile(path) || Files.size(path) != expectedLength) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read statement ciphertext", e);
        }
    }

    @Override
    public void delete(StorageKey key) {
        try {
            Files.deleteIfExists(resolve(key.value()));
        } catch (IOException e) {
            throw new UncheckedIOException("could not delete statement ciphertext", e);
        }
    }

    /** On Linux a rename is only durable once its directory is synced. Some platforms can't open a directory. */
    private static void syncDirectory(Path dir) {
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException notSupported) {
            // Windows and some filesystems don't allow this; the file data itself is already forced.
        }
    }

    /** Defence in depth: whatever the key says, the resolved path must stay under the root. */
    Path resolve(String relative) {
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("storage key resolves outside the storage root");
        }
        return resolved;
    }
}
