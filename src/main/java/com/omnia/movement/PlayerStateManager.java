package com.omnia.movement;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;

/**
 * 玩家状态管理器。
 * 每个需要追踪状态的玩家实例对应一个 PlayerStateManager。
 * 本地客户端玩家 + 服务端玩家都需要。
 */
public class PlayerStateManager {

    private MovementState state = MovementState.NONE;
    private int stateTimer = 0;          // 当前状态已持续的 tick 数
    private int landingTimer = 0;        // 落地缓冲倒计时

    private double lockedMotionX = 0;    // 滑铲/飞扑锁定的水平方向
    private double lockedMotionZ = 0;
    private boolean directionLocked = false;

    // ====== 状态读写 ======

    public MovementState getState() { return state; }
    public void setState(MovementState s) {
        this.state = s;
        this.stateTimer = 0;
        if (s != MovementState.LANDING) {
            this.landingTimer = 0;
        }
    }

    public int getStateTimer() { return stateTimer; }
    public void tickTimer() { stateTimer++; }

    // ====== 滑铲专用 ======

    public boolean isSliding() { return state == MovementState.SLIDING; }

    // ====== 飞扑专用 ======

    public boolean isDiving() { return state == MovementState.DIVING; }
    public boolean isLanding() { return state == MovementState.LANDING; }

    public int getLandingTimer() { return landingTimer; }
    public void setLandingTimer(int t) { this.landingTimer = t; }
    public void decLandingTimer() { if (landingTimer > 0) landingTimer--; }

    // ====== 方向锁定 ======

    public void lockDirection(double mx, double mz) {
        this.lockedMotionX = mx;
        this.lockedMotionZ = mz;
        this.directionLocked = true;
    }

    public void unlockDirection() {
        this.lockedMotionX = 0;
        this.lockedMotionZ = 0;
        this.directionLocked = false;
    }

    public boolean isDirectionLocked() { return directionLocked; }
    public double getLockedMotionX() { return lockedMotionX; }
    public double getLockedMotionZ() { return lockedMotionZ; }

    /**
     * 根据玩家当前视线方向锁定运动方向。
     * 滑铲飞扑前调用，锁定后空中/地面不可转向。
     */
    public void lockDirectionFromLook(EntityPlayer player) {
        Vec3 look = player.getLookVec();
        this.lockedMotionX = look.xCoord;
        this.lockedMotionZ = look.zCoord;
        this.directionLocked = true;
    }

    // ====== 状态持久化 (NBT 存档) ======

    public void saveToNBT(NBTTagCompound tag) {
        tag.setString("omniState", state.name());
        tag.setInteger("omniStateTimer", stateTimer);
        tag.setInteger("omniLandingTimer", landingTimer);
    }

    public void loadFromNBT(NBTTagCompound tag) {
        if (tag.hasKey("omniState")) {
            try {
                this.state = MovementState.valueOf(tag.getString("omniState"));
            } catch (Exception e) {
                this.state = MovementState.NONE;
            }
            this.stateTimer = tag.getInteger("omniStateTimer");
            this.landingTimer = tag.getInteger("omniLandingTimer");
        }
    }

    // ====== 辅助 ======

    public boolean canSlide() {
        return state == MovementState.NONE;
    }

    public boolean canDive() {
        return state == MovementState.NONE;
    }

    /** 滑铲取消检查 */
    public boolean canSlideCancel() {
        return OmniConfig.slideCancelEnabled && isSliding() && stateTimer >= 2;
    }

    @Override
    public String toString() {
        return state + "[" + stateTimer + "t] landing=" + landingTimer;
    }
}
