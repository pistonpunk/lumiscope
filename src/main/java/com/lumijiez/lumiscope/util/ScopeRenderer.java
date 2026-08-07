package com.lumijiez.lumiscope.util;

import com.lumijiez.lumiscope.network.records.RadarBlip;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class ScopeRenderer {

    private static final int[] TIER_COLOR = {
        0xFFFFB000, 0xFFFFD700, 0xFFADFF2F, 0xFF00CED1, 0xFF4169E1, 0xFF191970,
    };

    public static void renderScope(int cx, int cy, int radius,
                                    List<RadarBlip> blips, long frame) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1, 1, 1, 1);

        // background
        fillCircle(cx, cy, radius, 10, 26, 10, 220);

        // concentric rings
        for (int i = 1; i <= 5; i++) {
            int r = radius * i / 6;
            ring(cx, cy, r, 0, 180, 0, i == 5 ? 60 : 30);
        }

        // crosshair
        line(cx - radius, cy, cx + radius, cy, 0, 100, 0, 25);
        line(cx, cy - radius, cx, cy + radius, 0, 100, 0, 25);

        // cardinal ticks
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float ix = cx + (float) Math.cos(a) * radius;
            float iy = cy + (float) Math.sin(a) * radius;
            float ox = cx + (float) Math.cos(a) * (radius - radius * 0.07f);
            float oy = cy + (float) Math.sin(a) * (radius - radius * 0.07f);
            line(ox, oy, ix, iy, 0, 140, 0, 55);
        }

        // sweep wedge
        float sweepDeg = (frame * 0.6f) % 360f;
        double sr = Math.toRadians(sweepDeg);
        double sr2 = Math.toRadians(sweepDeg - 5);
        float sx = cx + (float) Math.cos(sr) * radius;
        float sy = cy + (float) Math.sin(sr) * radius;
        float sx2 = cx + (float) Math.cos(sr2) * radius;
        float sy2 = cy + (float) Math.sin(sr2) * radius;

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        b.pos(cx, cy, 0).color(0, 255, 0, 30).endVertex();
        b.pos(sx, sy, 0).color(0, 255, 0, 30).endVertex();
        b.pos(sx2, sy2, 0).color(0, 255, 0, 4).endVertex();
        tess.draw();

        // blips
        if (blips != null) {
            for (RadarBlip blip : blips) {
                renderBlip(cx, cy, radius, blip, frame);
            }
        }

        // static noise
        long seed = frame / 4;
        for (int i = 0; i < 35; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double a = ((double) (seed >>> 16) / (double) (1L << 48)) * Math.PI * 2;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double d = ((double) (seed >>> 16) / (double) (1L << 48)) * radius * 0.88;
            float dx = cx + (float)(Math.cos(a) * d);
            float dy = cy + (float)(Math.sin(a) * d);
            int alpha = (int)(seed & 63) + 8;
            fillRect(dx - 0.5f, dy - 0.5f, 1, 1, 0, 170, 0, alpha);
        }

        // outer ring
        ring(cx, cy, radius, 0, 220, 0, 100);

        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderBlip(int cx, int cy, int radius, RadarBlip blip, long frame) {
        long seed = Double.doubleToRawLongBits(blip.direction) ^ (blip.distanceTier * 7919L);
        double phase = (seed & 0xFFFF) / 65535.0 * Math.PI * 2;

        double wobSpeed = 0.04 + blip.distanceTier * 0.008;
        double wobAmp = 4.0 + blip.distanceTier * 2.5;
        double wobX = Math.cos(frame * wobSpeed + phase) * wobAmp * 0.6;
        double wobY = Math.sin(frame * wobSpeed * 1.3 + phase) * wobAmp * 0.8;

        double jitter = Math.sin(frame * 0.07 + phase) * Math.toRadians(3.0);
        double angle = blip.direction + jitter;

        float distRatio;
        switch (blip.distanceTier) {
            case 0: distRatio = 0.90f; break;
            case 1: distRatio = 0.72f; break;
            case 2: distRatio = 0.52f; break;
            case 3: distRatio = 0.34f; break;
            case 4: distRatio = 0.20f; break;
            default:distRatio = 0.10f; break;
        }

        // Convert from math angle (0=East) to compass angle (0=North, top of screen)
        float bx = cx + (float)Math.sin(angle) * radius * distRatio + (float)wobX;
        float by = cy - (float)Math.cos(angle) * radius * distRatio + (float)wobY;

        int color = TIER_COLOR[blip.distanceTier];
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;

        double pulse = 0.7 + 0.3 * Math.sin((frame + seed % 97) * 0.15);
        float size = (1f - distRatio) * 10f + 6f;
        size = size * (float)pulse * (1f + blip.playerCount * 0.3f);
        if (size < 5f) size = 5f;

        fillCircle(bx, by, size, cr, cg, cb, 220);
        ring(bx, by, size + 1.5f, 255, 255, 255, 180);

        if (blip.playerCount > 1) {
            for (int i = 0; i < blip.playerCount && i < 4; i++) {
                double da = (i / (double)blip.playerCount) * Math.PI * 2 + frame * 0.03;
                float dx = bx + (float)Math.cos(da) * (size + 3f);
                float dy = by + (float)Math.sin(da) * (size + 3f);
                fillCircle(dx, dy, 2f, 255, 255, 255, 230);
            }
        }
    }

    // ---- primitives ----

    private static void fillCircle(float cx, float cy, float r,
                                    int cr, int cg, int cb, int ca) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        b.pos(cx, cy, 0).color(cr, cg, cb, ca).endVertex();
        int segs = (int)(r * 0.5 + 12);
        if (segs < 8) segs = 8;
        for (int i = 0; i <= segs; i++) {
            double a = (i / (double)segs) * Math.PI * 2;
            b.pos(cx + (float)Math.cos(a) * r,
                  cy + (float)Math.sin(a) * r, 0)
             .color(cr, cg, cb, ca).endVertex();
        }
        tess.draw();
    }

    private static void ring(float cx, float cy, float r,
                              int cr, int cg, int cb, int ca) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        int segs = 72;
        for (int i = 0; i < segs; i++) {
            double a = (i / (double)segs) * Math.PI * 2;
            b.pos(cx + (float)Math.cos(a) * r,
                  cy + (float)Math.sin(a) * r, 0)
             .color(cr, cg, cb, ca).endVertex();
        }
        tess.draw();
    }

    private static void line(float x1, float y1, float x2, float y2,
                              int cr, int cg, int cb, int ca) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        b.pos(x1, y1, 0).color(cr, cg, cb, ca).endVertex();
        b.pos(x2, y2, 0).color(cr, cg, cb, ca).endVertex();
        tess.draw();
    }

    private static void fillRect(float x, float y, float w, float h,
                                  int cr, int cg, int cb, int ca) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        b.pos(x,     y,     0).color(cr, cg, cb, ca).endVertex();
        b.pos(x,     y + h, 0).color(cr, cg, cb, ca).endVertex();
        b.pos(x + w, y + h, 0).color(cr, cg, cb, ca).endVertex();
        b.pos(x + w, y,     0).color(cr, cg, cb, ca).endVertex();
        tess.draw();
    }
}
