package com.lumijiez.lumiscope.network.handlers;

import com.lumijiez.lumiscope.items.radars.RadarDevice;
import com.lumijiez.lumiscope.network.packets.RadarScanRequestPacket;
import com.lumijiez.lumiscope.network.packets.RadarScanResultPacket;
import com.lumijiez.lumiscope.network.records.RadarBlip;
import com.lumijiez.lumiscope.potions.PotionManager;
import com.lumijiez.lumiscope.util.PerlinNoise;
import com.lumijiez.lumiscope.world.RadarCooldownData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.*;

public class RadarNetworkHandler {

    private static final SimpleNetworkWrapper NETWORK =
            NetworkRegistry.INSTANCE.newSimpleChannel("lumiscope_radar");

    public enum ScanRange {
        LOCAL      (0, "Local",       5_000,       2 * 3600 * 1000L,        Items.DIAMOND,      16, 25.0),
        REGIONAL   (1, "Regional",    50_000,      2 * 3600 * 1000L,         Items.ENDER_PEARL,  16, 28.0),
        CONTINENTAL(2, "Continental", 250_000,     12 * 3600 * 1000L,        Items.ENDER_EYE,    64, 31.0),
        HEMISPHERIC(3, "Hemispheric", 1_000_000,   2 * 24 * 3600 * 1000L,    Items.GHAST_TEAR,   32, 34.0),
        GLOBAL     (4, "Global",      5_000_000,   7 * 24 * 3600 * 1000L,    Items.NETHER_STAR,  16, 37.0),
        ABSOLUTE   (5, "Absolute",    30_000_000,  14 * 24 * 3600 * 1000L,   Items.NETHER_STAR,  32, 40.0);

        public final byte ordinal;
        public final String label;
        public final int maxDistance;
        public final long cooldownMs;
        public final Item fuelItem;
        public final int fuelCount;
        public final double errorDegrees;

        ScanRange(int ordinal, String label, int maxDistance, long cooldownMs,
                  Item fuelItem, int fuelCount, double errorDegrees) {
            this.ordinal = (byte) ordinal;
            this.label = label;
            this.maxDistance = maxDistance;
            this.cooldownMs = cooldownMs;
            this.fuelItem = fuelItem;
            this.fuelCount = fuelCount;
            this.errorDegrees = errorDegrees;
        }

        public static ScanRange fromOrdinal(byte ord) {
            for (ScanRange r : values()) {
                if (r.ordinal == ord) return r;
            }
            return LOCAL;
        }

        public static int count() { return values().length; }
    }

    public enum DistanceTier {
        VERY_CLOSE  (0, 0, 500,           "Very Close"),
        CLOSE       (1, 500, 2_000,        "Close"),
        MODERATE    (2, 2_000, 50_000,      "Moderate"),
        FAR         (3, 50_000, 500_000,     "Far"),
        VERY_FAR    (4, 500_000, 5_000_000,   "Very Far"),
        FAINT       (5, 5_000_000, Integer.MAX_VALUE, "Faint");

        public final byte id;
        public final int minDist;
        public final int maxDist;
        public final String label;

        DistanceTier(int id, int minDist, int maxDist, String label) {
            this.id = (byte) id;
            this.minDist = minDist;
            this.maxDist = maxDist;
            this.label = label;
        }
    }

    public static void registerMessages() {
        NETWORK.registerMessage(RadarScanRequestPacket.Handler.class,
                RadarScanRequestPacket.class, 0, Side.SERVER);
        NETWORK.registerMessage(RadarScanResultPacket.Handler.class,
                RadarScanResultPacket.class, 1, Side.CLIENT);
    }

    public static SimpleNetworkWrapper getNetworkChannel() { return NETWORK; }

    public static IMessage handleScanRequest(EntityPlayerMP player, byte rangeOrdinal) {
        ScanRange range = ScanRange.fromOrdinal(rangeOrdinal);

        if (player.isPotionActive(PotionManager.JAMMED_POTION_EFFECT)) {
            return new RadarScanResultPacket(
                    Collections.emptyList(),
                    RadarScanResultPacket.STATUS_JAMMED,
                    System.currentTimeMillis(), rangeOrdinal);
        }

        long now = System.currentTimeMillis();
        UUID pid = player.getUniqueID();
        RadarCooldownData cdData = RadarCooldownData.get(player.world);
        if (cdData != null) {
            long cdEnd = cdData.getCooldownEnd(pid);
            if (cdEnd > now) {
                return new RadarScanResultPacket(
                        Collections.emptyList(),
                        RadarScanResultPacket.STATUS_COOLDOWN,
                        now, rangeOrdinal);
            }
        }

        if (!consumeFuel(player, range.fuelItem, range.fuelCount)) {
            return new RadarScanResultPacket(
                    Collections.emptyList(),
                    RadarScanResultPacket.STATUS_NO_FUEL,
                    now, rangeOrdinal);
        }

        damageRadarDevice(player);

        if (cdData != null) {
            cdData.setCooldown(pid, now + range.cooldownMs);
        }

        List<RadarBlip> blips = scanForPlayers(player, range);

        return new RadarScanResultPacket(blips, RadarScanResultPacket.STATUS_SUCCESS,
                now, rangeOrdinal);
    }

