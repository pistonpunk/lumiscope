package com.lumijiez.lumiscope.network.packets;

import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class RadarScanRequestPacket implements IMessage {

    public byte rangeOrdinal;

    public RadarScanRequestPacket() {}

    public RadarScanRequestPacket(byte rangeOrdinal) {
        this.rangeOrdinal = rangeOrdinal;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(rangeOrdinal);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        rangeOrdinal = buf.readByte();
    }

    public static class Handler implements IMessageHandler<RadarScanRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(RadarScanRequestPacket message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                IMessage reply = RadarNetworkHandler.handleScanRequest(
                        ctx.getServerHandler().player, message.rangeOrdinal);
                RadarNetworkHandler.getNetworkChannel().sendTo(reply, ctx.getServerHandler().player);
            });
            return null;
        }
    }
}
