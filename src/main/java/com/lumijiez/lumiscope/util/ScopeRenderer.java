package com.lumijiez.lumiscope.util;

import com.lumijiez.lumiscope.network.records.RadarBlip;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

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
        line(cx - radius, cy, cx + radius, cy, 0, 100, 0, 20);
        line(cx, cy - radius, cx, cy + radius, 0, 100, 0, 20);

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

        // blips — organic amoeba shapes
        if (blips != null) {
            for (RadarBlip blip : blips) {
                renderBlob(cx, cy, radius, blip, frame);
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

    // ---- organic blob rendering ----

    private static void renderBlob(int cx, int cy, int scopeR, RadarBlip blip, long frame) {
        int color = TIER_COLOR[blip.distanceTier];
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;

        // position with heavy wobble — ambiguous, never precise
        long id = Double.doubleToRawLongBits(blip.direction) ^ (blip.distanceTier * 7919L);
        double phase = (id & 0xFFFF) / 65535.0 * Math.PI * 2;

        double wobSpeedA = 0.03 + blip.distanceTier * 0.01;
        double wobSpeedB = 0.05 + blip.distanceTier * 0.007;
        double wobAmp = 6.0 + blip.distanceTier * 4.0;
        double wobX = Math.cos(frame * wobSpeedA + phase) * wobAmp
                    + Math.sin(frame * wobSpeedB * 1.4 + phase * 2.3) * wobAmp * 0.7;
        double wobY = Math.sin(frame * wobSpeedA * 1.3 + phase) * wobAmp
                    + Math.cos(frame * wobSpeedB + phase * 1.7) * wobAmp * 0.7;

        // more jitter for ambiguity
        double jitter = Math.sin(frame * 0.07 + phase) * Math.toRadians(6.0)
                      + Math.cos(frame * 0.11 + phase * 0.7) * Math.toRadians(4.0);
        double angle = blip.direction + jitter;

        // distance ratio — how far from center
        float distRatio;
        switch (blip.distanceTier) {
            case 0: distRatio = 0.88f; break;
            case 1: distRatio = 0.70f; break;
            case 2: distRatio = 0.50f; break;
            case 3: distRatio = 0.32f; break;
            case 4: distRatio = 0.18f; break;
            default:distRatio = 0.09f; break;
        }

        float bx = cx + (float)Math.sin(angle) * scopeR * distRatio + (float)wobX;
        float by = cy - (float)Math.cos(angle) * scopeR * distRatio + (float)wobY;

        // blob size — larger for more players, pulses over time
        int count = Math.min(blip.playerCount, 8);
        double pulse = 0.75 + 0.25 * Math.sin((frame + id % 97) * 0.12);
        double pulse2 = 0.85 + 0.15 * Math.sin((frame + id % 73) * 0.19 + 1.5);
        float baseSize = 7f + distRatio * 8f;
        float blobSize = (float)(baseSize * pulse * (1.0 + count * 0.25));

        // draw the organic amoeba shape
        drawOrganicBlob(bx, by, blobSize, count, cr, cg, cb, frame, id);

        // tendrils for merged blips
        if (count >= 2) {
            int tendrils = Math.min(count + 1, 6);
            drawTendrils(bx, by, blobSize, tendrils, cr, cg, cb, frame, id);
        }

        // inner core — brighter, smaller, pulses out of phase
        float coreSize = blobSize * 0.4f * (float)pulse2;
        drawOrganicBlob(bx, by, coreSize, 0, Math.min(255, cr + 60),
                Math.min(255, cg + 60), Math.min(255, cb + 60), frame, id + 1);

        // ambient wisps around the blob
        drawWisps(bx, by, blobSize * 1.6f, cr, cg, cb, frame, id, count);
    }

    private static void drawOrganicBlob(float cx, float cy, float size, int complexity,
                                         int cr, int cg, int cb, long frame, long seed) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();

        // outer body — noise-deformed triangle fan
        int verts = 28 + complexity * 4;
        float baseAlpha = 180f + complexity * 8f;
        float outerAlpha = 60f + complexity * 5f;

        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        b.pos(cx, cy, 0).color(cr, cg, cb, (int)baseAlpha).endVertex();

        for (int i = 0; i <= verts; i++) {
            double a = (i / (double)verts) * Math.PI * 2;
            double noise = noiseRadius(frame * 0.04, a, seed, complexity);
            float r = size * (float)(0.7 + 0.3 * noise);
            // extra deformation for complex blobs
            if (complexity >= 3) {
                double noise2 = noiseRadius(frame * 0.06 + 1.0, a * 2.3, seed + 37, complexity);
                r *= (float)(0.85 + 0.15 * noise2);
            }
            float x = cx + (float)Math.cos(a) * r;
            float y = cy + (float)Math.sin(a) * r;
            b.pos(x, y, 0).color(cr, cg, cb, (int)outerAlpha).endVertex();
        }
        tess.draw();

        // darker membrane outline
        b.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= verts; i++) {
            double a = (i / (double)verts) * Math.PI * 2;
            double noise = noiseRadius(frame * 0.04, a, seed, complexity);
            float r = size * (float)(0.7 + 0.3 * noise);
            if (complexity >= 3) {
                double noise2 = noiseRadius(frame * 0.06 + 1.0, a * 2.3, seed + 37, complexity);
                r *= (float)(0.85 + 0.15 * noise2);
            }
            float x = cx + (float)Math.cos(a) * r;
            float y = cy + (float)Math.sin(a) * r;
            int edgeAlpha = 100 + (int)(noise * 40);
            b.pos(x, y, 0).color(cr / 2, cg / 2, cb / 2, edgeAlpha).endVertex();
        }
        tess.draw();
    }

    private static void drawTendrils(float cx, float cy, float size, int count,
                                      int cr, int cg, int cb, long frame, long seed) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();

        for (int t = 0; t < count; t++) {
            double baseAngle = (t / (double)count) * Math.PI * 2
                             + Math.sin(frame * 0.03 + t + seed * 0.1) * 0.5;
            double tendrilLen = size * (0.6 + Math.sin(frame * 0.08 + t * 2.1) * 0.4);

            // tendril starts at blob surface, extends outward
            float sx = cx + (float)Math.cos(baseAngle) * size * 0.9f;
            float sy = cy + (float)Math.sin(baseAngle) * size * 0.9f;

            // tendril curves outward with noise
            int segs = 5;
            b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (int s = 0; s <= segs; s++) {
                double frac = s / (double)segs;
                double a = baseAngle + Math.sin(frame * 0.06 + s + t) * 0.25;
                float dist = size * 0.9f + (float)(tendrilLen * frac);
                float tx = cx + (float)Math.cos(a) * dist;
                float ty = cy + (float)Math.sin(a) * dist;
                float thick = 1.8f * (1f - (float)frac) * (float)(0.6 + 0.4 * Math.sin(frame * 0.1 + t));
                int alpha = (int)(120 * (1f - frac * 0.6));
                if (alpha < 10) alpha = 10;

                double perp = a + Math.PI / 2;
                float px = (float)Math.cos(perp) * thick;
                float py = (float)Math.sin(perp) * thick;
                b.pos(tx + px, ty + py, 0).color(cr, cg, cb, alpha).endVertex();
                b.pos(tx - px, ty - py, 0).color(cr, cg, cb, alpha / 2).endVertex();
            }
            tess.draw();
        }
    }

    private static void drawWisps(float cx, float cy, float radius,
                                   int cr, int cg, int cb, long frame, long seed, int count) {
        int wispCount = 5 + count * 2;
        for (int i = 0; i < wispCount; i++) {
            long s = seed + i * 131L;
            double a = (s & 0xFFFF) / 65535.0 * Math.PI * 2
                     + frame * 0.02 * ((i % 3) + 1);
            double dist = radius * (0.5 + ((s >> 16) & 0x7FFF) / 65535.0 * 0.5);
            float wx = cx + (float)(Math.cos(a) * dist);
            float wy = cy + (float)(Math.sin(a) * dist);
            float sz = 1.0f + (s & 3);
            int alpha = 30 + (int)(s & 63);
            fillCircle(wx, wy, sz, cr, cg, cb, alpha);
        }
    }

    // simple Perlin-like radius noise for organic deformation
    private static double noiseRadius(double t, double angle, long seed, int octaves) {
        double val = 0;
        double amp = 1.0;
        double freq = 1.0;
        for (int o = 0; o < Math.min(octaves + 1, 4); o++) {
            double s = Math.sin(angle * freq * 2.3 + t * (1.0 + o * 0.5) + seed * 0.01 + o * 1.7)
                     + Math.cos(angle * freq * 1.7 - t * (0.7 + o * 0.3) + seed * 0.013 + o * 0.9);
            val += s * amp;
            amp *= 0.5;
            freq *= 2.0;
        }
        // normalize to 0..1 range
        return (val / 2.0 + 1.0) / 2.0;
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
