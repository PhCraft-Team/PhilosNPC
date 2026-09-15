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
    private boolean useCurrency;
    private double currencyPrice;

    public ShopTrade(ItemStack result, ItemStack price1, ItemStack price2, int maxUses) {
        this.result = result;
        this.price1 = price1;
        this.price2 = price2;
        this.maxUses = maxUses;
        this.uses = 0;
        this.useCurrency = false;
        this.currencyPrice = 0;
    }

    public ShopTrade(ItemStack result, double currencyPrice, int maxUses) {
        this.result = result;
        this.price1 = null;
        this.price2 = null;
        this.maxUses = maxUses;
        this.uses = 0;
        this.useCurrency = true;
        this.currencyPrice = currencyPrice;
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

    public boolean isUseCurrency() { return useCurrency; }
    public void setUseCurrency(boolean useCurrency) { this.useCurrency = useCurrency; }

    public double getCurrencyPrice() { return currencyPrice; }
    public void setCurrencyPrice(double currencyPrice) { this.currencyPrice = currencyPrice; }

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
        if (price1 != null) {
            map.put("price1", price1.serialize());
        }
        if (price2 != null) {
            map.put("price2", price2.serialize());
        }
        map.put("maxUses", maxUses);
        map.put("uses", uses);
        map.put("useCurrency", useCurrency);
        map.put("currencyPrice", currencyPrice);
        return map;
    }

    public static ShopTrade deserialize(Map<String, Object> map) {
        ItemStack result = ItemStack.deserialize((Map<String, Object>) map.get("result"));
        int maxUses = map.containsKey("maxUses") ? ((Number) map.get("maxUses")).intValue() : -1;
        int uses = map.containsKey("uses") ? ((Number) map.get("uses")).intValue() : 0;
        boolean useCurrency = map.containsKey("useCurrency") ? (boolean) map.get("useCurrency") : false;
        double currencyPrice = map.containsKey("currencyPrice") ? ((Number) map.get("currencyPrice")).doubleValue() : 0;

        if (useCurrency) {
            ShopTrade trade = new ShopTrade(result, currencyPrice, maxUses);
            trade.setUses(uses);
            return trade;
        }

        ItemStack price1 = map.containsKey("price1") ? ItemStack.deserialize((Map<String, Object>) map.get("price1")) : null;
        ItemStack price2 = null;
        if (map.containsKey("price2")) {
            price2 = ItemStack.deserialize((Map<String, Object>) map.get("price2"));
        }
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
