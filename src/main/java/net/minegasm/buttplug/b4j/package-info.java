/**
 * A {@link net.minegasm.buttplug.HapticProvider} backed by the
 * <a href="https://github.com/blackspherefollower/buttplug4j">buttplug4j</a> client library
 * (v4 feature-based spec, artifact
 * {@code io.github.blackspherefollower:buttplug4j.connectors.jetty.websocket.client:4.0.278}).
 * This is the default backend for talking to Intiface; the dependency-free
 * {@link net.minegasm.buttplug.ButtplugProvider} (JDK WebSocket + Gson) remains as a fallback and as
 * the in-process test backend.
 *
 * <p><strong>Build note:</strong> compiled only by the Gradle build (it depends on buttplug4j, which
 * pulls in Jetty + Jackson), not by the standalone JDK core harness in {@code .localbuild}. The
 * buttplug4j client-side API used here (feature range getters in particular) should be confirmed
 * against the pinned {@code 4.0.278} artifact at kickoff; the mapping is isolated in
 * {@code B4jDeviceMapper} for exactly that reason.
 */
package net.minegasm.buttplug.b4j;
