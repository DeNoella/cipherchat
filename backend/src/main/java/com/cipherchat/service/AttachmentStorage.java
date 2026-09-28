package com.cipherchat.service;

import com.cipherchat.config.AppProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores encrypted attachment blobs on local disk, named by server-generated UUID only (so no
 * client-controlled path ever reaches the filesystem). In production this would be object storage.
 */
@Component
public class AttachmentStorage {

    private final Path root;

    public AttachmentStorage(AppProperties properties) {
        this.root = Path.of(properties.attachments().dir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create attachment directory " + root, e);
        }
    }

    public void store(UUID id, InputStream content) throws IOException {
        Path target = pathFor(id);
        Path temp = Files.createTempFile(root, "upload-", ".part");
        try {
            Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public Optional<Resource> load(UUID id) {
        Path path = pathFor(id);
        return Files.isRegularFile(path) ? Optional.of(new FileSystemResource(path)) : Optional.empty();
    }

    public void delete(UUID id) {
        try {
            Files.deleteIfExists(pathFor(id));
        } catch (IOException ignored) {
            // Best effort; an orphaned blob is unreadable ciphertext.
        }
    }

    private Path pathFor(UUID id) {
        return root.resolve(id + ".pgp");
    }
}
