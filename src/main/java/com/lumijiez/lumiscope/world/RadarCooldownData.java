package com.lumijiez.lumiscope.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RadarCooldownData extends WorldSavedData {

    private static final String DATA_NAME = "lumiscope_cooldowns";
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public RadarCooldownData() {
        super(DATA_NAME);
    }

    public RadarCooldownData(String name) {
        super(name);
    }

    public static RadarCooldownData get(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) return null;
        RadarCooldownData data = (RadarCooldownData) storage.getOrLoadData(RadarCooldownData.class, DATA_NAME);
        if (data == null) {
            data = new RadarCooldownData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public long getCooldownEnd(UUID playerId) {
        Long end = cooldowns.get(playerId);
        if (end == null) return 0;
        if (end < System.currentTimeMillis()) {
            cooldowns.remove(playerId);
            markDirty();
            return 0;
        }
        return end;
    }

    public void setCooldown(UUID playerId, long endMs) {
        cooldowns.put(playerId, endMs);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        cooldowns.clear();
        NBTTagList list = nbt.getTagList("cooldowns", Constants.NBT.TAG_COMPOUND);
        long now = System.currentTimeMillis();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            UUID id = UUID.fromString(entry.getString("uuid"));
            long end = entry.getLong("end");
            if (end > now) {
                cooldowns.put(id, end);
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : cooldowns.entrySet()) {
            if (e.getValue() > now) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("uuid", e.getKey().toString());
                entry.setLong("end", e.getValue());
                list.appendTag(entry);
            }
        }
        nbt.setTag("cooldowns", list);
        return nbt;
    }
}
