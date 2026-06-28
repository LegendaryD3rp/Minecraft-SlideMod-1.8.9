package com.omnia.movement;

/**
 * 玩家运动状态枚举。
 * 此状态存储在 PlayerStateManager 中，仅对本地玩家有效。
 * 多人模式下通过自定义包同步到服务端和其他客户端。
 */
public enum MovementState {

    /** 常规状态 */
    NONE,

    /** 滑铲中（地面） */
    SLIDING,

    /** 飞扑中（空中） */
    DIVING,

    /** 飞扑落地缓冲 */
    LANDING
}
