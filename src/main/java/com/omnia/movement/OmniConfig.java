package com.omnia.movement;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 全局配置。所有参数均可通过配置文件 / 游戏内 Mod Options 调整。
 * 客户端与服务端共用同一份默认值，但各自加载本地文件。
 */
public class OmniConfig {

    // ========== 滑铲 ==========
    public static String slideKey = "C";
    public static double slideSpeedMultiplier = 1.4;
    public static int slideDurationTicks = 10;           // 0.5秒
    public static double slideFriction = 0.96;           // 每tick速度衰减
    public static double slideHitboxHeight = 1.0;        // 滑铲碰撞箱高度
    public static double slideEyeHeight = 0.85;          // 滑铲眼睛高度

    // ========== 飞扑 ==========
    public static String diveKey = "LCONTROL";
    public static double diveSpeedMultiplier = 1.8;      // 飞扑水平初速度倍率
    public static double diveVerticalVelocity = 0.3;     // 飞扑垂直初速度
    public static double diveHitboxHeight = 0.6;         // 空中碰撞箱高度
    public static double diveEyeHeight = 0.4;            // 飞扑眼睛高度
    public static int diveLandingDuration = 10;           // 落地缓冲 tick 数 (0.5秒)

    // ========== 落地缓冲限制 ==========
    public static double landingSpeedReduction = 0.5;     // 缓冲期减速倍率
    public static boolean landingBlockJump = true;        // 缓冲期禁止跳跃
    public static boolean landingBlockSprint = true;      // 缓冲期禁止冲刺
    public static boolean landingAllowAttack = true;      // 缓冲期允许攻击
    public static boolean landingBlockSlide = true;       // 缓冲期禁止再次滑铲
    public static boolean landingBlockDive = true;        // 缓冲期禁止再次飞扑

    // ========== 滑铲取消 ==========
    public static boolean slideCancelEnabled = true;      // 是否启用滑铲取消
    public static double slideCancelSpeedRetention = 0.95; // 取消后速度保留比例

    // ========== 飞扑空中 ==========
    public static double diveAirFriction = 0.99;          // 空中水平速度衰减

    // ========== 坡度 ==========
    public static double downSlopeSpeedBoost = 0.03;      // 下坡每tick速度加成
    public static double downSlopeVerticalBoost = 0.02;   // 下坡垂直助推

    // ========== 多人同步 ==========
    public static boolean debugSync = false;              // 调试用，打印同步日志

    // ========== 滑墙 ==========
    public static boolean wallRunEnabled = true;           // 是否启用滑墙
    public static int wallRunMaxTicks = 40;                 // 单次滑墙最长 tick (2秒)
    public static double wallRunSpeed = 0.3;                // 滑墙水平速度
    public static double wallGravityReduction = 0.0;        // 滑墙时重力倍率（0=无重力）
    public static double wallJumpHorizontalStrength = 0.5;  // 蹬墙跳水平力度
    public static double wallJumpVerticalStrength = 0.6;    // 蹬墙跳垂直力度

    // ========== 滑墙视角倾斜 ==========
    public static double wallRunCameraTilt = 15.0;          // 滑墙时视角倾斜角度（度）
    public static double wallRunTiltSmoothSpeed = 8.0;      // 视角倾斜过渡速度

    // ========== PhD Flopper ==========
    public static boolean phdFlopperEnabled = true;        // 是否启用 PhD Flopper
    public static double phdExplosionStrengthPerBlock = 1.5; // 每格坠落高度的爆炸强度
    public static double phdMaxExplosionStrength = 5.0;    // 最大爆炸强度
    public static boolean phdDestroyBlocks = false;        // 爆炸是否破坏方块

