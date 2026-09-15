package com.phcraft.philosnpc.npc;

public enum FeatureType {
    SHOP("商店", "EMERALD", "玩家可以在此购买和出售物品"),
    TELEPORT("传送", "ENDER_PEARL", "花费游戏币传送到指定地点"),
    JUKEBOX("点歌台", "MUSIC_DISC_CAT", "播放唱片中的音乐"),
    MESSAGE("留言板", "PAPER", "查看NPC主人留下的留言");

    private final String displayName;
    private final String icon;
    private final String description;

    FeatureType(String displayName, String icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String displayName() { return displayName; }
    public String icon() { return icon; }
    public String description() { return description; }
}
