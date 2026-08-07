package com.lumijiez.lumiscope.gui;

import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler.ScanRange;
import com.lumijiez.lumiscope.network.packets.RadarScanRequestPacket;
import com.lumijiez.lumiscope.network.packets.RadarScanResultPacket;
import com.lumijiez.lumiscope.network.records.RadarBlip;
import com.lumijiez.lumiscope.potions.PotionManager;
import com.lumijiez.lumiscope.util.ScopeRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.util.List;

public class RadarGuiScreen extends GuiScreen {

    // container — same width as standard MC chest, slightly taller
    private static final int W = 176;
    private static final int H = 186;

    private static final int BTN_SCAN = 0, BTN_RANGE_PREV = 1, BTN_RANGE_NEXT = 2;

    private static List<RadarBlip> scanBlips;
    private static byte scanStatus = RadarScanResultPacket.STATUS_SUCCESS;
    private static long cooldownEndMs;
    private static byte selectedRange;

    private int guiLeft, guiTop;
    private GuiButton scanBtn, rangePrevBtn, rangeNextBtn;
    private long animFrame;

    // scope — centered in the container's upper area
    // guiTop+20 title bar /  guiTop+H-32 bottom bar  /  scope fills the middle
    private int scopeCX, scopeCY, scopeR;

    // ================================================================
    //  STATIC
    // ================================================================

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

    // ================================================================
    //  INIT
    // ================================================================

    @Override
    public void initGui() {
        guiLeft = (width - W) / 2;
        guiTop = (height - H) / 2;

        // scope fills the middle of the container
        int scopeAreaTop = guiTop + 20;
        int scopeAreaBot = guiTop + H - 36;
        int scopeAreaH = scopeAreaBot - scopeAreaTop;
        int scopeAreaW = W - 16;
        scopeR = Math.min(scopeAreaH / 2, scopeAreaW / 2) - 4;
        scopeCX = guiLeft + W / 2;
        scopeCY = scopeAreaTop + scopeAreaH / 2;

        // range buttons in title bar
        rangePrevBtn = new GuiButton(BTN_RANGE_PREV, guiLeft + W - 42, guiTop + 4, 14, 14, "◀");
        rangeNextBtn = new GuiButton(BTN_RANGE_NEXT, guiLeft + W - 14, guiTop + 4, 14, 14, "▶");
        addButton(rangePrevBtn);
        addButton(rangeNextBtn);

        // scan button — bottom right
        scanBtn = new GuiButton(BTN_SCAN, guiLeft + W - 66, guiTop + H - 28, 58, 18, "Scan");
        addButton(scanBtn);

        updateButtonStates();
    }

    private void updateButtonStates() {
        long now = System.currentTimeMillis();
        boolean onCd = now < cooldownEndMs;
        boolean jammed = mc.player != null
                && mc.player.isPotionActive(PotionManager.JAMMED_POTION_EFFECT);

        scanBtn.enabled = !onCd && !jammed;
        scanBtn.displayString = onCd ? fmtCooldown(cooldownEndMs)
                              : jammed ? "Jammed!"
                              : "Scan";
    }

    // ================================================================
    //  TICK
    // ================================================================

    @Override
    public void updateScreen() {
        super.updateScreen();
        animFrame++;
        updateButtonStates();
    }

    // ================================================================
    //  DRAW
    // ================================================================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawContainer();

        ScanRange range = RadarNetworkHandler.getRange(selectedRange);

        // title
        drawString(fontRenderer, TextFormatting.GOLD + "☍ Lumiscope",
                guiLeft + 6, guiTop + 6, 0xFFFFFF);

        // range name between buttons
        String rng = TextFormatting.GOLD + range.label;
        drawCenteredString(fontRenderer, rng,
                guiLeft + W - 74, guiTop + 6, 0xFFFFFF);

        // scope
        drawScope();

        // fuel
        drawFuel(range);

        // status
        drawStatus(range);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // ---- scope ----

    private void drawScope() {
        // border ring
        drawCircle(scopeCX, scopeCY, scopeR, 0xFF444444);

        ScopeRenderer.renderScope(scopeCX, scopeCY, scopeR, scanBlips, animFrame);

        // compass letters — red, small, tight inside scope edge
        int c = 0xFF4444;
        float s = 0.55f;
        int inset = 3;

        // N
        GlStateManager.pushMatrix();
        GlStateManager.translate(scopeCX, scopeCY - scopeR + inset, 0);
        GlStateManager.scale(s, s, 1f);
        drawCenteredString(fontRenderer, "N", 0, 0, c);
        GlStateManager.popMatrix();

        // S
        GlStateManager.pushMatrix();
        GlStateManager.translate(scopeCX, scopeCY + scopeR - inset - 6, 0);
        GlStateManager.scale(s, s, 1f);
        drawCenteredString(fontRenderer, "S", 0, 0, c);
        GlStateManager.popMatrix();

        // W
        GlStateManager.pushMatrix();
        GlStateManager.translate(scopeCX - scopeR + inset, scopeCY - 3, 0);
        GlStateManager.scale(s, s, 1f);
        drawString(fontRenderer, "W", 0, 0, c);
        GlStateManager.popMatrix();

        // E
        GlStateManager.pushMatrix();
        GlStateManager.translate(scopeCX + scopeR - inset, scopeCY - 3, 0);
        GlStateManager.scale(s, s, 1f);
        drawString(fontRenderer, "E", -fontRenderer.getStringWidth("E"), 0, c);
        GlStateManager.popMatrix();
    }

