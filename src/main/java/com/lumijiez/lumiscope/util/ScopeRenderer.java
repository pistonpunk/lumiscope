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

        fillCircle(cx, cy, radius, 8, 20, 8, 220);

        for (int r = 1; r <= 5; r++) {
            ring(cx, cy, radius * r / 6f, 0, 160, 0, 30 + r * 6);
        }
        ring(cx, cy, radius, 0, 180, 0, 60);

        line(cx - radius, cy, cx + radius, cy, 0, 120, 0, 40);
        line(cx, cy - radius, cx, cy + radius, 0, 120, 0, 40);

        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float inner = cx + (float)Math.cos(a) * radius * 0.92f;
            float iy   = cy + (float)Math.sin(a) * radius * 0.92f;
            float outer = cx + (float)Math.cos(a) * radius * 0.98f;
            float oy   = cy + (float)Math.sin(a) * radius * 0.98f;
            int alpha = i % 2 == 0 ? 80 : 45;
            line(inner, iy, outer, oy, 0, 150, 0, alpha);
        }

        float sweepDeg = (frame * 1.2f) % 360f;
        double sweep = Math.toRadians(sweepDeg);
        double sweepEnd = Math.toRadians(sweepDeg - 9);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        b.pos(cx, cy, 0).color(0, 220, 0, 20).endVertex();
        b.pos(cx + (float)Math.cos(sweep) * radius, cy + (float)Math.sin(sweep) * radius, 0)
         .color(0, 220, 0, 8).endVertex();
        b.pos(cx + (float)Math.cos(sweepEnd) * radius, cy + (float)Math.sin(sweepEnd) * radius, 0)
         .color(0, 220, 0, 2).endVertex();
        tess.draw();

        long seed = frame / 4;
        for (int i = 0; i < 45; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double a = ((double) (seed >>> 16) / (double) (1L << 48)) * Math.PI * 2;
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double d = ((double) (seed >>> 16) / (double) (1L << 48)) * radius * 0.92;
            float dx = cx + (float)(Math.cos(a) * d);
            float dy = cy + (float)(Math.sin(a) * d);
            int alpha = (int)(seed & 63) + 8;
            fillRect(dx - 0.5f, dy - 0.5f, 1, 1, 0, 170, 0, alpha);
        }

        if (blips != null) {
            for (RadarBlip blip : blips) {
                renderBlip(cx, cy, radius, blip, frame);
            }
        }

        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderBlip(int cx, int cy, int scopeR, RadarBlip blip, long frame) {
        int tier = blip.distanceTier;
        int color = TIER_COLOR[tier];
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;
        int hcr = Math.min(255, cr + 80);
        int hcg = Math.min(255, cg + 80);
        int hcb = Math.min(255, cb + 80);

        long id = Double.doubleToRawLongBits(blip.direction) ^ (tier * 7919L);
        double phase = (id & 0xFFFF) / 65535.0 * Math.PI * 2;

        double jitter = Math.sin(frame * 0.06 + phase) * Math.toRadians(2.0)
                      + Math.cos(frame * 0.09 + phase * 0.7) * Math.toRadians(1.5);
        double angle = blip.direction + jitter;

        int count = Math.min(blip.playerCount, 8);
        int barCount = 3 + count;
        double spread = Math.toRadians(3.0 + count * 1.5);

        for (int i = 0; i < barCount; i++) {
            double frac = (i - (barCount - 1) / 2.0) / ((barCount - 1) / 2.0);
            double a = angle + frac * spread;
            double pulse = 0.5 + 0.5 * Math.sin(frame * 0.12 + i * 1.7 + phase);
            double pulse2 = 0.5 + 0.5 * Math.sin(frame * 0.17 + i * 2.3 + phase + 1.0);
            float maxH = 6f + count * 2.5f + tier * 1.2f;
            float barH = maxH * (float)(0.4 + 0.6 * pulse);

            float outerX = cx + (float)Math.cos(a) * scopeR;
            float outerY = cy + (float)Math.sin(a) * scopeR;
            float innerX = cx + (float)Math.cos(a) * (scopeR - barH);
            float innerY = cy + (float)Math.sin(a) * (scopeR - barH);

            float thickness = 1.2f + count * 0.3f + (float)pulse2 * 0.5f;
            int alpha = 120 + (int)(80 * pulse);

            double perp = a + Math.PI / 2;
            float px = (float)Math.cos(perp) * thickness;
            float py = (float)Math.sin(perp) * thickness;

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder b = tess.getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            b.pos(outerX + px, outerY + py, 0).color(hcr, hcg, hcb, alpha).endVertex();
            b.pos(outerX - px, outerY - py, 0).color(cr, cg, cb, alpha / 2).endVertex();
            b.pos(innerX - px, innerY - py, 0).color(cr, cg, cb, alpha / 3).endVertex();
            b.pos(innerX + px, innerY + py, 0).color(hcr, hcg, hcb, alpha / 2).endVertex();
            tess.draw();
        }
    }

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
        int segs = (int)(r * 0.5 + 12);
        if (segs < 8) segs = 8;
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

    private static void thickLine(float x1, float y1, float x2, float y2,
                                   float width, int cr, int cg, int cb, int ca) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001) return;
        double px = -dy / len * width / 2.0;
        double py =  dx / len * width / 2.0;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        b.pos(x1 + px, y1 + py, 0).color(cr, cg, cb, ca).endVertex();
        b.pos(x1 - px, y1 - py, 0).color(cr, cg, cb, ca / 2).endVertex();
        b.pos(x2 - px, y2 - py, 0).color(cr, cg, cb, ca / 2).endVertex();
        b.pos(x2 + px, y2 + py, 0).color(cr, cg, cb, ca).endVertex();
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
