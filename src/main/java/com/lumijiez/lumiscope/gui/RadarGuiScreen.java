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

    private static final int W = 176;
    private static final int H = 182;

    private static final int BTN_SCAN = 0, BTN_PREV = 1, BTN_NEXT = 2;

    private static List<RadarBlip> scanBlips;
    private static byte scanStatus = RadarScanResultPacket.STATUS_SUCCESS;
    private static long cooldownEndMs;
    private static byte selectedRange;

    private int guiLeft, guiTop;
    private GuiButton scanBtn, prevBtn, nextBtn;
    private long animFrame;

    private int scopeCX, scopeCY, scopeR;

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

    @Override
    public void initGui() {
        guiLeft = (width - W) / 2;
        guiTop = (height - H) / 2;

        int top = guiTop + 21;
        int bot = guiTop + H - 36;
        int areaH = bot - top;
        scopeR = Math.min(areaH / 2, (W - 24) / 2) - 4;
        scopeCX = guiLeft + W / 2;
        scopeCY = top + areaH / 2;

        prevBtn = new GuiButton(BTN_PREV, guiLeft + W - 34, guiTop + 4, 14, 14, "◀");
        nextBtn = new GuiButton(BTN_NEXT, guiLeft + W - 19, guiTop + 4, 14, 14, "▶");
        addButton(prevBtn);
        addButton(nextBtn);

        int barY = guiTop + H - 34 + 6;
        scanBtn = new GuiButton(BTN_SCAN, guiLeft + W / 2 - 32, barY, 64, 18, "Scan");
        addButton(scanBtn);

        updateButtonStates();
    }

    private void updateButtonStates() {
        long now = System.currentTimeMillis();
        boolean onCd = now < cooldownEndMs;
        boolean jammed = mc.player != null
                && mc.player.isPotionActive(PotionManager.JAMMED_POTION_EFFECT);

        scanBtn.enabled = !onCd && !jammed;
        if (onCd)      scanBtn.displayString = fmtCooldown(cooldownEndMs);
        else if (jammed) scanBtn.displayString = "Jammed!";
        else           scanBtn.displayString = "Scan";
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        animFrame++;
        updateButtonStates();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawContainer();

        ScanRange range = RadarNetworkHandler.getRange(selectedRange);

        drawString(fontRenderer, TextFormatting.GOLD + "☍ Lumiscope",
                guiLeft + 6, guiTop + 6, 0xFFFFFF);

        drawCenteredString(fontRenderer, TextFormatting.GOLD + range.label,
                guiLeft + W - 66, guiTop + 6, 0xFFFFFF);

        drawScope();

        drawFuel(range);

        drawMessage(range);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScope() {
        drawCircle(scopeCX, scopeCY, scopeR, 0xFF444444);
        ScopeRenderer.renderScope(scopeCX, scopeCY, scopeR, scanBlips, animFrame);

        int c = 0xFF4444;
        float s = 0.55f;
        int in = 3;

        GlStateManager.pushMatrix();
        GlStateManager.translate(scopeCX, scopeCY - scopeR + in, 0);
        GlStateManager.scale(s, s, 1f);
        drawCenteredString(fontRenderer, "N", 0, 0, c);
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(scopeCX, scopeCY + scopeR - in - 6, 0);
        GlStateManager.scale(s, s, 1f);
        drawCenteredString(fontRenderer, "S", 0, 0, c);
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(scopeCX - scopeR + in, scopeCY - 3, 0);
        GlStateManager.scale(s, s, 1f);
        drawString(fontRenderer, "W", 0, 0, c);
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(scopeCX + scopeR - in, scopeCY - 3, 0);
        GlStateManager.scale(s, s, 1f);
        drawString(fontRenderer, "E", -fontRenderer.getStringWidth("E"), 0, c);
        GlStateManager.popMatrix();
    }

    private void drawFuel(ScanRange range) {
        int barY = guiTop + H - 34 + 6;
        int x = guiLeft + 8;
        int y = barY;

        drawRect(x, y, x + 18, y + 18, 0xFF8B8B8B);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF373737);

        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(
                new ItemStack(range.fuelItem, range.fuelCount), x + 1, y + 1);
        RenderHelper.disableStandardItemLighting();

        drawString(fontRenderer, "×" + range.fuelCount, x + 22, y + 5, 0xFFFFFF);
    }

    private void drawMessage(ScanRange range) {
        int my = guiTop + H + 8;
        int cx = guiLeft + W / 2;

        boolean hasRes = scanBlips != null && !scanBlips.isEmpty()
                && scanStatus == RadarScanResultPacket.STATUS_SUCCESS;

        String msg;
        int color;

        if (scanStatus == RadarScanResultPacket.STATUS_JAMMED) {
            msg = TextFormatting.DARK_RED + "◆ Jammed — cannot scan";
            color = 0xFF0000;
        } else if (scanStatus == RadarScanResultPacket.STATUS_NO_FUEL) {
            String name = range.fuelItem.getItemStackDisplayName(new ItemStack(range.fuelItem));
            msg = TextFormatting.RED + "◆ Need " + range.fuelCount + "× " + name;
            color = 0xFF4444;
        } else if (hasRes) {
            msg = TextFormatting.GREEN + "" + scanBlips.size() + " signal(s) detected";
            color = 0x00FF00;
        } else {
            return;
        }

        drawCenteredString(fontRenderer, msg, cx, my, color);
    }

    private void drawContainer() {
        drawRect(guiLeft, guiTop, guiLeft + W, guiTop + H, 0xFFC6C6C6);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + W - 3, guiTop + H - 3, 0xFF000000);
        drawRect(guiLeft + 4, guiTop + 4, guiLeft + W - 4, guiTop + H - 4, 0xFF2B2B2B);
        drawRect(guiLeft + 4, guiTop + 18, guiLeft + W - 4, guiTop + 19, 0xFF444444);
        drawRect(guiLeft + 4, guiTop + H - 34, guiLeft + W - 4, guiTop + H - 33, 0xFF444444);
    }

    private static void drawCircle(int cx, int cy, int r, int color) {
        for (int i = 0; i < 360; i++) {
            double rad = Math.toRadians(i);
            drawRect((int)(cx + Math.cos(rad) * r), (int)(cy + Math.sin(rad) * r),
                     (int)(cx + Math.cos(rad) * r) + 1, (int)(cy + Math.sin(rad) * r) + 1, color);
        }
    }

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

    @Override
    protected void keyTyped(char c, int code) throws IOException {
        if (code == 1 || code == mc.gameSettings.keyBindInventory.getKeyCode()) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) mc.setIngameFocus();
        } else super.keyTyped(c, code);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }

    private static String fmtCooldown(long endMs) {
        long r = Math.max(0, (endMs - System.currentTimeMillis()) / 1000);
        if (r >= 3600) return (r / 3600) + "h" + ((r % 3600) / 60) + "m";
        if (r >= 60) return (r / 60) + "m" + (r % 60) + "s";
        return r + "s";
    }
}
