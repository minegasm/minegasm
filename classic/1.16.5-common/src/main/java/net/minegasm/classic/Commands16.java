package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;

/**
 * Bridges the 1.16.5 brigadier command registration (which differs per loader) to the shared,
 * Minecraft-free {@link ClassicCommands} parser. Both loaders register {@code /minegasm} (and
 * {@code /mg}) with a greedy string argument and hand the raw text here; the parsing and the actions
 * are identical across loaders. Feedback goes to chat through {@link Chat16}.
 */
public final class Commands16 {

    /** Command output routed to Minegasm chat lines; safe to call from any thread. */
    public static final ClassicCommands.Feedback FEEDBACK = new ClassicCommands.Feedback() {
        @Override
        public void info(String message) {
            Chat16.send(message);
        }

        @Override
        public void error(String message) {
            Chat16.send("§c" + message);
        }
    };

    private Commands16() {
    }

    /** Split a raw argument string (may be empty/null) and dispatch through the shared parser. */
    public static void run(MinegasmClient client, long gameTick, String argsString) {
        String[] args = (argsString == null || argsString.trim().isEmpty())
                ? new String[0] : argsString.trim().split("\\s+");
        ClassicCommands.dispatch(client, gameTick, args, FEEDBACK);
    }
}
