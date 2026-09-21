package com.phcraft.philosnpc.npc;

import com.phcraft.philosnpc.PluginSettings;
import com.phcraft.philosnpc.features.GiftPack;
import com.phcraft.philosnpc.features.ShopTrade;
import com.phcraft.philosnpc.features.TeleportFeature;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PhilosNPC {

    private String id;
    private String ownerName;
    private UUID ownerUuid;
    private Location location;
    private NPCPose pose;
    private double scale;
    private List<FeatureType> features;
    private String displayName;
    private ItemStack[] equipment; // 5格: 头盔, 胸甲, 护腿, 靴子, 主手

    // NPC类型
    private NPCType npcType = NPCType.PERSONAL;
    // 系统NPC实体类型名称（如 "ZOMBIE", "SKELETON", "PLAYER:Notch"）
    private String entityTypeName = null;
    // 系统NPC自定义传送费用（-1表示使用默认）
    private double customTeleportCost = -1;
    // 商店交易编辑模式：true=物品交易(以物换物)，false=金币交易；仅影响新增交易
    private boolean shopItemTradeMode = false;
    // 玩家形态皮肤纹理（从创建者捕获，用于虚拟玩家实体）
    private String skinValue = null;
    private String skinSignature = null;

    // 商店相关
    private ItemStack[] shopInventory; // 36格共享商店背包
    private List<ShopTrade> trades;

    // 礼包发放相关
    private List<GiftPack> giftPacks;

    // 传送相关
    private Location teleportTarget;

    // 留言相关
    private String message;

    private long createdAt;

    public PhilosNPC(String ownerName, UUID ownerUuid, Location location) {
        this.id = UUID.randomUUID().toString();
        this.ownerName = ownerName;
        this.ownerUuid = ownerUuid;
        this.location = location;
        this.pose = NPCPose.STANDING;
        this.scale = 1.0;
        this.features = new ArrayList<>();
        this.displayName = ownerName;
        this.equipment = new ItemStack[5];
        this.shopInventory = new ItemStack[36];
        this.trades = new ArrayList<>();
        this.giftPacks = new ArrayList<>();
        this.teleportTarget = null;
        this.message = "";
        this.createdAt = System.currentTimeMillis();
    }

    public PhilosNPC() {
        this.features = new ArrayList<>();
        this.equipment = new ItemStack[5];
        this.shopInventory = new ItemStack[36];
        this.trades = new ArrayList<>();
        this.giftPacks = new ArrayList<>();
        this.message = "";
        this.createdAt = System.currentTimeMillis();
    }

    // ===== Getters & Setters =====

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public NPCPose getPose() { return pose; }
    public void setPose(NPCPose pose) { this.pose = pose; }

    public double getScale() { return scale; }
    public void setScale(double scale) { this.scale = scale; }

    public List<FeatureType> getFeatures() { return features; }
    public void setFeatures(List<FeatureType> features) { this.features = features; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public ItemStack[] getEquipment() { return equipment; }
    public void setEquipment(ItemStack[] equipment) { this.equipment = equipment; }

    public ItemStack[] getShopInventory() { return shopInventory; }
    public void setShopInventory(ItemStack[] shopInventory) { this.shopInventory = shopInventory; }

    public List<ShopTrade> getTrades() { return trades; }
    public void setTrades(List<ShopTrade> trades) { this.trades = trades; }

    public List<GiftPack> getGiftPacks() { return giftPacks; }

    public GiftPack getGiftPack(String packId) {
        for (GiftPack pack : giftPacks) {
            if (pack.getId().equals(packId)) return pack;
        }
        return null;
    }

    public Location getTeleportTarget() { return teleportTarget; }
    public void setTeleportTarget(Location teleportTarget) { this.teleportTarget = teleportTarget; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public NPCType getNpcType() { return npcType; }
    public void setNpcType(NPCType npcType) { this.npcType = npcType; }

    public String getEntityTypeName() { return entityTypeName; }
    public void setEntityTypeName(String entityTypeName) { this.entityTypeName = entityTypeName; }

    public double getCustomTeleportCost() { return customTeleportCost; }
    public void setCustomTeleportCost(double cost) { this.customTeleportCost = cost; }

    public boolean isShopItemTradeMode() { return shopItemTradeMode; }
    public void setShopItemTradeMode(boolean shopItemTradeMode) { this.shopItemTradeMode = shopItemTradeMode; }

    public String getSkinValue() { return skinValue; }
    public void setSkinValue(String skinValue) { this.skinValue = skinValue; }

    public String getSkinSignature() { return skinSignature; }
    public void setSkinSignature(String skinSignature) { this.skinSignature = skinSignature; }

    public boolean isSystem() { return npcType == NPCType.SYSTEM; }

    public double getEffectiveTeleportCost() {
        return isSystem() && customTeleportCost >= 0 ? customTeleportCost : TeleportFeature.getTeleportCost();
    }

    // ===== Feature 管理 =====

    public boolean addFeature(FeatureType feature) {
        if (features.size() >= PluginSettings.maxFeatures()) {
            return false;
        }
        if (features.contains(feature)) {
            return false;
        }
        features.add(feature);
        return true;
    }

    public boolean removeFeature(FeatureType feature) {
        return features.remove(feature);
    }

    public boolean hasFeature(FeatureType feature) {
        return features.contains(feature);
    }

    // ===== 序列化 / 反序列化 =====

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("ownerName", ownerName);
        map.put("ownerUuid", ownerUuid.toString());
        map.put("location", serializeLocation(location));
        map.put("pose", pose.name());
        map.put("scale", scale);

        List<String> featureNames = new ArrayList<>();
        for (FeatureType f : features) {
            featureNames.add(f.name());
        }
        map.put("features", featureNames);

        map.put("displayName", displayName);

        // equipment
        List<Map<String, Object>> equipmentList = new ArrayList<>();
        for (ItemStack item : equipment) {
            if (item != null) {
                equipmentList.add(item.serialize());
            } else {
                equipmentList.add(null);
            }
        }
        map.put("equipment", equipmentList);

        // shopInventory
        List<Map<String, Object>> shopInvList = new ArrayList<>();
        for (ItemStack item : shopInventory) {
            if (item != null) {
                shopInvList.add(item.serialize());
            } else {
                shopInvList.add(null);
            }
        }
        map.put("shopInventory", shopInvList);

        // trades
        List<Map<String, Object>> tradesList = new ArrayList<>();
        for (ShopTrade trade : trades) {
            tradesList.add(trade.toMap());
        }
        map.put("trades", tradesList);

        // teleportTarget
        if (teleportTarget != null) {
            map.put("teleportTarget", serializeLocation(teleportTarget));
        }

        map.put("message", message);
        map.put("createdAt", createdAt);
        map.put("npcType", npcType.name());
        if (skinValue != null) {
            map.put("skinValue", skinValue);
            if (skinSignature != null) {
                map.put("skinSignature", skinSignature);
            }
        }
        if (entityTypeName != null) map.put("entityTypeName", entityTypeName);
        map.put("customTeleportCost", customTeleportCost);
        map.put("shopItemTradeMode", shopItemTradeMode);

        // giftPacks
        List<Map<String, Object>> packList = new ArrayList<>();
        for (GiftPack pack : giftPacks) {
            packList.add(pack.toMap());
        }
        map.put("giftPacks", packList);

        return map;
    }

    @SuppressWarnings("unchecked")
    public static PhilosNPC fromMap(Map<String, Object> map) {
        PhilosNPC npc = new PhilosNPC();

        npc.id = (String) map.get("id");
        npc.ownerName = (String) map.get("ownerName");
        npc.ownerUuid = UUID.fromString((String) map.get("ownerUuid"));
        npc.location = deserializeLocation((Map<String, Object>) map.get("location"));
        npc.pose = NPCPose.parse((String) map.get("pose"));
        npc.scale = ((Number) map.get("scale")).doubleValue();
        npc.skinValue = (String) map.get("skinValue");
        npc.skinSignature = (String) map.get("skinSignature");

        List<String> featureNames = (List<String>) map.get("features");
        if (featureNames != null) {
            for (String name : featureNames) {
                // 容错：跳过已删除的功能类型（如旧数据的JUKEBOX），避免加载崩溃
                try {
                    npc.features.add(FeatureType.valueOf(name));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        npc.displayName = (String) map.getOrDefault("displayName", npc.ownerName);

        // equipment
        List<Map<String, Object>> equipmentList = (List<Map<String, Object>>) map.get("equipment");
        if (equipmentList != null) {
            npc.equipment = new ItemStack[5];
            for (int i = 0; i < 5 && i < equipmentList.size(); i++) {
                Map<String, Object> itemMap = equipmentList.get(i);
                if (itemMap != null) {
                    npc.equipment[i] = ItemStack.deserialize(itemMap);
                }
            }
        }

        // shopInventory
        List<Map<String, Object>> shopInvList = (List<Map<String, Object>>) map.get("shopInventory");
        if (shopInvList != null) {
            npc.shopInventory = new ItemStack[36];
            for (int i = 0; i < 36 && i < shopInvList.size(); i++) {
                Map<String, Object> itemMap = shopInvList.get(i);
                if (itemMap != null) {
                    npc.shopInventory[i] = ItemStack.deserialize(itemMap);
                }
            }
        }

        // trades
        List<Map<String, Object>> tradesList = (List<Map<String, Object>>) map.get("trades");
        if (tradesList != null) {
            for (Map<String, Object> tradeMap : tradesList) {
                npc.trades.add(ShopTrade.fromMap(tradeMap));
            }
        }

        // teleportTarget
        if (map.containsKey("teleportTarget")) {
            npc.teleportTarget = deserializeLocation((Map<String, Object>) map.get("teleportTarget"));
        }

        npc.message = (String) map.getOrDefault("message", "");
        npc.createdAt = map.containsKey("createdAt") ? ((Number) map.get("createdAt")).longValue() : System.currentTimeMillis();
        npc.npcType = map.containsKey("npcType") ? NPCType.valueOf((String) map.get("npcType")) : NPCType.PERSONAL;
        npc.entityTypeName = (String) map.get("entityTypeName");
        npc.customTeleportCost = map.containsKey("customTeleportCost") ? ((Number) map.get("customTeleportCost")).doubleValue() : -1;
        npc.shopItemTradeMode = map.containsKey("shopItemTradeMode") && (Boolean) map.get("shopItemTradeMode");

        // giftPacks
        List<Map<String, Object>> packList = (List<Map<String, Object>>) map.get("giftPacks");
        if (packList != null) {
            for (Map<String, Object> packMap : packList) {
                npc.giftPacks.add(GiftPack.fromMap(packMap));
            }
        }

        return npc;
    }

    // ===== Location 序列化辅助 =====

    private static Map<String, Object> serializeLocation(Location loc) {
        if (loc == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", loc.getWorld().getName());
        map.put("x", loc.getX());
        map.put("y", loc.getY());
        map.put("z", loc.getZ());
        map.put("yaw", (double) loc.getYaw());
        map.put("pitch", (double) loc.getPitch());
        return map;
    }

    private static Location deserializeLocation(Map<String, Object> map) {
        if (map == null) return null;
        World world = Bukkit.getWorld((String) map.get("world"));
        double x = ((Number) map.get("x")).doubleValue();
        double y = ((Number) map.get("y")).doubleValue();
        double z = ((Number) map.get("z")).doubleValue();
        float yaw = ((Number) map.get("yaw")).floatValue();
        float pitch = ((Number) map.get("pitch")).floatValue();
        return new Location(world, x, y, z, yaw, pitch);
    }
}
