package com.slide;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 滑墙（Titanfall 风格）+ 二段跳。
 *
 * 滑墙：
 *   玩家空中且朝墙面前进时，自动吸附墙面并沿墙跑动。
 *   重力被抵消，WS 控制沿墙前进/后退。持续最长 wallRunMaxTicks。
 *   按跳跃 → 蹬墙跳（远离墙面）。
 *
 * 二段跳：
 *   离开地面后可再按跳跃一次。
 *   蹬墙跳后也可触发。
 */
@SideOnly(Side.CLIENT)
public class WallRunHandler {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // ── 滑墙状态 ──
    public static boolean isWallRunning = false;
    public static int wallDirX = 0;       // 墙的法线方向
    public static int wallDirZ = 0;
    private static int wallRunTicks = 0;

    // ── 二段跳状态 ──
    public static boolean hasDoubleJumped = false;

    // ── 内部追踪 ──
    private static boolean wasOnGround = true;
    private static boolean wasJumping = false;
    private static boolean wasInAir = false;       // 上一 tick 是否在空中
    private static double lastFallStartY = 0;      // 记录开始下落的高度

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != Phase.END) return;
        if (!SlideMod.config.enabled) return;

        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.isDead) return;
        if (player.capabilities.isCreativeMode || player.capabilities.isFlying) {
            reset();
            return;
        }

        boolean onGround = player.onGround;

        // ── 落地重置 ──
        if (onGround) {
            hasDoubleJumped = false;
            wallRunTicks = 0;
            if (isWallRunning) {
                isWallRunning = false;
                wallDirX = 0;
                wallDirZ = 0;
            }
        }

        // ── 与滑铲/飞扑互斥 ──
        if (SlideHandler.isSliding || SlideHandler.isDiving) {
            if (isWallRunning) {
                isWallRunning = false;
                wallDirX = 0;
                wallDirZ = 0;
            }
            wasOnGround = onGround;
            wasJumping = player.movementInput.jump;
            return;
        }

        // ── 滑墙检测 ──
        if (!onGround && !isWallRunning && SlideMod.config.wallRunEnabled) {
            checkForWall(player);
        }

        // ── 滑墙物理 ──
        if (isWallRunning) {
            if (!tickWallRun(player)) {
                // 滑墙结束
                isWallRunning = false;
                wallDirX = 0;
                wallDirZ = 0;
            }
        }

        // ── 蹬墙跳 ──
        boolean jumpNow = player.movementInput.jump;
        if (isWallRunning && jumpNow && !wasJumping) {
            wallJump(player);
        }

        // ── 二段跳 ──
        if (!onGround && !isWallRunning && SlideMod.config.doubleJumpEnabled
                && jumpNow && !wasJumping && !wasOnGround && !hasDoubleJumped) {
            doDoubleJump(player);
        }

        wasOnGround = onGround;
        wasJumping = jumpNow;
    }

    // ════════════════════════════════════════════
    //  滑墙检测
    // ════════════════════════════════════════════

    /** 检测玩家附近是否有墙，且玩家正朝墙运动 */
    private void checkForWall(EntityPlayerSP player) {
        // 必须有足够的速度才能上墙
        double hSpeed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        if (hSpeed < 0.08) return;

        BlockPos pos = player.getPosition();

        // 四个方向：北(-Z) 南(+Z) 西(-X) 东(+X)
        int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

        for (int[] d : dirs) {
            int dx = d[0], dz = d[1];

            // 检查这面墙是否紧贴玩家（检查三个高度）
            boolean validWall = false;
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos checkPos = pos.add(dx, dy, dz);
                IBlockState state = player.worldObj.getBlockState(checkPos);
                Block block = state.getBlock();
                if (block != Blocks.air && block.isFullBlock()) {
                    validWall = true;
                    break;
                }
                // fence / wall / 玻璃等非 fullBlock 但能站人的方块
                if (block != Blocks.air && block.isFullCube()) {
                    validWall = true;
                    break;
                }
            }
            if (!validWall) continue;

            // 检查玩家是否朝墙运动
            double vx = player.motionX;
            double vz = player.motionZ;
            double speed = Math.sqrt(vx * vx + vz * vz);
            if (speed < 0.05) continue;

            // 归一化速度方向
            double ndx = vx / speed;
            double ndz = vz / speed;
            // 与墙法线的点积 > 0.2 表示朝墙运动
            double dot = ndx * dx + ndz * dz;

            if (dot > 0.2) {
                // 启动滑墙
                wallDirX = dx;
                wallDirZ = dz;
                wallRunTicks = 0;
                isWallRunning = true;

                // 清掉朝墙的速度，改为沿墙方向
                projectMotionAlongWall(player);
                player.motionY = 0;
                return;
            }
        }
    }

    // ════════════════════════════════════════════
    //  滑墙物理（每 tick）
    // ════════════════════════════════════════════

    /** @return false 表示滑墙结束 */
    private boolean tickWallRun(EntityPlayerSP player) {
        wallRunTicks++;

        // 超时结束
        if (wallRunTicks > SlideMod.config.wallRunMaxTicks) {
            stopWallRun(player);
            return false;
        }

        // 检查墙还在不在
        BlockPos pos = player.getPosition();
        boolean wallStillThere = false;
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos checkPos = pos.add(wallDirX, dy, wallDirZ);
            IBlockState state = player.worldObj.getBlockState(checkPos);
            Block block = state.getBlock();
            if (block != Blocks.air && (block.isFullBlock() || block.isFullCube())) {
                wallStillThere = true;
                break;
            }
        }
        if (!wallStillThere) {
            stopWallRun(player);
            return false;
        }

        // 抵消重力
        player.motionY = 0;

        // 玩家沿墙移动（根据 WASD 投影到墙面）
        projectMotionAlongWall(player);

        // 粒子
        if (SlideMod.config.wallRunParticles && player.ticksExisted % 2 == 0) {
            spawnWallRunParticles(player);
        }

        return true;
    }

    /** 将玩家输入方向投影到墙面，设定 motionX/motionZ */
    private void projectMotionAlongWall(EntityPlayerSP player) {
        // 玩家视角水平方向
        double yaw = Math.toRadians(player.rotationYaw);
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);

        // 墙的法线
        double wallNX = wallDirX;
        double wallNZ = wallDirZ;

        // 视角方向投影到墙面（减去法线分量）
        double dot = lookX * wallNX + lookZ * wallNZ;
        double alongX = lookX - dot * wallNX;
        double alongZ = lookZ - dot * wallNZ;

        // 归一化
        double len = Math.sqrt(alongX * alongX + alongZ * alongZ);
        if (len > 0.001) {
            alongX /= len;
            alongZ /= len;
        } else {
            // 视角正好垂直于墙面，用一个默认方向
            alongX = -wallNZ;
            alongZ = wallNX;
            double len2 = Math.sqrt(alongX * alongX + alongZ * alongZ);
            if (len2 > 0.001) { alongX /= len2; alongZ /= len2; }
        }

        // 应用移动速度
        double speed = SlideMod.config.wallRunSpeed;
        player.motionX = alongX * speed;
        player.motionZ = alongZ * speed;
    }

    /** 滑墙结束：轻轻推离墙面 */
    private void stopWallRun(EntityPlayerSP player) {
        if (wallDirX != 0 || wallDirZ != 0) {
            player.motionX += wallDirX * 0.1;
            player.motionZ += wallDirZ * 0.1;
        }
        isWallRunning = false;
    }

    // ════════════════════════════════════════════
    //  蹬墙跳
    // ════════════════════════════════════════════

    private void wallJump(EntityPlayerSP player) {
        double hPower = SlideMod.config.wallJumpHorizontal;
        double vPower = SlideMod.config.wallJumpVertical;

        // 远离墙面
        player.motionX = -wallDirX * hPower;
        player.motionZ = -wallDirZ * hPower;
        player.motionY = vPower;

        isWallRunning = false;
        wallDirX = 0;
        wallDirZ = 0;
        wallRunTicks = 0;

        // 蹬墙跳后允许二段跳（如果还没用）
        // hasDoubleJumped 不重置，保留原状态
    }

    // ════════════════════════════════════════════
    //  二段跳
    // ════════════════════════════════════════════

    private void doDoubleJump(EntityPlayerSP player) {
        double hPower = SlideMod.config.doubleJumpHorizontal;
        double vPower = SlideMod.config.doubleJumpVertical;

        // 水平方向：朝当前视角方向
        double yaw = Math.toRadians(player.rotationYaw);
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);

        player.motionX += lookX * hPower;
        player.motionZ += lookZ * hPower;
        player.motionY = vPower;

        hasDoubleJumped = true;

        // 粒子效果
        if (SlideMod.config.enableParticles) {
            for (int i = 0; i < 6; i++) {
                double px = player.posX + (Math.random() - 0.5) * 0.5;
                double py = player.posY;
                double pz = player.posZ + (Math.random() - 0.5) * 0.5;
                double vx = (Math.random() - 0.5) * 0.2;
                double vy = Math.random() * 0.3;
                double vz = (Math.random() - 0.5) * 0.2;
                player.worldObj.spawnParticle(EnumParticleTypes.CLOUD, px, py, pz, vx, vy, vz);
            }
        }
    }

    // ════════════════════════════════════════════
    //  粒子效果
    // ════════════════════════════════════════════

    private void spawnWallRunParticles(EntityPlayerSP player) {
        double x = player.posX + wallDirX * 0.4;
        double y = player.posY + 0.5 + Math.random() * 1.0;
        double z = player.posZ + wallDirZ * 0.4;

        BlockPos groundPos = new BlockPos(
            player.posX + wallDirX,
            player.posY - 0.5,
            player.posZ + wallDirZ
        );

        IBlockState state = player.worldObj.getBlockState(groundPos);
        Block block = state.getBlock();
        if (block == Blocks.air || !block.isFullBlock()) {
            block = Blocks.stone;
        }

        // 使用 blockcrack 粒子模拟刮擦效果
        int blockId = Block.getIdFromBlock(block);
        int meta = block.getMetaFromState(state);
        player.worldObj.spawnParticle(
            EnumParticleTypes.BLOCK_CRACK,
            x, y, z,
            0, 0.02, 0,
            blockId + (meta << 12)
        );

        // 额外几个小粒子
        for (int i = 0; i < 2; i++) {
            player.worldObj.spawnParticle(
                EnumParticleTypes.BLOCK_CRACK,
                x + (Math.random() - 0.5) * 0.3,
                y + (Math.random() - 0.5) * 0.5,
                z + (Math.random() - 0.5) * 0.3,
                0, 0.01, 0,
                blockId + (meta << 12)
            );
        }
    }

    // ════════════════════════════════════════════
    //  重置
    // ════════════════════════════════════════════

    public static void reset() {
        isWallRunning = false;
        hasDoubleJumped = false;
        wallDirX = 0;
        wallDirZ = 0;
        wallRunTicks = 0;
        wasOnGround = true;
        wasJumping = false;
    }
}
