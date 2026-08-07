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

        // dark background
        fillCircle(cx, cy, radius, 8, 20, 8, 220);

        // concentric rings
        for (int r = 1; r <= 5; r++) {
            ring(cx, cy, radius * r / 6f, 0, 160, 0, 30 + r * 6);
        }
        ring(cx, cy, radius, 0, 180, 0, 60);

        // crosshair
        line(cx - radius, cy, cx + radius, cy, 0, 120, 0, 40);
        line(cx, cy - radius, cx, cy + radius, 0, 120, 0, 40);

        // cardinal ticks
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float inner = cx + (float)Math.cos(a) * radius * 0.92f;
            float iy   = cy + (float)Math.sin(a) * radius * 0.92f;
            float outer = cx + (float)Math.cos(a) * radius * 0.98f;
            float oy   = cy + (float)Math.sin(a) * radius * 0.98f;
            int alpha = i % 2 == 0 ? 80 : 45;
            line(inner, iy, outer, oy, 0, 150, 0, alpha);
        }

        // sweep wedge
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

        // static noise
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

        // blips — animated directed line indicators
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

    // ================================================================
    //  ANIMATED DIRECTED-LINE BLIP
    // ================================================================

    private static void renderBlip(int cx, int cy, int scopeR, RadarBlip blip, long frame) {
        int tier = blip.distanceTier;
        int color = TIER_COLOR[tier];
        int cr = (color >> 16) & 0xFF;
        int cg = (color >> 8) & 0xFF;
        int cb = color & 0xFF;
        // bright highlight variant
        int hcr = Math.min(255, cr + 80);
        int hcg = Math.min(255, cg + 80);
        int hcb = Math.min(255, cb + 80);

        long id = Double.doubleToRawLongBits(blip.direction) ^ (tier * 7919L);
        double phase = (id & 0xFFFF) / 65535.0 * Math.PI * 2;

        // --- position ---
        double wobAmp = 4.0 + tier * 2.0;
        double wobX = Math.cos(frame * 0.04 + phase) * wobAmp
                    + Math.sin(frame * 0.07 + phase * 2.1) * wobAmp * 0.6;
        double wobY = Math.sin(frame * 0.05 + phase) * wobAmp
                    + Math.cos(frame * 0.06 + phase * 1.5) * wobAmp * 0.6;

        double jitter = Math.sin(frame * 0.08 + phase) * Math.toRadians(5.0)
                      + Math.cos(frame * 0.11 + phase * 0.7) * Math.toRadians(3.0);
        double angle = blip.direction + jitter;

        float distRatio;
        switch (tier) {
            case 0: distRatio = 0.88f; break;
            case 1: distRatio = 0.70f; break;
            case 2: distRatio = 0.50f; break;
            case 3: distRatio = 0.32f; break;
            case 4: distRatio = 0.18f; break;
            default:distRatio = 0.09f; break;
        }

        float bx = cx + (float)Math.sin(angle) * scopeR * distRatio + (float)wobX;
        float by = cy - (float)Math.cos(angle) * scopeR * distRatio + (float)wobY;

        int count = Math.min(blip.playerCount, 8);
        double pulse = 0.7 + 0.3 * Math.sin((frame + id % 97) * 0.1);
        double pulseFast = 0.7 + 0.3 * Math.sin((frame + id % 73) * 0.17);
        float baseLen = 8f + distRatio * 10f;

        // ============================================================
        //  1. GHOST AFTERIMAGES — faint offset copies of the main cluster
        // ============================================================
        for (int g = 0; g < 3; g++) {
            double goffX = Math.cos(frame * 0.02 + g * 2.1) * 3.0;
            double goffY = Math.sin(frame * 0.025 + g * 1.8) * 3.0;
            float gAlpha = 15 + (g == 0 ? 15 : 0);
            float gLen = baseLen * (0.7f - g * 0.15f);
            for (int i = 0; i < 4; i++) {
                double ga = phase + i * Math.PI / 2 + frame * 0.02 * (g + 1);
                float gx1 = bx + (float)(Math.cos(ga) * gLen * 0.5f + goffX);
                float gy1 = by + (float)(Math.sin(ga) * gLen * 0.5f + goffY);
                float gx2 = bx + (float)(-Math.cos(ga) * gLen * 0.5f + goffX);
                float gy2 = by + (float)(-Math.sin(ga) * gLen * 0.5f + goffY);
                line(gx1, gy1, gx2, gy2, cr, cg, cb, (int)gAlpha);
            }
        }

        // ============================================================
        //  2. EMANATING PULSE RINGS — expanding sonar pings
        // ============================================================
        for (int ring = 0; ring < 3; ring++) {
            double ringPhase = (frame * 0.06 + ring * 2.094 + phase) % (Math.PI * 2);
            double life = Math.sin(ringPhase);
            if (life < 0.05) continue; // only visible during growth half
            float rMin = baseLen * 0.5f;
            float rMax = baseLen * 2.0f;
            float rr = rMin + (rMax - rMin) * (float)life;
            int ra = (int)(70 * (1.0 - life));
            if (ra < 5) continue;
            ring(bx, by, rr, hcr, hcg, hcb, ra);
        }

        // ============================================================
        //  3. INNER CLUSTER — tight fast-spinning lines (clockwise)
        // ============================================================
        int innerCount = 5 + count;
        float innerR = baseLen * 0.45f;
        for (int i = 0; i < innerCount; i++) {
            double ip = phase + (i * Math.PI * 2) / innerCount;
            double spin = frame * 0.06 + ip;
            double swing = Math.sin(frame * 0.11 + ip) * 0.8 + Math.cos(frame * 0.14 + ip) * 0.5;
            double la = spin + swing;
            float h = innerR * (float)(0.5 + 0.5 * Math.sin(frame * 0.09 + i));
            float sx = bx - (float)Math.cos(la) * h;
            float sy = by - (float)Math.sin(la) * h;
            float ex = bx + (float)Math.cos(la) * h;
            float ey = by + (float)Math.sin(la) * h;
            int alpha = 120 + (int)(80 * Math.sin(frame * 0.07 + ip));
            thickLine(sx, sy, ex, ey, 0.6f + 0.4f * (float)pulse, hcr, hcg, hcb, alpha);
        }

        // ============================================================
        //  4. OUTER CLUSTER — slower counter-rotating lines
        // ============================================================
        int outerCount = 3 + count;
        float outerR = baseLen * 0.85f;
        for (int i = 0; i < outerCount; i++) {
            double op = phase + (i * Math.PI * 2) / outerCount + Math.PI / outerCount;
            double spin = frame * -0.04 + op; // counter-rotate
            double swing = Math.cos(frame * 0.08 + op) * 0.6 + Math.sin(frame * 0.12 + op) * 0.5;
            double la = spin + swing;
            float h = outerR * (float)(0.55 + 0.45 * Math.cos(frame * 0.07 + i));
            float sx = bx - (float)Math.cos(la) * h;
            float sy = by - (float)Math.sin(la) * h;
            float ex = bx + (float)Math.cos(la) * h;
            float ey = by + (float)Math.sin(la) * h;
            int alpha = 80 + (int)(70 * Math.cos(frame * 0.08 + op));
            thickLine(sx, sy, ex, ey, 1.0f + 0.5f * (float)pulseFast, cr, cg, cb, alpha);
        }

        // ============================================================
        //  5. ZIGZAG BOLT LINES — jagged energy arcs
        // ============================================================
        int boltCount = 2 + count / 2;
        for (int b = 0; b < boltCount; b++) {
            double bp = phase + b * Math.PI / boltCount + frame * 0.035;
            drawBolt(bx, by, bp, outerR * 1.3f, hcr, hcg, hcb, frame, (long)(id + b * 37));
        }

        // ============================================================
        //  6. HALO ARC SEGMENTS — curved fragments orbiting
        // ============================================================
        int arcCount = 3 + count;
        float arcR = outerR * 1.35f * (float)pulse;
        for (int a = 0; a < arcCount; a++) {
            double ap = phase + (a * Math.PI * 2) / arcCount + frame * 0.03 * (a % 2 == 0 ? 1 : -1);
            double arcLen = Math.toRadians(20 + (a * 13) % 40);
            drawArc(bx, by, arcR, ap, arcLen, cr, cg, cb, 60 + a * 8);
        }

        // ============================================================
        //  7. FLYING SPARKS — particles ejected from line tips
        // ============================================================
        int sparkCount = 8 + count * 3;
        for (int s = 0; s < sparkCount; s++) {
            long ss = id * 7919 + s * 6364136223846793005L;
            double sa = phase + ((ss & 0xFFFF) / 65535.0) * Math.PI * 2;
            double sDist = baseLen * (0.3 + ((ss >> 16) & 0x7FFF) / 65535.0 * 1.5);
            double sLife = Math.sin(frame * 0.13 + s * 0.7 + phase) * 0.5 + 0.5;
            sDist += sLife * baseLen * 0.6;
            float sx = bx + (float)Math.cos(sa) * (float)sDist;
            float sy = by + (float)Math.sin(sa) * (float)sDist;
            float sr = 0.6f + (s & 3) * 0.3f;
            int saAlpha = (int)(40 + sLife * 120);
            fillCircle(sx, sy, sr, hcr, hcg, hcb, saAlpha);
        }

        // ============================================================
        //  8. DIRECTION VECTOR ARROWS — chevrons pointing along angle
        // ============================================================
        int chevronCount = 2 + count / 2;
        for (int cv = 0; cv < chevronCount; cv++) {
            double cvDist = baseLen * (0.8 + cv * 0.9);
            double cvPhase = frame * 0.06 + cv * 1.8 + phase;
            double cvOsc = Math.sin(cvPhase) * baseLen * 0.5;
            double cvX = bx + Math.sin(angle) * (cvDist + cvOsc);
            double cvY = by - Math.cos(angle) * (cvDist + cvOsc);
            // small V chevron
            double cvSize = 2.5 + Math.abs(Math.cos(cvPhase)) * 2.0;
            double cvBase = angle - Math.PI / 2;
            double cvA1 = cvBase + Math.toRadians(140);
            double cvA2 = cvBase - Math.toRadians(140);
            int cvAlpha = 50 + (int)(Math.abs(Math.sin(cvPhase)) * 80);
            float cvx1 = (float)(cvX + Math.cos(cvA1) * cvSize);
            float cvy1 = (float)(cvY + Math.sin(cvA1) * cvSize);
            float cvx2 = (float)(cvX + Math.cos(cvA2) * cvSize);
            float cvy2 = (float)(cvY + Math.sin(cvA2) * cvSize);
            line((float)cvX, (float)cvY, cvx1, cvy1, hcr, hcg, hcb, cvAlpha);
            line((float)cvX, (float)cvY, cvx2, cvy2, hcr, hcg, hcb, cvAlpha);
        }

        // ============================================================
        //  9. FLASHING CONNECTORS — lines bridging inner/outer
        // ============================================================
        int connCount = 3 + count;
        for (int c = 0; c < connCount; c++) {
            double cp = phase + c * Math.PI * 2 / connCount + frame * 0.05;
            double flash = Math.abs(Math.sin(frame * 0.14 + c * 1.3 + phase));
            if (flash < 0.6) continue;
            float cInnerX = bx + (float)Math.cos(cp) * innerR;
            float cInnerY = by + (float)Math.sin(cp) * innerR;
            float cOuterX = bx + (float)Math.cos(cp) * outerR * 1.3f;
            float cOuterY = by + (float)Math.sin(cp) * outerR * 1.3f;
            int cAlpha = (int)(flash * 150);
            thickLine(cInnerX, cInnerY, cOuterX, cOuterY, 0.5f, hcr, hcg, hcb, cAlpha);
        }

        // ============================================================
        //  10. CORE — layered bright center
        // ============================================================
        float coreR = 3f + 2f * (float)pulse;
        // outer glow
        fillCircle(bx, by, coreR * 1.8f, cr, cg, cb, 70);
        fillCircle(bx, by, coreR * 1.3f, cr, cg, cb, 110);
        // bright ring
        ring(bx, by, coreR * 1.1f, hcr, hcg, hcb, 180);
        // solid core
        fillCircle(bx, by, coreR, hcr, hcg, hcb, 220);
        fillCircle(bx, by, coreR * 0.4f, 255, 255, 255, 200);

        // ============================================================
        //  11. SPINNING RETICLE — 4 thin arms at quarter angles
        // ============================================================
        double retSpin = frame * 0.045 + phase;
        float retLen = outerR * 1.6f;
        for (int arm = 0; arm < 4; arm++) {
            double ra = retSpin + arm * Math.PI / 2;
            float rx1 = bx - (float)Math.cos(ra) * retLen * 0.45f;
            float ry1 = by - (float)Math.sin(ra) * retLen * 0.45f;
            float rx2 = bx + (float)Math.cos(ra) * retLen * 0.45f;
            float ry2 = by + (float)Math.sin(ra) * retLen * 0.45f;
            // tiny gaps at tips for a tech look
            float gap = retLen * 0.08f;
            float rx1b = bx - (float)Math.cos(ra) * (retLen * 0.45f - gap);
            float ry1b = by - (float)Math.sin(ra) * (retLen * 0.45f - gap);
            line(rx1, ry1, rx1b, ry1b, cr, cg, cb, 55);
            float rx2b = bx + (float)Math.cos(ra) * (retLen * 0.45f - gap);
            float ry2b = by + (float)Math.sin(ra) * (retLen * 0.45f - gap);
            line(rx2, ry2, rx2b, ry2b, cr, cg, cb, 55);
        }
    }

    // ---- zigzag bolt helper ----

    private static void drawBolt(float cx, float cy, double baseAngle, float length,
                                  int cr, int cg, int cb, long frame, long seed) {
        int segments = 4;
        float segLen = length / segments;
        float px = cx, py = cy;
        for (int i = 1; i <= segments; i++) {
            double jitterA = ((seed * (i * 7 + 1) + i * 131) & 0xFFFF) / 65535.0 * 1.4 - 0.7;
            double jitter = jitterA + Math.sin(frame * 0.2 + i + seed * 0.01) * 0.5;
            double a = baseAngle + jitter;
            float nx = px + (float)Math.cos(a) * segLen;
            float ny = py + (float)Math.sin(a) * segLen;
            int alpha = 90 + i * 15;
            thickLine(px, py, nx, ny, 0.7f + i * 0.1f, cr, cg, cb, alpha);
            px = nx;
            py = ny;
        }
    }

    // ---- arc segment helper ----

    private static void drawArc(float cx, float cy, float r, double startAngle,
                                 double arcLen, int cr, int cg, int cb, int alpha) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder b = tess.getBuffer();
        int segs = 8;
        b.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double a = startAngle + arcLen * i / segs;
            float x = cx + (float)Math.cos(a) * r;
            float y = cy + (float)Math.sin(a) * r;
            b.pos(x, y, 0).color(cr, cg, cb, alpha).endVertex();
        }
        tess.draw();
    }

    // ================================================================
    //  PRIMITIVES
    // ================================================================

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
