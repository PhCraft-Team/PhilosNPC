package com.phcraft.philosnpc.features;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ShopTrade implements ConfigurationSerializable {

    private ItemStack result;
    private ItemStack price1;
    private ItemStack price2;
    private int maxUses;
    private int uses;

    public ShopTrade(ItemStack result, ItemStack price1, ItemStack price2, int maxUses) {
        this.result = result;
        this.price1 = price1;
        this.price2 = price2;
        this.maxUses = maxUses;
        this.uses = 0;
    }

    public ShopTrade(ItemStack result, ItemStack price1, int maxUses) {
        this(result, price1, null, maxUses);
    }

    public ItemStack getResult() { return result; }
    public void setResult(ItemStack result) { this.result = result; }

    public ItemStack getPrice1() { return price1; }
    public void setPrice1(ItemStack price1) { this.price1 = price1; }

    public ItemStack getPrice2() { return price2; }
    public void setPrice2(ItemStack price2) { this.price2 = price2; }

    public int getMaxUses() { return maxUses; }
    public void setMaxUses(int maxUses) { this.maxUses = maxUses; }

    public int getUses() { return uses; }
    public void setUses(int uses) { this.uses = uses; }

    public boolean isInfinite() { return maxUses == -1; }

    public boolean canUse() {
        return isInfinite() || uses < maxUses;
    }

    public void incrementUses() {
        if (!isInfinite()) {
            uses++;
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("result", result.serialize());
        map.put("price1", price1.serialize());
        if (price2 != null) {
            map.put("price2", price2.serialize());
        }
        map.put("maxUses", maxUses);
        map.put("uses", uses);
        return map;
    }

    public static ShopTrade deserialize(Map<String, Object> map) {
        ItemStack result = ItemStack.deserialize((Map<String, Object>) map.get("result"));
        ItemStack price1 = ItemStack.deserialize((Map<String, Object>) map.get("price1"));
        ItemStack price2 = null;
        if (map.containsKey("price2")) {
            price2 = ItemStack.deserialize((Map<String, Object>) map.get("price2"));
        }
        int maxUses = map.containsKey("maxUses") ? ((Number) map.get("maxUses")).intValue() : -1;
        int uses = map.containsKey("uses") ? ((Number) map.get("uses")).intValue() : 0;
        ShopTrade trade = new ShopTrade(result, price1, price2, maxUses);
        trade.setUses(uses);
        return trade;
    }

    public Map<String, Object> toMap() {
        return serialize();
    }

    public static ShopTrade fromMap(Map<String, Object> map) {
        return deserialize(map);
    }
}
