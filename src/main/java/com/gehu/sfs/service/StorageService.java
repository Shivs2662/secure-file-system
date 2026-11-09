package com.gehu.sfs.service;

import com.gehu.sfs.config.SfsProperties;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/** Real bytes live on the server's disk: storage/{fileId}/v{version}. The block map in MySQL is the OS *simulation*. */
@Service
public class StorageService {
    private final Path root;
    public StorageService(SfsProperties props) { this.root = Paths.get(props.storageDir).toAbsolutePath().normalize(); }

    private Path path(int fileId, int version) { return root.resolve(String.valueOf(fileId)).resolve("v" + version); }

    public void write(int fileId, int version, byte[] data) {
        try {
            Path p = path(fileId, version);
            Files.createDirectories(p.getParent());
            Files.write(p, data);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public byte[] read(int fileId, int version) {
        try { return Files.readAllBytes(path(fileId, version)); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public void deleteAll(int fileId) {
        Path dir = root.resolve(String.valueOf(fileId));
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) { }
    }
}
