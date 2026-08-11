package net.minegasm.classic;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.config.HapticConfig;
import net.minegasm.device.HapticDevice;
import net.minegasm.client.MinegasmClient;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import java.util.List;
import java.util.Locale;

/**
 * The main in-game control screen for Minecraft 1.7.10, opened from the mods list "Config" button (see
 * {@link ClassicGuiFactory}). It mirrors the modern dashboard: master enable, connect/disconnect,
 * scan/refresh, test, an always-visible stop/resume, a live device list and provider-error history on the
 * right, and buttons through to the {@link ClassicSettingsScreen} and the {@link ClassicLegacyImportScreen}.
 * Actions run live against the shared {@link MinegasmClient}; the deferred knobs live on the settings
 * sub-screen. The adapter toggle switches the Buttplug backend between buttplug4j and the native
 * WebSocket provider (ADR-019), the same control the modern dashboard has.
 *
 * <p>Sibling of the 1.8.9 and 1.12.2 dashboards. On 1.7.10 the GuiScreen callbacks do not throw
 * {@code IOException} and buttons are added straight to {@code buttonList}.
 */
public final class ClassicConfigScreen extends GuiScreen {

    private static final int ID_ENABLED = 1;
    private static final int ID_CONNECT = 2;
    private static final int ID_SCAN = 3;
    private static final int ID_REFRESH = 4;
    private static final int ID_TEST = 5;
    private static final int ID_PANIC = 6;
    private static final int ID_SETTINGS = 7;
    private static final int ID_LEGACY = 8;
    private static final int ID_DONE = 9;
    private static final int ID_CLEAR_ERRORS = 10;
    private static final int ID_CUSTOMIZATION = 11;
    private static final int ID_DEVICE_EDITOR = 12;
    private static final int ID_SCENE_PACKS = 13;
    private static final int ID_ADAPTER = 14;

    private final GuiScreen parent;
    private final MinegasmClient client;

    private GuiButton connectBtn;
    private GuiButton scanBtn;
    private GuiButton refreshBtn;
    private GuiButton testBtn;
    private GuiButton adapterBtn;
    private GuiButton clearErrorsBtn;

    private int leftX;
    private int rightX;
    private int columnWidth;
    private int deviceTop;
    private int deviceHeight;
    private int errorTop;
    private int errorHeight;
    private int deviceScroll;
    private int errorScroll;

    public ClassicConfigScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        int totalWidth = Math.min(width - 16, 420);
        int columnGap = 8;
        columnWidth = (totalWidth - columnGap) / 2;
        leftX = (width - totalWidth) / 2;
        rightX = leftX + columnWidth + columnGap;
        int half = (columnWidth - 4) / 2;
        int h = 20;
        int gap = 24;
        int y = 42;

        deviceTop = 52;
        deviceHeight = 64;
        errorTop = 132;
        errorHeight = Math.max(28, height - 144);

        // Buttplug controls only; master output and stop live on the hub.
        adapterBtn = new GuiButton(ID_ADAPTER, leftX, y, columnWidth, h, adapterLabel());
        buttonList.add(adapterBtn);
        y += gap;
        connectBtn = new GuiButton(ID_CONNECT, leftX, y, columnWidth, h, connectLabel());
        buttonList.add(connectBtn);
        y += gap;
        scanBtn = new GuiButton(ID_SCAN, leftX, y, half, h, scanLabel());
        refreshBtn = new GuiButton(ID_REFRESH, leftX + half + 4, y, half, h, "Refresh");
        buttonList.add(scanBtn);
        buttonList.add(refreshBtn);
        y += gap;
        testBtn = new GuiButton(ID_TEST, leftX, y, columnWidth, h, "Test");
        buttonList.add(testBtn);
        y += gap;
        buttonList.add(new GuiButton(ID_DEVICE_EDITOR, leftX, y, columnWidth, h, "Device editor..."));
        y += gap;
        buttonList.add(new GuiButton(ID_DONE, leftX, height - 24, columnWidth, h, "Done"));

        clearErrorsBtn = new GuiButton(ID_CLEAR_ERRORS, rightX + columnWidth - 44, 117, 44, 14, "Clear");
        buttonList.add(clearErrorsBtn);

        refreshActionButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_CONNECT:
                if (client.isConnected()) {
                    client.disconnect();
                } else {
                    client.connect();
                }
                break;
            case ID_SCAN:
                if (client.status().state() == ConnectionState.SCANNING) {
                    client.stopScanning();
                } else {
                    client.startScanning();
                }
                break;
            case ID_REFRESH:
                client.refreshDevices();
                break;
            case ID_TEST:
                client.testButtplugOutput(0.25f);
                break;
            case ID_CLEAR_ERRORS:
                client.clearErrorHistory();
                break;
            case ID_ADAPTER:
                toggleAdapter();
                break;
            case ID_DEVICE_EDITOR:
                mc.displayGuiScreen(new DeviceEditorScreen(this));
                break;
            case ID_DONE:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
        refreshActionButtons();
    }

    @Override
    public void updateScreen() {
        refreshActionButtons();
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int step = wheel > 0 ? -1 : 1; // wheel up scrolls toward the top of the list
        if (inRect(mx, my, rightX, deviceTop, columnWidth, deviceHeight)) {
            deviceScroll = clampScroll(deviceScroll + step,
                    client.provider().devices().all().size(), deviceCapacity());
        } else if (inRect(mx, my, rightX, errorTop, columnWidth, errorHeight)) {
            errorScroll = clampScroll(errorScroll + step,
                    client.errorHistory().size(), errorCapacity());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        drawCenteredString(fontRendererObj, "Minegasm", width / 2, 12, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "State: " + stateName(), width / 2, 26, 0xA0A0A0);

        int devices = client.provider().devices().all().size();
        drawCenteredString(fontRendererObj, "Devices (" + devices + ")",
                rightX + columnWidth / 2, 42, 0xFFFFFF);
        drawString(fontRendererObj, "Errors (" + client.errorHistory().size() + ")",
                rightX, 120, 0xFFFFFF);

        drawDevicePanel();
        drawErrorPanel();
    }

    @Override
    public void onGuiClosed() {
    }

    private void drawDevicePanel() {
        drawRect(rightX, deviceTop, rightX + columnWidth, deviceTop + deviceHeight, 0x60000000);
        List<HapticDevice> devices = client.provider().devices().all();
        if (devices.isEmpty()) {
            drawString(fontRendererObj, "No devices", rightX + 4, deviceTop + 6, 0x808080);
            return;
        }
        int rowH = 20;
        int capacity = deviceCapacity();
        deviceScroll = clampScroll(deviceScroll, devices.size(), capacity);
        int end = Math.min(devices.size(), deviceScroll + capacity);
        for (int i = deviceScroll; i < end; i++) {
            HapticDevice d = devices.get(i);
            int ry = deviceTop + 2 + (i - deviceScroll) * rowH;
            drawString(fontRendererObj,
                    fontRendererObj.trimStringToWidth(ClassicDeviceFormat.label(d), columnWidth - 10),
                    rightX + 4, ry + 2, 0xFFFFFF);
            drawString(fontRendererObj,
                    fontRendererObj.trimStringToWidth(ClassicDeviceFormat.capabilities(d), columnWidth - 10),
                    rightX + 4, ry + 11, 0xA0A0A0);
        }
        drawScrollbar(deviceTop, deviceHeight, devices.size(), capacity, deviceScroll);
    }

    private void drawErrorPanel() {
        drawRect(rightX, errorTop, rightX + columnWidth, errorTop + errorHeight, 0x60000000);
        List<String> errors = client.errorHistory();
        if (errors.isEmpty()) {
            drawString(fontRendererObj, "No errors", rightX + 4, errorTop + 6, 0x808080);
            return;
        }
        int lineH = 10;
        int capacity = errorCapacity();
        errorScroll = clampScroll(errorScroll, errors.size(), capacity);
        // Newest first, so scroll offset 0 keeps the latest error pinned to the top.
        int shown = Math.min(capacity, errors.size() - errorScroll);
        for (int r = 0; r < shown; r++) {
            int idx = errors.size() - 1 - (errorScroll + r);
            drawString(fontRendererObj,
                    fontRendererObj.trimStringToWidth(errors.get(idx), columnWidth - 10),
                    rightX + 4, errorTop + 2 + r * lineH, 0xFF7777);
        }
        drawScrollbar(errorTop, errorHeight, errors.size(), capacity, errorScroll);
    }

    private int deviceCapacity() {
        return Math.max(1, (deviceHeight - 4) / 20);
    }

    private int errorCapacity() {
        return Math.max(1, (errorHeight - 4) / 10);
    }

    private void drawScrollbar(int top, int height, int total, int capacity, int scroll) {
        if (total <= capacity) {
            return;
        }
        int barX = rightX + columnWidth - 3;
        drawRect(barX, top, barX + 2, top + height, 0x40FFFFFF);
        int thumbH = Math.max(6, height * capacity / total);
        int max = Math.max(1, total - capacity);
        int thumbY = top + (height - thumbH) * scroll / max;
        drawRect(barX, thumbY, barX + 2, thumbY + thumbH, 0xC0FFFFFF);
    }

    private static int clampScroll(int scroll, int total, int capacity) {
        int max = Math.max(0, total - capacity);
        return Math.max(0, Math.min(scroll, max));
    }

    private static boolean inRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** Flip the Buttplug backend between buttplug4j and native, live (no restart needed). */
    private void toggleAdapter() {
        String next = "native".equalsIgnoreCase(client.backend()) ? "buttplug4j" : "native";
        client.setBackend(next);
        if (adapterBtn != null) {
            adapterBtn.displayString = adapterLabel();
        }
    }

    private String adapterLabel() {
        return "Adapter: " + ("native".equalsIgnoreCase(client.backend()) ? "native" : "buttplug4j");
    }

    private void refreshActionButtons() {
        ConnectionState state = client.status().state();
        boolean connected = state != ConnectionState.DISCONNECTED;
        boolean busy = state == ConnectionState.CONNECTING || state == ConnectionState.NEGOTIATING
                || state == ConnectionState.STOPPING;
        boolean panicked = !client.runtime().worker().isOutputEnabled();
        boolean enabled = client.config().enabled();

        if (adapterBtn != null) {
            adapterBtn.displayString = adapterLabel();
        }
        if (connectBtn != null) {
            connectBtn.displayString = connectLabel();
            connectBtn.enabled = !busy;
        }
        if (scanBtn != null) {
            scanBtn.displayString = scanLabel();
            scanBtn.enabled = connected && !busy;
        }
        if (refreshBtn != null) {
            refreshBtn.enabled = connected && !busy;
        }
        if (testBtn != null) {
            testBtn.enabled = enabled && connected
                    && client.status().deviceCount() > 0 && !panicked;
        }
        if (clearErrorsBtn != null) {
            clearErrorsBtn.enabled = !client.errorHistory().isEmpty();
        }
    }

    private String connectLabel() {
        return client.isConnected() ? "Disconnect" : "Connect";
    }

    private String scanLabel() {
        return client.status().state() == ConnectionState.SCANNING ? "Stop scan" : "Scan";
    }

    private String stateName() {
        return client.status().state().name().toLowerCase(Locale.ROOT);
    }
}