    public static void load(Configuration config) {
        try {
            config.load();

            // ---- 滑铲 ----
            slideKey = config.getString("slideKey", "slide", "C",
                    "滑铲按键名 (参考 LWJGL KeyBoard.getKeyName)");
            slideSpeedMultiplier = config.getFloat("slideSpeedMultiplier",
                    "slide", 1.4F, 0.5F, 5.0F, "滑铲速度倍率（相对冲刺速度）");
            slideDurationTicks = config.getInt("slideDurationTicks",
                    "slide", 10, 2, 100, "滑铲持续 tick 数 (20 tick = 1秒)");
            slideFriction = config.getFloat("slideFriction",
                    "slide", 0.96F, 0.5F, 1.0F, "滑铲每tick速度衰减系数。越小停越快");
            slideHitboxHeight = config.getFloat("slideHitboxHeight",
                    "slide", 1.0F, 0.2F, 1.8F, "滑铲碰撞箱高度");
            slideEyeHeight = config.getFloat("slideEyeHeight",
                    "slide", 0.85F, 0.1F, 1.6F, "滑铲眼睛高度");

            // ---- 飞扑 ----
            diveKey = config.getString("diveKey", "dive", "LCONTROL",
                    "飞扑按键名 (LWJGL KeyBoard.getKeyName)");
            diveSpeedMultiplier = config.getFloat("diveSpeedMultiplier",
                    "dive", 1.8F, 0.5F, 5.0F, "飞扑水平初速度倍率");
            diveVerticalVelocity = config.getFloat("diveVerticalVelocity",
                    "dive", 0.3F, 0.0F, 1.0F, "飞扑垂直初速度");
            diveHitboxHeight = config.getFloat("diveHitboxHeight",
                    "dive", 0.6F, 0.2F, 1.8F, "空中碰撞箱高度");
            diveEyeHeight = config.getFloat("diveEyeHeight",
                    "dive", 0.4F, 0.1F, 1.6F, "飞扑眼睛高度");
            diveLandingDuration = config.getInt("diveLandingDuration",
                    "dive", 10, 0, 100, "落地缓冲 tick 数");

            // ---- 落地缓冲 ----
            landingSpeedReduction = config.getFloat("landingSpeedReduction",
                    "landing", 0.5F, 0.0F, 1.0F, "缓冲期减速倍率");
            landingBlockJump = config.getBoolean("landingBlockJump",
                    "landing", true, "缓冲期禁止跳跃");
            landingBlockSprint = config.getBoolean("landingBlockSprint",
                    "landing", true, "缓冲期禁止冲刺");
            landingAllowAttack = config.getBoolean("landingAllowAttack",
                    "landing", true, "缓冲期允许攻击");
            landingBlockSlide = config.getBoolean("landingBlockSlide",
                    "landing", true, "缓冲期禁止再次滑铲");
            landingBlockDive = config.getBoolean("landingBlockDive",
                    "landing", true, "缓冲期禁止再次飞扑");

            // ---- 滑铲取消 ----
            slideCancelEnabled = config.getBoolean("slideCancelEnabled",
                    "slideCancel", true, "是否启用滑铲取消");
            slideCancelSpeedRetention = config.getFloat("slideCancelSpeedRetention",
                    "slideCancel", 0.95F, 0.0F, 1.0F, "取消后速度保留比例");

            // ---- 飞扑空中 ----
            diveAirFriction = config.getFloat("diveAirFriction",
                    "dive", 0.99F, 0.9F, 1.0F, "空中水平速度每tick衰减");

            // ---- 坡度 ----
            downSlopeSpeedBoost = config.getFloat("downSlopeSpeedBoost",
                    "slope", 0.03F, 0.0F, 0.5F, "下坡每tick水平速度加成");
            downSlopeVerticalBoost = config.getFloat("downSlopeVerticalBoost",
                    "slope", 0.02F, 0.0F, 0.5F, "下坡垂直助推");

            // ---- 调试 ----
            debugSync = config.getBoolean("debugSync",
                    "debug", false, "打印同步调试日志");

            // ---- 滑墙 ----
            wallRunEnabled = config.getBoolean("wallRunEnabled",
                    "wall_run", true, "是否启用滑墙");
            wallRunMaxTicks = config.getInt("wallRunMaxTicks",
                    "wall_run", 40, 5, 200, "单次滑墙最长 tick (20 tick = 1秒)");
            wallRunSpeed = config.getFloat("wallRunSpeed",
                    "wall_run", 0.3F, 0.0F, 1.0F, "滑墙水平速度");
            wallGravityReduction = config.getFloat("wallGravityReduction",
                    "wall_run", 0.0F, 0.0F, 1.0F, "滑墙时重力倍率（0=无重力）");
            wallJumpHorizontalStrength = config.getFloat("wallJumpHorizontalStrength",
                    "wall_run", 0.5F, 0.0F, 2.0F, "蹬墙跳水平力度");
            wallJumpVerticalStrength = config.getFloat("wallJumpVerticalStrength",
                    "wall_run", 0.6F, 0.0F, 2.0F, "蹬墙跳垂直力度");

            // ---- 滑墙视角倾斜 ----
            wallRunCameraTilt = config.getFloat("wallRunCameraTilt",
                    "wall_run", 15.0F, 0.0F, 45.0F, "滑墙时视角倾斜角度（度）");
            wallRunTiltSmoothSpeed = config.getFloat("wallRunTiltSmoothSpeed",
                    "wall_run", 8.0F, 1.0F, 30.0F, "视角倾斜过渡速度");

            // ---- PhD Flopper ----
            phdFlopperEnabled = config.getBoolean("phdFlopperEnabled",
                    "phd_flopper", true, "是否启用 PhD Flopper 效果");
            phdExplosionStrengthPerBlock = config.getFloat("phdExplosionStrengthPerBlock",
                    "phd_flopper", 1.5F, 0.0F, 10.0F, "每格坠落高度的爆炸强度");
            phdMaxExplosionStrength = config.getFloat("phdMaxExplosionStrength",
                    "phd_flopper", 5.0F, 0.0F, 20.0F, "最大爆炸强度");
            phdDestroyBlocks = config.getBoolean("phdDestroyBlocks",
                    "phd_flopper", false, "爆炸是否破坏方块");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (config.hasChanged()) config.save();
        }
    }
}
