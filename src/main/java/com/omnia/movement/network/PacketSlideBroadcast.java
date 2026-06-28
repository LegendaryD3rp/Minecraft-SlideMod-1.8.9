package com.omnia.movement.network;

import com.omnia.movement.MovementState;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * S→C 广播包：服务端将某玩家的滑铲/飞扑状态广播给所有其他在线玩家。
 */
public class PacketSlideBroadcast implements IMessage {

    private int playerId;
    private MovementState state;

    public PacketSlideBroadcast() {}

    public PacketSlideBroadcast(int playerId, MovementState state) {
        this.playerId = playerId;
        this.state = state;
    }

    public int getPlayerId() { return playerId; }
    public MovementState getState() { return state; }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.playerId = buf.readInt();
        int ordinal = buf.readByte();
        if (ordinal >= 0 && ordinal < MovementState.values().length) {
            this.state = MovementState.values()[ordinal];
        } else {
            this.state = MovementState.NONE;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(playerId);
        buf.writeByte(state.ordinal());
    }
}
