package com.phcraft.philosnpc.features;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 礼包数据：名字、内容物、容器类型（收纳袋/潜影盒）与颜色、领取记录
 */
public class GiftPack {

    private final String id;
    private String name;
    private boolean shulker;   // true=潜影盒 false=收纳袋
    private String color;      // DyeColor.name()
    private List<ItemStack> contents = new ArrayList<>();
    private Set<UUID> claimedBy = new HashSet<>();

    public GiftPack(String id) {
        this.id = id;
        this.name = "新礼包";
        this.shulker = false;
        this.color = "WHITE";
    }

    public String getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isShulker() { return shulker; }
    public void setShulker(boolean shulker) { this.shulker = shulker; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public List<ItemStack> getContents() { return contents; }
    public void setContents(List<ItemStack> contents) { this.contents = contents; }

    public boolean isClaimedBy(UUID uuid) { return claimedBy.contains(uuid); }
    public void markClaimed(UUID uuid) { claimedBy.add(uuid); }
    public int getClaimedCount() { return claimedBy.size(); }

    /**
     * 对应的容器物品材质（16色收纳袋/16色潜影盒）
     */
    public Material containerMaterial() {
        try {
            return Material.valueOf(color + (shulker ? "_SHULKER_BOX" : "_BUNDLE"));
        } catch (IllegalArgumentException e) {
            return shulker ? Material.SHULKER_BOX : Material.BUNDLE;
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("shulker", shulker);
        map.put("color", color);

        List<Map<String, Object>> contentList = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null) contentList.add(item.serialize());
        }
        map.put("contents", contentList);

        List<String> claimed = new ArrayList<>();
        for (UUID uuid : claimedBy) {
            claimed.add(uuid.toString());
        }
        map.put("claimedBy", claimed);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static GiftPack fromMap(Map<String, Object> map) {
        GiftPack pack = new GiftPack((String) map.get("id"));
        pack.name = (String) map.getOrDefault("name", "新礼包");
        pack.shulker = map.containsKey("shulker") && (boolean) map.get("shulker");
        pack.color = (String) map.getOrDefault("color", "WHITE");

        List<Map<String, Object>> contentList = (List<Map<String, Object>>) map.get("contents");
        if (contentList != null) {
            for (Map<String, Object> itemMap : contentList) {
                pack.contents.add(ItemStack.deserialize(itemMap));
            }
        }

        List<String> claimed = (List<String>) map.get("claimedBy");
        if (claimed != null) {
            for (String uuid : claimed) {
                try {
                    pack.claimedBy.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return pack;
    }
}
