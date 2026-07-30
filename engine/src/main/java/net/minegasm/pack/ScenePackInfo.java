package net.minegasm.pack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A read-only summary of a loaded pack for display and selection in a UI or command (brief 0003 §2.7):
 * its id (the value written to the config's {@code recipePack} selector), plus author-facing metadata.
 * Kept separate from {@link ScenePack} so the UI never touches scene internals.
 */
public final class ScenePackInfo {

    private final String id;
    private final String name;
    private final String author;
    private final String description;
    private final int triggerCount;

    public ScenePackInfo(String id, String name, String author, String description, int triggerCount) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.triggerCount = triggerCount;
    }

    public static ScenePackInfo of(ScenePack pack) {
        return new ScenePackInfo(pack.packId(), pack.name(), pack.author(), pack.description(),
                pack.triggers().size());
    }

    /** All loaded packs as display summaries, in load order. */
    public static List<ScenePackInfo> from(PackRegistry registry) {
        List<ScenePackInfo> out = new ArrayList<>();
        for (ScenePack pack : registry.all()) {
            out.add(of(pack));
        }
        return Collections.unmodifiableList(out);
    }

    public String id() {
        return id;
    }

    /** Display name, or the id when the pack left it blank. */
    public String displayName() {
        return name.isEmpty() ? id : name;
    }

    public String name() {
        return name;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    public int triggerCount() {
        return triggerCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScenePackInfo)) {
            return false;
        }
        ScenePackInfo other = (ScenePackInfo) o;
        return triggerCount == other.triggerCount
                && Objects.equals(id, other.id)
                && Objects.equals(name, other.name)
                && Objects.equals(author, other.author)
                && Objects.equals(description, other.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, author, description, triggerCount);
    }

    @Override
    public String toString() {
        return "ScenePackInfo[id=" + id + ", name=" + name + ", author=" + author
                + ", triggerCount=" + triggerCount + "]";
    }
}