    private static List<RadarBlip> scanForPlayers(EntityPlayerMP scanner, ScanRange range) {
        List<RadarBlip> raw = new ArrayList<>();

        for (EntityPlayerMP target : scanner.getServer().getPlayerList().getPlayers()) {
            if (!shouldInclude(scanner, target)) continue;
            double dist = scanner.getDistance(target);
            if (dist > range.maxDistance) continue;

            byte tier = getTier(dist);
            double rawAngle = calculateRawAngle(scanner, target);
            double noisyAngle = applyError(rawAngle, range.errorDegrees, scanner, target);
            double rad = Math.toRadians(normalizeAngle(noisyAngle));
            raw.add(new RadarBlip(rad, tier, (byte) 1));
        }

        return mergeNearby(raw);
    }

    private static boolean shouldInclude(EntityPlayerMP scanner, EntityPlayerMP target) {
        if (target.equals(scanner)) return false;
        if (scanner.dimension != target.dimension) return false;
        if (target.isPotionActive(PotionManager.JAMMED_POTION_EFFECT)) return false;
        return true;
    }

    private static double calculateRawAngle(EntityPlayerMP from, EntityPlayerMP to) {
        double dx = to.posX - from.posX;
        double dz = to.posZ - from.posZ;
        double a = MathHelper.atan2(dz, dx) * (180.0 / Math.PI) - 90.0;
        if (a < 0) a += 360.0;
        return (a + 180.0) % 360.0;
    }

    private static double normalizeAngle(double a) {
        return ((a % 360.0) + 360.0) % 360.0;
    }

    private static double applyError(double base, double maxError,
                                      EntityPlayerMP scanner, EntityPlayerMP target) {
        double t = System.currentTimeMillis() / 1000.0;
        double h = Math.abs((scanner.getUniqueID().hashCode() * 31L
                + target.getUniqueID().hashCode()) % 10000) / 10000.0;

        double split = (PerlinNoise.noise(t * 0.3 + h * 77.0) + 1.0) / 2.0;
        double totalWindow = maxError * 2.0;
        double leftMax  = split * totalWindow;
        double rightMax = (1.0 - split) * totalWindow;

        double pos = (PerlinNoise.noise(t * 0.7 + h * 131.0) + 1.0) / 2.0;
        double err = -leftMax + pos * totalWindow;

        return base + err;
    }

    private static byte getTier(double dist) {
        for (DistanceTier t : DistanceTier.values()) {
            if (dist >= t.minDist && dist < t.maxDist) return t.id;
        }
        return DistanceTier.FAINT.id;
    }

    public static String getTierLabel(byte id) {
        for (DistanceTier t : DistanceTier.values()) {
            if (t.id == id) return t.label;
        }
        return "???";
    }

    private static final double MERGE_ANGLE = Math.toRadians(25.0);

    private static List<RadarBlip> mergeNearby(List<RadarBlip> raw) {
        if (raw.isEmpty()) return raw;
        List<RadarBlip> out = new ArrayList<>();
        boolean[] used = new boolean[raw.size()];

        for (int i = 0; i < raw.size(); i++) {
            if (used[i]) continue;
            RadarBlip a = raw.get(i);
            byte count = a.playerCount;
            double sumDir = a.direction;
            byte best = a.distanceTier;

            for (int j = i + 1; j < raw.size(); j++) {
                if (used[j]) continue;
                RadarBlip b = raw.get(j);
                double diff = Math.abs(normRad(a.direction - b.direction));
                if (diff < MERGE_ANGLE || diff > Math.PI * 2 - MERGE_ANGLE) {
                    used[j] = true;
                    count++;
                    sumDir += b.direction;
                    if (b.distanceTier < best) best = b.distanceTier;
                }
            }
            out.add(new RadarBlip(sumDir / count, best, count));
        }
        return out;
    }

    private static double normRad(double r) {
        while (r < 0) r += Math.PI * 2;
        while (r >= Math.PI * 2) r -= Math.PI * 2;
        return r;
    }

    private static boolean consumeFuel(EntityPlayerMP player, Item fuelItem, int count) {
        NonNullList<ItemStack> inv = player.inventory.mainInventory;
        int found = 0;
        for (ItemStack s : inv) {
            if (!s.isEmpty() && s.getItem() == fuelItem) found += s.getCount();
        }
        if (found < count) return false;

        int remaining = count;
        for (int i = 0; i < inv.size() && remaining > 0; i++) {
            ItemStack s = inv.get(i);
            if (!s.isEmpty() && s.getItem() == fuelItem) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
                if (s.isEmpty()) inv.set(i, ItemStack.EMPTY);
            }
        }
        return true;
    }

    private static void damageRadarDevice(EntityPlayerMP player) {
        ItemStack mh = player.getHeldItemMainhand();
        ItemStack oh = player.getHeldItemOffhand();
        if (mh.getItem() instanceof RadarDevice) mh.damageItem(1, player);
        else if (oh.getItem() instanceof RadarDevice) oh.damageItem(1, player);
    }

    public static ScanRange getRange(byte ord) { return ScanRange.fromOrdinal(ord); }
    public static int getRangeCount() { return ScanRange.count(); }
}
