package net.minegasm.pack;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Directory loading: valid packs load, a bad file is isolated, missing dir is empty, dups reported. */
class PackLoaderTest {

    @TempDir
    Path dir;

    private final PackLoader loader = new PackLoader();
    private final ScenePackCodec codec = new ScenePackCodec();

    @Test
    void loadsValidPacksAndIsolatesBadOnes() throws IOException {
        write("good.json", codec.toJson(pack("good.pack")));
        Files.write(dir.resolve("bad.json"), "{ not json".getBytes(StandardCharsets.UTF_8));

        PackLoader.Result result = loader.loadDirectory(dir);

        assertEquals(1, result.loaded(), "the good pack loads despite the bad one");
        assertTrue(result.registry().find("good.pack").isPresent());
        assertEquals(1, result.registry().all().size(), "listing shows exactly the loaded pack");
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("bad.json"), "the bad file is named in the error");
    }

    @Test
    void missingDirectoryYieldsEmptyRegistryWithNoError() {
        PackLoader.Result result = loader.loadDirectory(dir.resolve("does-not-exist"));
        assertTrue(result.registry().isEmpty());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void duplicatePackIdKeepsFirstAndReportsTheRest() throws IOException {
        write("a.json", codec.toJson(pack("dup")));
        write("b.json", codec.toJson(pack("dup")));

        PackLoader.Result result = loader.loadDirectory(dir);

        assertEquals(1, result.loaded());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("duplicate"));
    }

    @Test
    void aFilePackCannotClaimAReservedBuiltInId() throws IOException {
        write("sneaky.json", codec.toJson(pack("balanced"))); // tries to shadow the built-in Balanced

        PackLoader.Result result = loader.loadDirectory(dir);

        assertTrue(result.registry().isEmpty(), "a pack claiming a built-in id is not loaded");
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("reserved"), "the collision is reported");
    }

    @Test
    void anOversizedPackFileIsRejectedBeforeReading() throws IOException {
        byte[] big = new byte[(1 << 20) + 16]; // just over the 1 MiB cap
        java.util.Arrays.fill(big, (byte) ' ');
        Files.write(dir.resolve("huge.json"), big);

        PackLoader.Result result = loader.loadDirectory(dir);

        assertTrue(result.registry().isEmpty(), "an oversized file is not loaded");
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("too large"), "the size limit is reported");
    }

    private void write(String name, String json) throws IOException {
        Files.write(dir.resolve(name), json.getBytes(StandardCharsets.UTF_8));
    }

    private static ScenePack pack(String id) {
        LayerTemplate layer = new LayerTemplate("l", HapticRole.IMPACT,
                new HapticPrimitive.Impulse(0.5f, 200, 10, 40), Collections.emptySet(),
                DeliveryMode.ALL_COMPATIBLE, CouplingMode.MAX, 0, 0, 250, null);
        SceneTemplate scene = new SceneTemplate(100, 250, null, Collections.singletonList(layer));
        return new ScenePack(ScenePack.SCHEMA_VERSION, id, "", "", "",
                Collections.singletonList(new PackTrigger(GameEventKind.HURT, scene)));
    }
}
