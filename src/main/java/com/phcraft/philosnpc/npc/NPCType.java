package com.phcraft.philosnpc.npc;

public enum NPCType {
    PERSONAL("个人NPC", "&b"),
    SYSTEM("系统NPC", "&d");

    private final String displayName;
    private final String colorPrefix;

    NPCType(String displayName, String colorPrefix) {
        this.displayName = displayName;
        this.colorPrefix = colorPrefix;
    }

    public String displayName() { return displayName; }
    public String colorPrefix() { return colorPrefix; }
}
