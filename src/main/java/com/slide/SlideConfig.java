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
    public boolean omniDirectional = true;  // 全向机动

    // ── Dive Mechanics ──
    public boolean diveEnabled = true;
    public float diveForwardBoost = 2.5F;
    public float diveUpwardBoost = 0.5F;
    public float diveHitboxHeight = 0.4F;
    public int diveAutoStandDelay = 5;

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
        // 构造函数：从文件加载
        config.load();
        readFieldsFromConfig();
        if (config.hasChanged()) config.save();
    }

    /** 从 Configuration 对象（内存）读取所有字段。不读文件。 */
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

    /** 持久化配置到磁盘，并同步字段。不重新读文件。 */
    public void save() {
        config.save();
        readFieldsFromConfig();
    }
}
