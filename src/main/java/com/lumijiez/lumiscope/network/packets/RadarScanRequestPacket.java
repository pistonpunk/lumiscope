package com.lumijiez.lumiscope.network.packets;

import com.lumijiez.lumiscope.network.handlers.RadarNetworkHandler;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class RadarScanRequestPacket implements IMessage {

    public byte rangeOrdinal;
    public boolean query;

    public RadarScanRequestPacket() {}

    public RadarScanRequestPacket(byte rangeOrdinal) {
        this.rangeOrdinal = rangeOrdinal;
        this.query = false;
    }

    public RadarScanRequestPacket(byte rangeOrdinal, boolean query) {
        this.rangeOrdinal = rangeOrdinal;
        this.query = query;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(rangeOrdinal);
        buf.writeBoolean(query);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        rangeOrdinal = buf.readByte();
        query = buf.readBoolean();
    }

    public static class Handler implements IMessageHandler<RadarScanRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(RadarScanRequestPacket message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                IMessage reply = RadarNetworkHandler.handleScanRequest(
                        ctx.getServerHandler().player, message.rangeOrdinal, message.query);
                RadarNetworkHandler.getNetworkChannel().sendTo(reply, ctx.getServerHandler().player);
            });
            return null;
        }
    }
}
