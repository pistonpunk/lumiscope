package com.lumijiez.lumiscope.commands;

import com.lumijiez.lumiscope.world.RadarCooldownData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class ClearRadarCooldownsCommand extends CommandBase {

    @Override
    public String getName() {
        return "clearradarcountdowns";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/clearradarcountdowns";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        RadarCooldownData data = RadarCooldownData.get(server.getWorld(0));
        if (data != null) {
            data.clearAll();
            sender.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "All radar cooldowns cleared."));
        } else {
            sender.sendMessage(new TextComponentString(
                    TextFormatting.RED + "Failed to access cooldown data."));
        }
    }
}
