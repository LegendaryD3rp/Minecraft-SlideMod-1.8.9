package com.omnia.movement.network;

import com.omnia.movement.MovementState;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * C→S 上行包：客户端发送当前运动状态给服务端。
 * 服务端据此更新碰撞箱、速度修饰器，并广播给其他客户端。
 */
public class PacketSlideState implements IMessage {

    private MovementState state;

    /** 反序列化用 */
    public PacketSlideState() {}

    public PacketSlideState(MovementState state) {
        this.state = state;
    }

    public MovementState getState() {
        return state;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        if (ordinal >= 0 && ordinal < MovementState.values().length) {
            this.state = MovementState.values()[ordinal];
        } else {
            this.state = MovementState.NONE;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(state.ordinal());
    }
}
