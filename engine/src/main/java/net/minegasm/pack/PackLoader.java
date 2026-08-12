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

    // Built-in pack names (and their aliases) that a file pack may not claim, or it would silently
    // replace the built-in behavior under the same selection (review P2-7).
    private static final java.util.Set<String> RESERVED_IDS = new java.util.HashSet<>(
            java.util.Arrays.asList("classic", "balanced", "modern"));

    /** Reject a pack file larger than this before reading it into memory (review P2-5). */
    private static final long MAX_PACK_BYTES = 1L << 20; // 1 MiB

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
                long size = Files.size(file);
                if (size > MAX_PACK_BYTES) {
                    errors.add(name + ": too large (" + size + " bytes, limit " + MAX_PACK_BYTES + ")");
                    continue;
                }
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                ScenePack pack = codec.fromJson(text);
                if (isReserved(pack.packId())) {
                    errors.add(name + ": pack id '" + pack.packId()
                            + "' is reserved for a built-in, ignored");
                    continue;
                }
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

    private static boolean isReserved(String id) {
        return id != null && RESERVED_IDS.contains(id.trim().toLowerCase(Locale.ROOT));
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
