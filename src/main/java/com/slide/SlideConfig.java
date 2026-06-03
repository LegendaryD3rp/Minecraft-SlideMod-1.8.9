package com.slide;

import net.minecraftforge.common.config.Configuration;

public class SlideConfig {

    final Configuration config;

    // ── General ──
    public boolean enabled = true;

    // ── Slide Mechanics ──
    public float slideSpeedMultiplier = 1.5F;
    public int slideDurationTicks = 30;
    public int slideCooldownTicks = 10;
    public float slideHitboxHeight = 0.6F;
    public boolean preventFallDamage = true;
    public float initialBoost = 2.0F;
    public boolean omniDirectional = true;

    // ── Dive Mechanics ──
    public boolean diveEnabled = true;
    public float diveForwardBoost = 2.5F;
    public float diveUpwardBoost = 0.5F;
    public float diveHitboxHeight = 0.4F;
    public int diveAutoStandDelay = 5;

    // ── Wall Run ──
    public boolean wallRunEnabled = true;
    public int wallRunMaxTicks = 40;         // 2秒
    public double wallRunSpeed = 0.25D;
    public double wallJumpHorizontal = 1.2D;
    public double wallJumpVertical = 0.55D;
    public boolean wallRunParticles = true;

    // ── Double Jump ──
    public boolean doubleJumpEnabled = true;
    public double doubleJumpHorizontal = 0.35D;
    public double doubleJumpVertical = 0.55D;

    // ── PhD Flopper ──
    public boolean phdFlopperEnabled = true;
    public float phdExplosionMinRadius = 1.5F;
    public float phdExplosionMaxRadius = 6.0F;
    public float phdMinFallDist = 2.0F;
    public float phdMaxFallDist = 30.0F;
    /** 是否需要飞扑姿势才触发爆炸（否则只要有 PhD 效果 + 摔落就爆） */
    public boolean phdRequireDive = false;

    // ── Visual ──
    public float slideBodyPitch = 65.0F;
    public float diveBodyPitch = 80.0F;
    public boolean showBody = true;
    public int particleCount = 3;
    public boolean enableParticles = true;

    // ── Key ──
    public String keyBindingName = "key.slide";
    public int keyBindingDefault = 44;      // Z
    public String diveKeyName = "key.dive";
    public int diveKeyDefault = 45;         // X

    public SlideConfig(Configuration config) {
        this.config = config;
        config.load();
        readFieldsFromConfig();
        if (config.hasChanged()) config.save();
    }

