package com.omnia.movement.network;

import com.omnia.movement.MovementHandler;
import com.omnia.movement.MovementState;
import com.omnia.movement.OmniConfig;
import com.omnia.movement.PlayerStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * S→C 广播处理器（客户端专用）。
 * 服务端将此玩家的滑铲/飞扑状态广播给所有其他在线玩家。
 * 收到后更新本地对应玩家实体的碰撞箱与状态，用于第三人称渲染同步。
 */
@SideOnly(Side.CLIENT)
public class ClientBroadcastHandler implements IMessageHandler<PacketSlideBroadcast, IMessage> {

    @Override
    public IMessage onMessage(final PacketSlideBroadcast packet, MessageContext ctx) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            EntityPlayer otherPlayer = (EntityPlayer)
                    Minecraft.getMinecraft().theWorld.getEntityByID(packet.getPlayerId());

            if (otherPlayer == null) return;

            PlayerStateManager state = MovementHandler.getOrCreate(otherPlayer);
            MovementState newState = packet.getState();

            // 更新其他玩家的碰撞箱（第三人称同步）
            MovementHandler.adjustHitbox(otherPlayer, newState);

            // 更新状态
            switch (newState) {
                case SLIDING:
                    state.setState(MovementState.SLIDING);
                    break;
                case DIVING:
                    state.setState(MovementState.DIVING);
                    break;
                case LANDING:
                    state.setState(MovementState.LANDING);
                    state.setLandingTimer(OmniConfig.diveLandingDuration);
                    break;
                default:
                    state.setState(MovementState.NONE);
                    break;
            }

            if (OmniConfig.debugSync) {
                System.out.println("[OmniNetwork] CLIENT received broadcast " +
                        otherPlayer.getName() + " → " + newState);
            }
        });
        return null;
    }
}
