package net.minegasm.config;

/**
 * Marker for the immutable config value types that Gson (de)serializes through their single all-args
 * constructor rather than by reflective field assignment. Implemented by every type reachable from
 * {@link HapticConfig} on disk, so {@link ConfigValueTypeAdapterFactory} runs each type's constructor
 * (and thus its validation and defaults) on load. Enums and utility classes in this package are not
 * config values and do not implement it.
 */
interface ConfigValue {
}