    // ---- fuel ----

    private void drawFuel(ScanRange range) {
        int x = guiLeft + 6;
        int y = guiTop + H - 28;

        // slot
        drawRect(x, y, x + 18, y + 18, 0xFF8B8B8B);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF373737);

        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(
                new ItemStack(range.fuelItem, range.fuelCount), x + 1, y + 1);
        RenderHelper.disableStandardItemLighting();

        drawString(fontRenderer, "×" + range.fuelCount, x + 22, y + 5, 0xFFFFFF);
    }

    // ---- status ----

    private void drawStatus(ScanRange range) {
        long now = System.currentTimeMillis();
        long cd = cooldownEndMs - now;
        boolean hasRes = scanBlips != null && !scanBlips.isEmpty()
                && scanStatus == RadarScanResultPacket.STATUS_SUCCESS;

        String msg;
        int color;

        if (scanStatus == RadarScanResultPacket.STATUS_JAMMED) {
            msg = TextFormatting.DARK_RED + "Jammed";
            color = 0xFF0000;
        } else if (scanStatus == RadarScanResultPacket.STATUS_NO_FUEL) {
            msg = TextFormatting.RED + "Need " + range.fuelCount + "x " +
                    range.fuelItem.getItemStackDisplayName(new ItemStack(range.fuelItem));
            color = 0xFF4444;
        } else if (hasRes && cd > 0) {
            msg = TextFormatting.GREEN + "" + scanBlips.size() + " signal(s)  |  " +
                    TextFormatting.GRAY + fmtCooldown(cooldownEndMs);
            color = 0x00FF00;
        } else if (hasRes) {
            msg = TextFormatting.GREEN + "" + scanBlips.size() + " signal(s)";
            color = 0x00FF00;
        } else if (cd > 0) {
            msg = TextFormatting.GRAY + "Cooldown: " + fmtCooldown(cooldownEndMs);
            color = 0x888888;
        } else {
            msg = TextFormatting.YELLOW + "Ready";
            color = 0xFFFF00;
        }

        drawString(fontRenderer, msg, guiLeft + 32, guiTop + H - 19, color);
    }

    // ---- container ----

    private void drawContainer() {
        // main background (chest-gui grey)
        drawRect(guiLeft, guiTop, guiLeft + W, guiTop + H, 0xFFC6C6C6);
        // inner dark fill
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + W - 3, guiTop + H - 3, 0xFF000000);
        // border inset
        drawRect(guiLeft + 4, guiTop + 4, guiLeft + W - 4, guiTop + H - 4, 0xFF2B2B2B);
        // title separator
        drawRect(guiLeft + 4, guiTop + 18, guiLeft + W - 4, guiTop + 19, 0xFF444444);
        // bottom separator
        drawRect(guiLeft + 4, guiTop + H - 32, guiLeft + W - 4, guiTop + H - 31, 0xFF444444);
    }

    // manual circle since GuiScreen has no drawCircle
    private static void drawCircle(int cx, int cy, int r, int color) {
        for (int i = 0; i < 360; i++) {
            double rad = Math.toRadians(i);
            int x = (int)(cx + Math.cos(rad) * r);
            int y = (int)(cy + Math.sin(rad) * r);
            drawRect(x, y, x + 1, y + 1, color);
        }
    }

    // ================================================================
    //  ACTIONS
    // ================================================================

    @Override
    protected void actionPerformed(GuiButton btn) throws IOException {
        switch (btn.id) {
            case BTN_SCAN:
                if (System.currentTimeMillis() < cooldownEndMs) return;
                if (mc.player.isPotionActive(PotionManager.JAMMED_POTION_EFFECT)) return;
                scanBlips = null;
                scanStatus = RadarScanResultPacket.STATUS_SUCCESS;
                RadarNetworkHandler.getNetworkChannel()
                        .sendToServer(new RadarScanRequestPacket(selectedRange));
                break;
            case BTN_RANGE_PREV:
                selectedRange = (byte)((selectedRange - 1
                        + RadarNetworkHandler.getRangeCount())
                        % RadarNetworkHandler.getRangeCount());
                break;
            case BTN_RANGE_NEXT:
                selectedRange = (byte)((selectedRange + 1)
                        % RadarNetworkHandler.getRangeCount());
                break;
        }
    }

    // ================================================================
    //  KEYBOARD
    // ================================================================

    @Override
    protected void keyTyped(char c, int code) throws IOException {
        if (code == 1 || code == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) mc.setIngameFocus();
        } else super.keyTyped(c, code);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    // ================================================================
    //  HELPERS
    // ================================================================

    private static String fmtCooldown(long endMs) {
        long r = Math.max(0, (endMs - System.currentTimeMillis()) / 1000);
        if (r >= 3600) return (r / 3600) + "h" + ((r % 3600) / 60) + "m";
        if (r >= 60) return (r / 60) + "m" + (r % 60) + "s";
        return r + "s";
    }
}
