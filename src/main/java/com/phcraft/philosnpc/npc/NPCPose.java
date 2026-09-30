package com.phcraft.philosnpc.npc;

/**
 * NPC姿势：玩家原生Pose，仅站立与坐姿（玩家形态模型）
 */
public enum NPCPose {
    STANDING("站立", "🧍"),
    SITTING("坐着", "🪑");

    private final String displayName;
    private final String emoji;

    NPCPose(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }

    public String displayName() { return displayName; }
    public String emoji() { return emoji; }

    /**
     * 兼容旧数据：旧版本的其他姿势一律回退为站立
     */
    public static NPCPose parse(String name) {
        if (name == null) return STANDING;
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return STANDING;
        }
    }
}
