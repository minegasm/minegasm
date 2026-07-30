package net.minegasm.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads scene packs from a folder into a {@link PackRegistry} (brief 0003 §2.5, §2.6). Each
 * {@code .json} file is parsed independently and fails closed on its own: a malformed or hostile file
 * is recorded as an error and skipped, never aborting the load of the others. A missing folder is not
 * an error, it just yields an empty registry.
 *
 * <p>Files are processed in name order for determinism, and a duplicate pack id keeps the first file
 * and reports the rest, so the loaded set does not depend on directory iteration order.
 */
public final class PackLoader {

    private final ScenePackCodec codec = new ScenePackCodec();

    public Result loadDirectory(Path dir) {
        PackRegistry registry = new PackRegistry();
        List<String> errors = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return new Result(registry, errors);
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (IOException listing) {
            errors.add("could not list " + dir + ": " + listing.getMessage());
            return new Result(registry, errors);
        }

        for (Path file : files) {
            String name = file.getFileName().toString();
            try {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                ScenePack pack = codec.fromJson(text);
                if (registry.find(pack.packId()).isPresent()) {
                    errors.add(name + ": duplicate pack id '" + pack.packId() + "', ignored");
                    continue;
                }
                registry.register(pack);
            } catch (IOException unreadable) {
                errors.add(name + ": could not read (" + unreadable.getMessage() + ")");
            } catch (RuntimeException invalid) {
                // PackFormatException and anything else the codec throws: isolate this one file.
                errors.add(name + ": " + invalid.getMessage());
            }
        }
        return new Result(registry, errors);
    }

    /** The loaded registry plus a human-readable error per file that failed. */
    public static final class Result {
        private final PackRegistry registry;
        private final List<String> errors;

        Result(PackRegistry registry, List<String> errors) {
            this.registry = registry;
            this.errors = java.util.Collections.unmodifiableList(new ArrayList<>(errors));
        }

        public PackRegistry registry() {
            return registry;
        }

        public List<String> errors() {
            return errors;
        }

        public int loaded() {
            return registry.size();
        }
    }
}
