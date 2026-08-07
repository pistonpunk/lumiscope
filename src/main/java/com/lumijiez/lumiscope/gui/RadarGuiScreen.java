package com.lumijiez.lumiscope.gui;

import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler.ScanRange;
import com.lumijiez.lumiscope.network.packets.RadarScanRequestPacket;
import com.lumijiez.lumiscope.network.packets.RadarScanResultPacket;
import com.lumijiez.lumiscope.network.records.RadarBlip;
import com.lumijiez.lumiscope.potions.PotionManager;
import com.lumijiez.lumiscope.util.ScopeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.List;

public class RadarGuiScreen extends GuiScreen {

    private static final int BTN_SCAN = 0, BTN_PREV = 1, BTN_NEXT = 2;

    private static List<RadarBlip> scanBlips = null;
    private static byte scanStatus = RadarScanResultPacket.STATUS_SUCCESS;
    private static long cooldownEndMs = 0;
    private static byte selectedRange = 0;

    private int scopeCX, scopeCY, scopeR;
    private GuiButton scanBtn, prevBtn, nextBtn;
    private long animFrame = 0;
    private int errorTicks = 0;
    private String statusMsg = "";
    private int statusColor = 0xFFFFFF;

    // ---- static ----

    public static void onScanResult(List<RadarBlip> blips, byte status,
                                     long serverTimestamp, byte rangeOrdinal) {
        scanBlips = blips;
        scanStatus = status;
        if (status == RadarScanResultPacket.STATUS_SUCCESS) {
            ScanRange r = RadarNetworkHandler.getRange(rangeOrdinal);
            cooldownEndMs = serverTimestamp + r.cooldownMs;
            selectedRange = rangeOrdinal;
        }
    }

    // ---- init ----

    @Override
    public void initGui() {
        // Responsive scope — scales with screen, generous max
        int headerH = 15;
        int footerH = 28;
        int availH = height - headerH - footerH;
        int availW = width - 24;
        // Add 8px padding so scope never touches header/footer/edges
        scopeR = Math.min(availH / 2, availW / 2) - 12;
        if (scopeR > 140) scopeR = 140;
        if (scopeR < 44) scopeR = 44;

        scopeCX = width / 2;
        scopeCY = headerH + availH / 2;

        // Range buttons — left side under the range text
        int rangeX = 8;
        int rangeY = 42;
        prevBtn = new GuiButton(BTN_PREV, rangeX, rangeY, 16, 16, "<");
        nextBtn = new GuiButton(BTN_NEXT, rangeX + 20, rangeY, 16, 16, ">");
        addButton(prevBtn);
        addButton(nextBtn);

        // Scan button — centered at bottom
        scanBtn = new GuiButton(BTN_SCAN, width / 2 - 40, height - 24, 80, 20, "Scan");
        addButton(scanBtn);
    }

    // ---- tick ----

    @Override
    public void updateScreen() {
        super.updateScreen();
        animFrame++;

        boolean onCd = System.currentTimeMillis() < cooldownEndMs;
        boolean jammed = mc.player.isPotionActive(PotionManager.JAMMED_POTION_EFFECT);
        boolean hasRes = scanBlips != null && !scanBlips.isEmpty()
                && scanStatus == RadarScanResultPacket.STATUS_SUCCESS;

        ScanRange range = RadarNetworkHandler.getRange(selectedRange);

        if (jammed) {
            scanBtn.displayString = "Jammed!";
            scanBtn.enabled = false;
            statusMsg = TextFormatting.DARK_RED + "JAMMED — cannot scan";
            statusColor = 0xFF0000;
        } else if (scanStatus == RadarScanResultPacket.STATUS_NO_FUEL) {
            scanBtn.displayString = "No Fuel!";
            scanBtn.enabled = false;
            statusMsg = TextFormatting.RED + "Need " + range.fuelCount + "x fuel";
            statusColor = 0xFF4444;
            errorTicks++;
            if (errorTicks > 100) {
                scanStatus = RadarScanResultPacket.STATUS_SUCCESS;
                errorTicks = 0;
            }
        } else if (hasRes) {
            scanBtn.displayString = onCd ? "Cooldown " + fmtBtn(cooldownEndMs) : "Scan Again";
            scanBtn.enabled = !onCd;
            statusMsg = TextFormatting.GREEN + "" + scanBlips.size()
                    + " signal(s)  |  " + range.label;
            statusColor = 0x00FF00;
        } else if (onCd) {
            scanBtn.displayString = "Cooldown " + fmtBtn(cooldownEndMs);
            scanBtn.enabled = false;
            statusMsg = TextFormatting.GRAY + "Recharging...";
            statusColor = 0x888888;
        } else {
            scanBtn.displayString = "Scan";
            scanBtn.enabled = true;
            statusMsg = TextFormatting.YELLOW + "Ready";
            statusColor = 0xFFFF00;
        }
    }

