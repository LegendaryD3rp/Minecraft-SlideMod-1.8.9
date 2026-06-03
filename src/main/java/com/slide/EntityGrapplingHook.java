package com.slide;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.List;

/**
 * 抓钩实体。
 *
 * 状态机：
 *   FLYING    → 沿玩家视线方向飞行，检测碰撞
 *   ATTACHED  → 钩中目标，拉动玩家
 *   RETRACTING → 收回（快速飞回玩家手中）
 */
public class EntityGrapplingHook extends Entity {

    public enum State { FLYING, ATTACHED, RETRACTING }

    // ── 常量 ──
    private static final double FLY_SPEED = 2.5;           // 飞行速度 (blocks/tick)
    private static final double PULL_SPEED = 0.4;          // 拉动速度基数
    private static final double PULL_SPEED_MAX = 0.9;      // 最大拉动速度
    private static final double MIN_PULL_DIST = 2.0;       // 最小拉动距离（近了自动松钩）
    private static final double MAX_RANGE = 40.0;          // 最大射程
    private static final int RETRACT_SPEED = 3;            // 回收速度 (blocks/tick)
    private static final int MAX_LIFETIME = 200;            // 最大存活 tick（10秒）

    private EntityPlayer shooter;
    private State currentState = State.FLYING;
    private int lifetime = 0;
    private double attachedX, attachedY, attachedZ;       // 钩中位置
    private int shooterId = -1;

    public EntityGrapplingHook(World world) {
        super(world);
        setSize(0.25F, 0.25F);
        ignoreFrustumCheck = true;
    }

    public EntityGrapplingHook(World world, EntityPlayer shooter) {
        super(world);
        this.shooter = shooter;
        this.shooterId = shooter.getEntityId();
        setSize(0.25F, 0.25F);
        ignoreFrustumCheck = true;

        // 从玩家眼睛位置发射
        Vec3 look = shooter.getLookVec();
        double eyeY = shooter.posY + shooter.getEyeHeight();

        // 初始位置：玩家眼睛前方 0.5 格
        this.posX = shooter.posX + look.xCoord * 0.5;
        this.posY = eyeY + look.yCoord * 0.5;
        this.posZ = shooter.posZ + look.zCoord * 0.5;
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        // 速度方向 = 视线方向 * 飞行速度
        this.motionX = look.xCoord * FLY_SPEED;
        this.motionY = look.yCoord * FLY_SPEED;
        this.motionZ = look.zCoord * FLY_SPEED;
    }

    @Override
    protected void entityInit() {}

    @Override
    public void onUpdate() {
        super.onUpdate();
        lifetime++;

        if (shooter == null) {
            // 尝试找回 shooter
            if (shooterId != -1 && worldObj.getEntityByID(shooterId) instanceof EntityPlayer) {
                shooter = (EntityPlayer) worldObj.getEntityByID(shooterId);
            }
        }

        if (shooter == null || shooter.isDead || lifetime > MAX_LIFETIME) {
            setDead();
            return;
        }

        // 距离检测
        double dist = getDistanceToEntity(shooter);

        switch (currentState) {
            case FLYING:
                updateFlying(dist);
                break;
            case ATTACHED:
                updateAttached(dist);
                break;
            case RETRACTING:
                updateRetracting(dist);
                break;
        }

        // 更新碰撞箱位置
        setPosition(posX, posY, posZ);
    }

    // ──────────────────────────────────────────────
    //  状态更新
    // ──────────────────────────────────────────────