    private void readFieldsFromConfig() {
        String cat;

        cat = "general";
        enabled = config.getBoolean("enabled", cat, true, "总开关");

        cat = "mechanics";
        slideSpeedMultiplier = config.getFloat("slideSpeedMultiplier", cat, 1.5F, 1.0F, 5.0F, "滑铲速度倍率");
        slideDurationTicks = config.getInt("slideDurationTicks", cat, 30, 5, 100, "滑铲持续 ticks");
        slideCooldownTicks = config.getInt("slideCooldownTicks", cat, 10, 0, 100, "冷却 ticks");
        slideHitboxHeight = config.getFloat("slideHitboxHeight", cat, 0.6F, 0.2F, 1.5F, "滑铲 hitbox 高度");
        preventFallDamage = config.getBoolean("preventFallDamage", cat, true, "免疫摔落伤害");
        initialBoost = config.getFloat("initialBoost", cat, 2.0F, 1.0F, 5.0F, "滑铲启动 boost");
        omniDirectional = config.getBoolean("omniDirectional", cat, true,
                "全向机动：根据 WASD 输入方向滑铲/飞扑，不只看镜头朝向");

        cat = "dive";
        diveEnabled = config.getBoolean("diveEnabled", cat, true, "飞扑开关");
        diveForwardBoost = config.getFloat("diveForwardBoost", cat, 2.5F, 1.0F, 6.0F, "飞扑前冲速度");
        diveUpwardBoost = config.getFloat("diveUpwardBoost", cat, 0.5F, 0.0F, 2.0F, "飞扑上跳速度");
        diveHitboxHeight = config.getFloat("diveHitboxHeight", cat, 0.4F, 0.2F, 1.5F, "飞扑 hitbox 高度");
        diveAutoStandDelay = config.getInt("diveAutoStandDelay", cat, 5, 0, 40, "落地后延迟恢复 ticks");

        cat = "wallrun";
        wallRunEnabled = config.getBoolean("wallRunEnabled", cat, true, "滑墙开关");
        wallRunMaxTicks = config.getInt("wallRunMaxTicks", cat, 40, 10, 100, "滑墙最大持续 ticks");
        wallRunSpeed = config.getFloat("wallRunSpeed", cat, 0.25F, 0.05F, 1.0F, "滑墙沿墙速度");
        wallJumpHorizontal = config.getFloat("wallJumpHorizontal", cat, 1.2F, 0.0F, 3.0F, "蹬墙跳水平速度");
        wallJumpVertical = config.getFloat("wallJumpVertical", cat, 0.55F, 0.0F, 2.0F, "蹬墙跳垂直速度");
        wallRunParticles = config.getBoolean("wallRunParticles", cat, true, "滑墙粒子效果");

        cat = "doublejump";
        doubleJumpEnabled = config.getBoolean("doubleJumpEnabled", cat, true, "二段跳开关");
        doubleJumpHorizontal = config.getFloat("doubleJumpHorizontal", cat, 0.35F, 0.0F, 1.5F, "二段跳水平速度");
        doubleJumpVertical = config.getFloat("doubleJumpVertical", cat, 0.55F, 0.0F, 2.0F, "二段跳垂直速度");

        cat = "phdflopper";
        phdFlopperEnabled = config.getBoolean("phdFlopperEnabled", cat, true, "PhD Flopper 开关");
        phdExplosionMinRadius = config.getFloat("phdExplosionMinRadius", cat, 1.5F, 0.0F, 10.0F, "最小爆炸半径");
        phdExplosionMaxRadius = config.getFloat("phdExplosionMaxRadius", cat, 6.0F, 0.0F, 20.0F, "最大爆炸半径（30格掉落）");
        phdMinFallDist = config.getFloat("phdMinFallDist", cat, 2.0F, 1.0F, 20.0F, "触发爆炸的最小掉落距离");
        phdMaxFallDist = config.getFloat("phdMaxFallDist", cat, 30.0F, 5.0F, 100.0F, "最大爆炸半径对应的掉落距离");
        phdRequireDive = config.getBoolean("phdRequireDive", cat, false, "是否需要飞扑姿势才爆炸");

        cat = "visual";
        slideBodyPitch = config.getFloat("slideBodyPitch", cat, 65.0F, 0.0F, 90.0F, "滑铲模型前倾");
        diveBodyPitch = config.getFloat("diveBodyPitch", cat, 80.0F, 0.0F, 90.0F, "飞扑模型俯仰");
        showBody = config.getBoolean("showBody", cat, true, "第一人称显示身体");
        particleCount = config.getInt("particleCount", cat, 3, 0, 20, "粒子数");
        enableParticles = config.getBoolean("enableParticles", cat, true, "粒子开关");

        cat = "key";
        keyBindingName = config.getString("keyBindingName", cat, "key.slide", "滑铲按键注册名");
        keyBindingDefault = config.getInt("keyBindingDefault", cat, 44, 0, 255, "滑铲按键代码 (默认 Z=44)");
        diveKeyName = config.getString("diveKeyName", cat, "key.dive", "飞扑按键注册名");
        diveKeyDefault = config.getInt("diveKeyDefault", cat, 45, 0, 255, "飞扑按键代码 (默认 X=45)");
    }

    public void save() {
        config.save();
        readFieldsFromConfig();
    }
}