    // ---- draw ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        ScanRange range = RadarNetworkHandler.getRange(selectedRange);

        // Title
        drawCenteredString(fontRenderer,
                TextFormatting.GOLD + "Lumiscope Radar",
                width / 2, 6, 0xFFFFFF);

        // Scope border
        drawRect(scopeCX - scopeR - 2, scopeCY - scopeR - 2,
                scopeCX + scopeR + 2, scopeCY + scopeR + 2, 0xFF333333);

        ScopeRenderer.renderScope(scopeCX, scopeCY, scopeR, scanBlips, animFrame);

        // Compass
        label(scopeCX, scopeCY - scopeR - 6, "N", 0x00DD00);
        label(scopeCX + scopeR + 7, scopeCY, "E", 0x00DD00);
        label(scopeCX, scopeCY + scopeR + 6, "S", 0x00DD00);
        label(scopeCX - scopeR - 16, scopeCY, "W", 0x00DD00);

        // Range label + buttons on the left
        drawString(fontRenderer,
                TextFormatting.GOLD + range.label,
                8, 30, 0xFFFFFF);

        // Fuel icon + count (left side, below range buttons)
        int fuelX = 8;
        int fuelY = 64;
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(
                new ItemStack(range.fuelItem), fuelX, fuelY);
        RenderHelper.disableStandardItemLighting();
        drawString(fontRenderer,
                TextFormatting.WHITE + "x" + range.fuelCount,
                fuelX + 20, fuelY + 4, 0xFFFFFF);

        // Cooldown duration (left side, below fuel)
        drawString(fontRenderer,
                TextFormatting.DARK_GRAY + "Cooldown: " + fmtDur(range.cooldownMs),
                fuelX, fuelY + 20, 0x666666);

        // Legend (top-right, compact)
        int lx = width - 100;
        int ly = 14;
        String[] names = {"V.Close","Close","Mod.","Far","V.Far","Faint"};
        int[] lc = {0xFFFFB000,0xFFFFD700,0xFFADFF2F,0xFF00CED1,0xFF4169E1,0xFF8888CC};
        for (int i = 0; i < names.length; i++) {
            int y = ly + i * 9;
            drawRect(lx, y, lx + 6, y + 5, lc[i]);
            drawString(fontRenderer, names[i], lx + 9, y - 1, lc[i]);
        }

        // Status (bottom-left)
        drawString(fontRenderer, statusMsg, 6, height - 13, statusColor);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // ---- actions ----

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        switch (btn.id) {
            case BTN_SCAN:
                if (System.currentTimeMillis() < cooldownEndMs) return;
                if (mc.player.isPotionActive(PotionManager.JAMMED_POTION_EFFECT)) return;
                if (!btn.enabled) return;
                scanBlips = null;
                scanStatus = RadarScanResultPacket.STATUS_SUCCESS;
                RadarNetworkHandler.getNetworkChannel()
                        .sendToServer(new RadarScanRequestPacket(selectedRange));
                break;
            case BTN_PREV:
                selectedRange = (byte)((selectedRange - 1
                        + RadarNetworkHandler.getRangeCount())
                        % RadarNetworkHandler.getRangeCount());
                break;
            case BTN_NEXT:
                selectedRange = (byte)((selectedRange + 1)
                        % RadarNetworkHandler.getRangeCount());
                break;
        }
    }

    // ---- helpers ----

    private void label(int x, int y, String s, int c) {
        drawString(fontRenderer, s, x - fontRenderer.getStringWidth(s) / 2, y - 4, c);
    }

    private static String fmtFull(long endMs) {
        long r = Math.max(0, (endMs - System.currentTimeMillis()) / 1000);
        if (r >= 3600) return (r/3600) + "h " + ((r%3600)/60) + "m remaining";
        if (r >= 60) return (r/60) + "m " + (r%60) + "s remaining";
        return r + "s remaining";
    }

    private static String fmtBtn(long endMs) {
        long r = Math.max(0, (endMs - System.currentTimeMillis()) / 1000);
        if (r >= 3600) return (r/3600) + "h" + ((r%3600)/60) + "m";
        if (r >= 60) return (r/60) + "m";
        return r + "s";
    }

    private static String fmtDur(long ms) {
        long s = ms / 1000;
        if (s >= 3600) return (s/3600) + "h";
        if (s >= 60) return (s/60) + "m";
        return s + "s";
    }

    @Override
    protected void keyTyped(char c, int code) throws IOException {
        if (code == 1 || code == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) mc.setIngameFocus();
        } else super.keyTyped(c, code);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
