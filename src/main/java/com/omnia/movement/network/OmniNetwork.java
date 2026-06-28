package com.omnia.movement.network;

import com.omnia.movement.MovementHandler;
import com.omnia.movement.MovementState;
import com.omnia.movement.OmniConfig;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 网络注册与处理器。
 *
 * 包类型：
 *   PacketSlideState —— C→S，客户端发状态给服务端
 *   PacketSlideBroadcast —— S→C，服务端广播给其他客户端
 *
 * 注意：客户端广播处理器 ClientBroadcastHandler 独立为文件，
 * 避免 OmniNetwork 类在服务端加载 net.minecraft.client.Minecraft。
 */
public class OmniNetwork {

    public static final SimpleNetworkWrapper INSTANCE =
            NetworkRegistry.INSTANCE.newSimpleChannel("omni_move");

    private static int nextId = 0;

    public static void init() {
        // C→S 客户端状态上行（服务端注册）
        INSTANCE.registerMessage(
                SlideStateHandler.class,
                PacketSlideState.class,
                nextId++,
                Side.SERVER);

        // S→C 状态广播下行（客户端注册）
        INSTANCE.registerMessage(
                ClientBroadcastHandler.class,
                PacketSlideBroadcast.class,
                nextId++,
                Side.CLIENT);
    }

    // ========== 便捷发送 ==========

    public static void sendToServer(IMessage msg) {
        INSTANCE.sendToServer(msg);
    }

    public static void sendTo(IMessage msg, EntityPlayerMP player) {
        INSTANCE.sendTo(msg, player);
    }

    public static void sendToAll(IMessage msg) {
        INSTANCE.sendToAll(msg);
    }

    // ========== C→S 处理器（服务端安全） ==========

    public static class SlideStateHandler implements IMessageHandler<PacketSlideState, IMessage> {

        @Override
        public IMessage onMessage(PacketSlideState packet, MessageContext ctx) {
            final EntityPlayerMP serverPlayer = ctx.getServerHandler().playerEntity;

            serverPlayer.getServerForPlayer().addScheduledTask(() -> {
                // 更新服务端状态
                MovementHandler.onServerPacket(serverPlayer, packet);

                // 广播给其他所有在线玩家
                MovementState state = packet.getState();
                PacketSlideBroadcast broadcast = new PacketSlideBroadcast(
                        serverPlayer.getEntityId(), state);

                for (EntityPlayerMP other : serverPlayer.getServerForPlayer()
                        .getMinecraftServer().getConfigurationManager()
                        .getPlayerList()) {
                    if (other != serverPlayer) {
                        sendTo(broadcast, other);
                    }
                }

                if (OmniConfig.debugSync) {
                    System.out.println("[OmniNetwork] SERVER broadcast " +
                            serverPlayer.getName() + " → " + state);
                }
            });

            return null;
        }
    }
}