    private void updateFlying(double distToShooter) {
        // 超距 → 回收
        if (distToShooter > MAX_RANGE) {
            retract();
            return;
        }

        // 移动
        this.posX += this.motionX;
        this.posY += this.motionY;
        this.posZ += this.motionZ;

        // 重力影响（轻微下坠）
        this.motionY -= 0.03;

        // 空气阻力
        this.motionX *= 0.99;
        this.motionY *= 0.99;
        this.motionZ *= 0.99;

        // 方块碰撞检测
        BlockPos blockPos = new BlockPos(this);
        IBlockState blockState = worldObj.getBlockState(blockPos);
        Block block = blockState.getBlock();

        if (!worldObj.isAirBlock(blockPos) && block.isCollidable()) {
            AxisAlignedBB blockBB = block.getCollisionBoundingBox(worldObj, blockPos, blockState);
            if (blockBB != null && blockBB.offset(blockPos.getX(), blockPos.getY(), blockPos.getZ()).isVecInside(new Vec3(posX, posY, posZ))) {
                // 钩中方块
                attachToBlock(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                return;
            }
        }

        // 实体碰撞检测
        AxisAlignedBB hookBB = getEntityBoundingBox().expand(0.3, 0.3, 0.3);
        List<Entity> entities = worldObj.getEntitiesWithinAABBExcludingEntity(this, hookBB);
        for (Entity entity : entities) {
            if (entity == shooter) continue;
            if (entity instanceof EntityLivingBase) {
                attachToEntity(entity);
                return;
            }
        }

        // 设置上一帧位置（用于渲染）
        this.prevPosX = this.posX - this.motionX;
        this.prevPosY = this.posY - this.motionY;
        this.prevPosZ = this.posZ - this.motionZ;
    }

    private void updateAttached(double distToShooter) {
        // 玩家太近或松手 → 松钩
        if (distToShooter < MIN_PULL_DIST) {
            setDead();
            return;
        }

        // 拉动玩家
        double dx = attachedX - shooter.posX;
        double dy = attachedY - shooter.posY;
        double dz = attachedZ - shooter.posZ;

        // 距离越远拉力越大，但有限速
        double pullDist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        double speed = Math.min(PULL_SPEED + pullDist * 0.02, PULL_SPEED_MAX);

        // 归一化方向
        dx /= pullDist;
        dy /= pullDist;
        dz /= pullDist;

        // 施加速度（平滑拉动）
        shooter.motionX += dx * speed * 0.15;
        shooter.motionY += dy * speed * 0.15;
        shooter.motionZ += dz * speed * 0.15;

        // 限制垂直速度（防止无限上升）
        shooter.motionY = Math.min(shooter.motionY, 1.2);

        // 标记玩家已落地（防止摔落伤害）
        shooter.fallDistance = 0;

        // 更新钩子位置到锚点（保持不变）
        this.posX = attachedX;
        this.posY = attachedY;
        this.posZ = attachedZ;
        this.prevPosX = attachedX;
        this.prevPosY = attachedY;
        this.prevPosZ = attachedZ;
    }

    private void updateRetracting(double distToShooter) {
        // 飞回玩家手中
        double dx = shooter.posX - posX;
        double dy = (shooter.posY + shooter.getEyeHeight() * 0.5) - posY;
        double dz = shooter.posZ - posZ;
        double d = Math.sqrt(dx*dx + dy*dy + dz*dz);

        if (d < 1.0 || lifetime > MAX_LIFETIME - 10) {
            setDead();
            return;
        }

        dx /= d;
        dy /= d;
        dz /= d;

        motionX = dx * RETRACT_SPEED;
        motionY = dy * RETRACT_SPEED;
        motionZ = dz * RETRACT_SPEED;

        posX += motionX;
        posY += motionY;
        posZ += motionZ;

        prevPosX = posX - motionX;
        prevPosY = posY - motionY;
        prevPosZ = posZ - motionZ;
    }

    // ──────────────────────────────────────────────
    //  行为
    // ──────────────────────────────────────────────

    private void attachToBlock(double x, double y, double z) {
        currentState = State.ATTACHED;
        attachedX = x;
        attachedY = y;
        attachedZ = z;
        motionX = 0;
        motionY = 0;
        motionZ = 0;
        posX = x;
        posY = y;
        posZ = z;
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;

        // 音效：钩中
        worldObj.playSoundAtEntity(this, "random.bowhit", 0.6F, 1.2F);
    }

    private void attachToEntity(Entity target) {
        currentState = State.ATTACHED;
        attachedX = target.posX;
        attachedY = target.posY + target.height * 0.5;
        attachedZ = target.posZ;
        motionX = 0;
        motionY = 0;
        motionZ = 0;

        // 跟随目标
        target.setInWeb();  // 缓速目标（小效果）
    }

    public void retract() {
        currentState = State.RETRACTING;
    }

    public State getState() { return currentState; }

    public EntityPlayer getShooter() { return shooter; }

    /** 获取指定玩家的活跃抓钩 */
    public static EntityGrapplingHook getActiveHook(EntityPlayer player) {
        if (player == null || player.worldObj == null) return null;
        for (Entity entity : player.worldObj.loadedEntityList) {
            if (entity instanceof EntityGrapplingHook) {
                EntityGrapplingHook hook = (EntityGrapplingHook) entity;
                if (hook.shooter == player && !hook.isDead) {
                    return hook;
                }
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────
    //  NBT（不需要持久化，但必须实现）
    // ──────────────────────────────────────────────

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {}
    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {}
}
