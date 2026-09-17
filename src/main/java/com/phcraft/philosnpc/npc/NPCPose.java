package com.phcraft.philosnpc.npc;

public enum NPCPose {
    STANDING("站立", "🧍"),
    SNEAKING("潜行", "🤫"),
    SITTING("坐着", "🪑"),
    LYING("躺着", "🛌"),
    DANCING("跳舞", "💃"),
    WAVE("挥手", "👋"),
    ARMS_CROSSED("抱胸", "🤔"),
    THUMBS_UP("点赞", "👍"),
    BOWING("鞠躬", "🙇"),
    SUPERMAN("超人飞行", "🦸"),
    POINTING("指向前方", "👉"),
    MEDITATION("打坐", "🧘"),
    FACEPALM("捂脸", "🤦");

    private final String displayName;
    private final String emoji;

    NPCPose(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }

    public String displayName() { return displayName; }
    public String emoji() { return emoji; }
}
