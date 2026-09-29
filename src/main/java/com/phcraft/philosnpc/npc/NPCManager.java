package com.phcraft.philosnpc.npc;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.PluginSettings;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NPCManager {

    private final PhilosNPCPlugin plugin;
    private final Map<String, PhilosNPC> npcs;
    private final Map<Integer, String> entityIdMap;
    // 共享商店背包：键 = 玩家UUID|世界名（同玩家同世界的NPC共享，跨世界隔离，防止跨世界走私物品）
    private final Map<String, ItemStack[]> sharedShopInventories;
    private final File dataFile;
    // 收购背包（物物交易收入）：键 = 玩家UUID|世界名，同玩家同世界共享，无限容量
    private final Map<String, List<ItemStack>> collectionBackpacks;
    private final File backpackFile;
    // 跨世界转移仓库：按玩家UUID（天生跨世界），只存白名单物品，独立文件存储
    private final Map<UUID, List<ItemStack>> transferVaults;
    private final File transferFile;
    // 商店背包编辑锁：scopeKey → 编辑者UUID（编辑期间冻结同店主同世界的交易，防止关闭时旧副本覆盖交易结果）
    private final Map<String, UUID> shopEditLocks = new HashMap<>();
    // 村民式头部动画状态（entityId -> 状态）
    private final Map<Integer, HeadState> headStates = new HashMap<>();
    // 已生成NPC的实体引用（entityId -> 实体），供动画循环快速访问
    private final Map<Integer, Entity> entityRefs = new HashMap<>();
    // 玩家形态NPC（PacketEvents虚拟实体）
    private final PlayerNpcSpawner playerNpcSpawner = new PlayerNpcSpawner();

    private void registerEntity(Entity entity, String npcId) {
        entityIdMap.put(entity.getEntityId(), npcId);
        entityRefs.put(entity.getEntityId(), entity);
    }

    public NPCManager() {
        this.plugin = PhilosNPCPlugin.instance();
        this.npcs = new HashMap<>();
        this.entityIdMap = new HashMap<>();
        this.sharedShopInventories = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.collectionBackpacks = new HashMap<>();
        this.backpackFile = new File(plugin.getDataFolder(), "collection_backpacks.yml");
        this.transferVaults = new HashMap<>();
        this.transferFile = new File(plugin.getDataFolder(), "transfer_vaults.yml");
    }

    /** 世界隔离存储键：玩家UUID|世界名 */
    static String scopeKey(UUID ownerUuid, String worldName) {
        return ownerUuid + "|" + (worldName == null ? "world" : worldName);
    }

    // ===== 加载 / 保存 =====

    @SuppressWarnings("unchecked")
    public void loadAll() {
        if (!dataFile.exists()) {
            plugin.saveResource("npcs.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        if (config.contains("npcs")) {
            List<Map<?, ?>> npcList = config.getMapList("npcs");
            for (Map<?, ?> rawMap : npcList) {
                Map<String, Object> map = (Map<String, Object>) rawMap;
                PhilosNPC npc = PhilosNPC.fromMap(map);
                npcs.put(npc.getId(), npc);

                // 如果所在区块已加载，则生成实体
                Location loc = npc.getLocation();
                if (loc != null && loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    spawnNPC(npc);
                }
            }
        }

        // 旧版共享库存一次性迁移到按店主+世界隔离格式（需在NPC加载后、共享库存分组前执行）
        migrateLegacyShopInventories(config);

        // 加载收购背包（NPC数据之后：旧格式迁移需要按该玩家NPC所在世界归并）
        loadCollectionBackpacks();
        loadTransferVaults();

        // 初始化共享商店背包（按 玩家|世界 分组：同玩家同世界的NPC共享一份）
        for (PhilosNPC npc : npcs.values()) {
            UUID ownerUuid = npc.getOwnerUuid();
            String key = scopeKey(ownerUuid, npc.getWorldName());
            if (!sharedShopInventories.containsKey(key)) {
                // 以该世界内第一个找到的NPC商店背包作为该世界侧共享背包
                sharedShopInventories.put(key, npc.getShopInventory().clone());
            }
            // 同步共享背包到该NPC（确保同世界所有NPC数据一致）
            npc.setShopInventory(sharedShopInventories.get(key));
        }

        plugin.getLogger().info("已加载 " + npcs.size() + " 个NPC");
    }

    public void saveAll() {
        // 保存前，将共享商店背包同步到所有NPC（按各自所在世界取对应侧）
        for (PhilosNPC npc : npcs.values()) {
            ItemStack[] sharedInv = sharedShopInventories.get(scopeKey(npc.getOwnerUuid(), npc.getWorldName()));
            if (sharedInv != null) {
                npc.setShopInventory(sharedInv.clone());
            }
        }

        FileConfiguration config = new YamlConfiguration();
        // 库存格式版本标记：已迁移/新格式存档不再走旧共享库存迁移
        config.set("inventory-format", 2);
        List<Map<String, Object>> npcList = new ArrayList<>();
        for (PhilosNPC npc : npcs.values()) {
            npcList.add(npc.toMap());
        }
        config.set("npcs", npcList);

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存NPC数据失败: " + e.getMessage());
        }

        saveCollectionBackpacks();
        saveTransferVaults();
    }

    // ===== 旧版共享库存一次性迁移 =====

    /**
     * 旧版按店主共享的商店库存（各世界NPC携带同一份副本）一次性迁移到按店主+世界隔离格式。
     * 无标记时执行：各世界副本完全一致 → 视为旧版复制产物，仅保留主世界侧一份，其余清空；
     * 副本不一致 → 可能为升级后各自演化过的数据，原样保留并提示管理员核账，不任意删除。
     * 迁移前备份 npcs.yml，完成后写入 inventory-format 标记，重复启动不会二次迁移。
     */
    private void migrateLegacyShopInventories(FileConfiguration config) {
        if (config.getInt("inventory-format", 0) >= 2) return;

        // 收集个人NPC（系统NPC无共享库存）按店主分组
        Map<UUID, List<PhilosNPC>> personalByOwner = new LinkedHashMap<>();
        for (PhilosNPC npc : npcs.values()) {
            if (!npc.isSystem()) {
                personalByOwner.computeIfAbsent(npc.getOwnerUuid(), k -> new ArrayList<>()).add(npc);
            }
        }

        boolean needsBackup = false;
        Map<UUID, String> migrateTargets = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<PhilosNPC>> entry : personalByOwner.entrySet()) {
            // 每个世界取第一个NPC保存的副本（与共享库存分组初始化口径一致）
            Map<String, ItemStack[]> firstByWorld = new LinkedHashMap<>();
            for (PhilosNPC npc : entry.getValue()) {
                firstByWorld.putIfAbsent(npc.getWorldName(), npc.getShopInventory());
            }
            if (firstByWorld.size() < 2) continue; // 单世界店主无复制风险

            ItemStack[] reference = null;
            boolean consistent = true;
            for (ItemStack[] inv : firstByWorld.values()) {
                if (reference == null) {
                    reference = inv;
                } else if (!inventoriesIdentical(reference, inv)) {
                    consistent = false;
                    break;
                }
            }

            if (!consistent) {
                plugin.getLogger().warning("店主 " + entry.getKey() + " 在多个世界的商店库存内容不一致，"
                        + "可能是升级后各自演化过的数据，已原样保留，请管理员人工核账，不做自动迁移");
                continue;
            }
            needsBackup = true;
            migrateTargets.put(entry.getKey(), migrateWorldFor(entry.getKey()));
        }

        // 备份必须在清空副本之前完成：备份失败时若内存已被清空，后续任意saveAll会把清空结果写盘绕过备份
        if (needsBackup) {
            File backup = new File(dataFile.getParentFile(), "npcs.yml.bak-v1-inventory");
            if (!backup.exists()) {
                try {
                    java.nio.file.Files.copy(dataFile.toPath(), backup.toPath());
                } catch (IOException e) {
                    plugin.getLogger().severe("迁移前备份 npcs.yml 失败，本次启动跳过迁移（下次启动重试）: " + e.getMessage());
                    return;
                }
            }
        }

        for (Map.Entry<UUID, String> entry : migrateTargets.entrySet()) {
            String target = entry.getValue();
            for (PhilosNPC npc : personalByOwner.get(entry.getKey())) {
                if (!npc.getWorldName().equals(target)) {
                    npc.setShopInventory(new ItemStack[36]);
                }
            }
            plugin.getLogger().info("旧版共享商店库存迁移：店主 " + entry.getKey()
                    + " 的库存保留至世界 " + target + "，其余世界副本清空");
        }

        config.set("inventory-format", 2);
        List<Map<String, Object>> npcList = new ArrayList<>();
        for (PhilosNPC npc : npcs.values()) {
            npcList.add(npc.toMap());
        }
        config.set("npcs", npcList);
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("写入库存格式标记失败（迁移结果仅在内存，下次启动将重新迁移）: " + e.getMessage());
        }
    }

    /** 逐格比较两份库存是否完全一致（类型、堆叠数与物品元数据） */
    static boolean inventoriesIdentical(ItemStack[] a, ItemStack[] b) {
        if (a == null || b == null) return a == b;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            ItemStack x = a[i];
            ItemStack y = b[i];
            if (x == null || y == null) {
                if (x != y) return false;
                continue;
            }
            if (x.getAmount() != y.getAmount() || !x.isSimilar(y)) return false;
        }
        return true;
    }

    // ===== 收购背包（物物交易收入，同玩家同世界共享，跨世界隔离） =====

    /**
     * 获取指定玩家在指定世界的收购背包（为空时自动创建）
     */
    public List<ItemStack> getCollectionBackpack(UUID ownerUuid, String worldName) {
        return collectionBackpacks.computeIfAbsent(scopeKey(ownerUuid, worldName), k -> new ArrayList<>());
    }

    /**
     * 收购物品入包：同类物品尽量并入已有堆，放不下则追加新条目
     */
    public void addItemToCollectionBackpack(UUID ownerUuid, String worldName, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemStack add = item.clone();
        List<ItemStack> backpack = getCollectionBackpack(ownerUuid, worldName);
        int max = add.getMaxStackSize();
        for (ItemStack existing : backpack) {
            if (add.getAmount() <= 0) break;
            if (existing.isSimilar(add) && existing.getAmount() < max) {
                int move = Math.min(max - existing.getAmount(), add.getAmount());
                existing.setAmount(existing.getAmount() + move);
                add.setAmount(add.getAmount() - move);
            }
        }
        if (add.getAmount() > 0) {
            backpack.add(add);
        }
    }

    /**
     * 清空并返回指定世界的收购背包全部物品（一键取回）
     */
    public List<ItemStack> clearCollectionBackpack(UUID ownerUuid, String worldName) {
        List<ItemStack> backpack = collectionBackpacks.remove(scopeKey(ownerUuid, worldName));
        return backpack != null ? backpack : new ArrayList<>();
    }

    /**
     * 旧格式收购背包（键=纯UUID）迁移目标世界：该玩家的NPC只在一个世界用该世界，
     * 多个世界或无NPC时取主世界
     */
    private String migrateWorldFor(UUID ownerUuid) {
        String found = null;
        for (PhilosNPC npc : npcs.values()) {
            if (npc.getOwnerUuid().equals(ownerUuid)) {
                if (found == null) {
                    found = npc.getWorldName();
                } else if (!found.equals(npc.getWorldName())) {
                    return Bukkit.getWorlds().get(0).getName();
                }
            }
        }
        return found != null ? found : Bukkit.getWorlds().get(0).getName();
    }

    @SuppressWarnings("unchecked")
    private void loadCollectionBackpacks() {
        if (!backpackFile.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(backpackFile);
        var section = config.getConfigurationSection("backpacks");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                if (key.contains("|")) {
                    // 新格式：UUID|世界名
                    String[] parts = key.split("\\|", 2);
                    UUID.fromString(parts[0]);
                    collectionBackpacks.put(key, deserializeItemList(config, "backpacks." + key));
                } else {
                    // 旧格式：纯UUID → 按该玩家NPC所在世界迁移（多世界取主世界）
                    UUID ownerUuid = UUID.fromString(key);
                    String world = migrateWorldFor(ownerUuid);
                    collectionBackpacks.put(scopeKey(ownerUuid, world), deserializeItemList(config, "backpacks." + key));
                    plugin.getLogger().info("收购背包旧数据迁移: " + key + " → " + world);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("收购背包数据包含无效的UUID: " + key);
            }
        }
    }

    private List<ItemStack> deserializeItemList(FileConfiguration config, String path) {
        List<ItemStack> items = new ArrayList<>();
        for (Map<?, ?> rawMap : config.getMapList(path)) {
            try {
                items.add(ItemStack.deserialize((Map<String, Object>) rawMap));
            } catch (IllegalArgumentException ignored) {
                // 单个物品损坏时跳过，不影响整包加载
            }
        }
        return items;
    }

    private void saveCollectionBackpacks() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, List<ItemStack>> entry : collectionBackpacks.entrySet()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ItemStack item : entry.getValue()) {
                if (item != null && !item.getType().isAir()) {
                    items.add(item.serialize());
                }
            }
            if (!items.isEmpty()) {
                config.set("backpacks." + entry.getKey(), items);
            }
        }
        try {
            config.save(backpackFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存收购背包失败: " + e.getMessage());
        }
    }

    // ===== 跨世界转移仓库（按玩家，全局共享，仅白名单物品可入仓） =====

    /**
     * 获取玩家的转移仓库（为空时自动创建）
     */
    public List<ItemStack> getTransferVault(UUID ownerUuid) {
        return transferVaults.computeIfAbsent(ownerUuid, k -> new ArrayList<>());
    }

    /**
     * 判断物品是否允许入仓跨世界转移（PDC rpgforge:item-id 在白名单内）
     */
    public boolean isTransferAllowed(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        String id = meta.getPersistentDataContainer()
                .get(NamespacedKey.fromString("rpgforge:item-id"), PersistentDataType.STRING);
        return id != null && PluginSettings.transferAllowedIds().contains(id);
    }

    /**
     * 转移物品入仓：同类物品尽量并入已有堆，放不下则追加新条目
     */
    public void addItemToTransferVault(UUID ownerUuid, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        ItemStack add = item.clone();
        List<ItemStack> vault = getTransferVault(ownerUuid);
        int max = add.getMaxStackSize();
        for (ItemStack existing : vault) {
            if (add.getAmount() <= 0) break;
            if (existing.isSimilar(add) && existing.getAmount() < max) {
                int move = Math.min(max - existing.getAmount(), add.getAmount());
                existing.setAmount(existing.getAmount() + move);
                add.setAmount(add.getAmount() - move);
            }
        }
        if (add.getAmount() > 0) {
            vault.add(add);
        }
    }

    /**
     * 按索引取出一组物品（点击仓库物品槽取出单个）
     * @return 取出的物品；索引越界返回null
     */
    public ItemStack takeTransferItemAt(UUID ownerUuid, int index) {
        List<ItemStack> vault = transferVaults.get(ownerUuid);
        if (vault == null || index < 0 || index >= vault.size()) return null;
        return vault.remove(index);
    }

    /**
     * 清空并返回转移仓库全部物品（一键取回）
     */
    public List<ItemStack> clearTransferVault(UUID ownerUuid) {
        List<ItemStack> vault = transferVaults.remove(ownerUuid);
        return vault != null ? vault : new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private void loadTransferVaults() {
        if (!transferFile.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(transferFile);
        var section = config.getConfigurationSection("vaults");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID ownerUuid = UUID.fromString(key);
                List<ItemStack> items = new ArrayList<>();
                for (Map<?, ?> rawMap : config.getMapList("vaults." + key)) {
                    try {
                        items.add(ItemStack.deserialize((Map<String, Object>) rawMap));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                transferVaults.put(ownerUuid, items);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("转移仓库数据包含无效的UUID: " + key);
            }
        }
    }

    private void saveTransferVaults() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, List<ItemStack>> entry : transferVaults.entrySet()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ItemStack item : entry.getValue()) {
                if (item != null && !item.getType().isAir()) {
                    items.add(item.serialize());
                }
            }
            if (!items.isEmpty()) {
                config.set("vaults." + entry.getKey(), items);
            }
        }
        try {
            config.save(transferFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存转移仓库失败: " + e.getMessage());
        }
    }

    // ===== 创建 / 删除 =====

    public PhilosNPC createNPC(Player player) {
        if (PhilosNPCPlugin.economy() != null && PluginSettings.createCost() > 0) {
            EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, PluginSettings.createCost());
            if (!resp.transactionSuccess()) {
                player.sendMessage(PhilosNPCPlugin.cc("&c金币不足！创建NPC需要 " + PluginSettings.createCost() + " 金币"));
                return null;
            }
        }

        PhilosNPC npc = new PhilosNPC(player.getName(), player.getUniqueId(), player.getLocation());
        // 短ID：玩家名_递增数字（如 Notch_1），方便命令输入
        npc.setId(generateShortId(player.getName(), false));
        npcs.put(npc.getId(), npc);

        // 玩家形态：捕获创建者皮肤（含签名，正版/离线服均可用）
        captureSkin(npc, player);

        // 装备默认留空（玩家模型自带身体），可通过装备编辑界面手动穿戴

        // 同步共享商店背包到新NPC（按玩家当前世界隔离）
        ItemStack[] sharedInv = getSharedShopInventory(player.getUniqueId(), player.getWorld().getName());
        npc.setShopInventory(sharedInv);

        spawnNPC(npc);
        saveAll();

        player.sendMessage(PhilosNPCPlugin.cc("&a已创建NPC，ID: &f" + npc.getId()
                + (PluginSettings.createCost() > 0 && PhilosNPCPlugin.economy() != null ? "&a，花费 " + PluginSettings.createCost() + " 金币" : "")));
        return npc;
    }

    public PhilosNPC createSystemNPC(Player admin, String entityTypeName) {
        PhilosNPC npc = new PhilosNPC(admin.getName(), admin.getUniqueId(), admin.getLocation());
        npc.setNpcType(NPCType.SYSTEM);
        npc.setEntityTypeName(entityTypeName);
        npc.setId(generateShortId(admin.getName(), true));

        // 解析显示名
        if (entityTypeName.startsWith("PLAYER:")) {
            String playerName = entityTypeName.substring(7);
            npc.setDisplayName(playerName);
        } else {
            try {
                EntityType type = EntityType.valueOf(entityTypeName.toUpperCase());
                npc.setDisplayName(type.name());
            } catch (IllegalArgumentException e) {
                npc.setDisplayName(entityTypeName);
            }
        }

        npcs.put(npc.getId(), npc);
        spawnNPC(npc);
        saveAll();

        admin.sendMessage(PhilosNPCPlugin.cc("&d系统NPC创建成功！"));
        return npc;
    }

    public boolean deleteNPC(String id) {
        PhilosNPC npc = npcs.get(id);
        if (npc == null) return false;

        // 个人NPC删除退款给主人（金额见 config.yml，0=不退款）
        if (!npc.isSystem() && PhilosNPCPlugin.economy() != null && PluginSettings.deleteRefund() > 0) {
            PhilosNPCPlugin.economy().depositPlayer(
                    Bukkit.getOfflinePlayer(npc.getOwnerUuid()), PluginSettings.deleteRefund());
            Player owner = Bukkit.getPlayer(npc.getOwnerUuid());
            if (owner != null) {
                owner.sendMessage(PhilosNPCPlugin.cc("&e你的NPC被删除，退回 &6" + PluginSettings.deleteRefund() + " 金币"));
            }
        }

        despawnNPC(npc);
        npcs.remove(id);
        saveAll();
        return true;
    }

    // ===== 查询 =====

    public PhilosNPC getNPC(String id) {
        return npcs.get(id);
    }

    /**
     * 生成短ID：玩家名_递增数字（如 Notch_1、Notch_2），系统NPC为 sys_递增数字。
     * 从现有同前缀ID中取最大编号+1，避免重复。
     */
    private String generateShortId(String ownerName, boolean system) {
        String prefix = system ? "sys" : ownerName.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]", "");
        if (prefix.isEmpty()) prefix = "npc";
        int max = 0;
        for (String existingId : npcs.keySet()) {
            if (existingId.startsWith(prefix + "_")) {
                String suffix = existingId.substring(prefix.length() + 1);
                try {
                    max = Math.max(max, Integer.parseInt(suffix));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return prefix + "_" + (max + 1);
    }

    /**
     * 按ID查找NPC：精确匹配 → 唯一前缀匹配（支持旧UUID输前几位）。
     * 多个匹配时返回null并提示调用方歧义。
     */
    public PhilosNPC findNPC(String input) {
        if (input == null || input.isBlank()) return null;
        PhilosNPC exact = npcs.get(input);
        if (exact != null) return exact;

        List<PhilosNPC> matches = new ArrayList<>();
        for (PhilosNPC npc : npcs.values()) {
            if (npc.getId().startsWith(input)) {
                matches.add(npc);
            }
        }
        if (matches.size() == 1) return matches.get(0);
        return null;
    }

    /**
     * 前缀匹配到的NPC数量（用于歧义提示）
     */
    public int countNPCMatches(String input) {
        if (input == null || input.isBlank()) return 0;
        if (npcs.containsKey(input)) return 1;
        int count = 0;
        for (PhilosNPC npc : npcs.values()) {
            if (npc.getId().startsWith(input)) count++;
        }
        return count;
    }

    public PhilosNPC getNPCByEntityId(int entityId) {
        String npcId = entityIdMap.get(entityId);
        if (npcId == null) return null;
        return npcs.get(npcId);
    }

    public List<PhilosNPC> getNPCsByOwner(UUID ownerUuid) {
        List<PhilosNPC> result = new ArrayList<>();
        for (PhilosNPC npc : npcs.values()) {
            if (npc.getOwnerUuid().equals(ownerUuid)) {
                result.add(npc);
            }
        }
        return result;
    }

    public List<PhilosNPC> getSystemNPCs() {
        List<PhilosNPC> result = new ArrayList<>();
        for (PhilosNPC npc : npcs.values()) {
            if (npc.isSystem()) {
                result.add(npc);
            }
        }
        return result;
    }

    public Collection<PhilosNPC> getAllNPCs() {
        return npcs.values();
    }

    // ===== 共享商店背包 =====

    /**
     * 获取指定玩家在指定世界的共享商店背包（跨世界隔离，防止跨世界走私物品）
     * @param ownerUuid 玩家UUID
     * @param worldName 世界名
     * @return 商店背包物品数组（36格）
     */
    public ItemStack[] getSharedShopInventory(UUID ownerUuid, String worldName) {
        String key = scopeKey(ownerUuid, worldName);
        ItemStack[] inv = sharedShopInventories.get(key);
        if (inv == null) {
            inv = new ItemStack[36];
            sharedShopInventories.put(key, inv);
        }
        return inv;
    }

    /**
     * 设置指定玩家在指定世界的共享商店背包
     * @param ownerUuid 玩家UUID
     * @param worldName 世界名
     * @param inventory 背包物品数组
     */
    public void setSharedShopInventory(UUID ownerUuid, String worldName, ItemStack[] inventory) {
        sharedShopInventories.put(scopeKey(ownerUuid, worldName), inventory);
    }

    // ===== 商店背包编辑锁 =====

    /**
     * 尝试锁定共享商店背包用于编辑（同一背包同一时间只允许一名编辑者，防止关闭时互相覆盖）。
     * 编辑期间该店主该世界的所有NPC交易被冻结。
     * @return 锁定成功（或编辑者即本人重入）返回true
     */
    public boolean tryLockShopInventory(UUID ownerUuid, String worldName, UUID editor) {
        String key = scopeKey(ownerUuid, worldName);
        UUID current = shopEditLocks.get(key);
        if (current != null && !current.equals(editor)) return false;
        shopEditLocks.put(key, editor);
        return true;
    }

    /** 解锁共享商店背包（仅编辑者本人可解，幂等） */
    public void unlockShopInventory(UUID ownerUuid, String worldName, UUID editor) {
        if (editor != null && editor.equals(shopEditLocks.get(scopeKey(ownerUuid, worldName)))) {
            shopEditLocks.remove(scopeKey(ownerUuid, worldName));
        }
    }

    /** 该背包当前是否处于编辑锁定中（编辑期间冻结交易） */
    public boolean isShopInventoryLocked(UUID ownerUuid, String worldName) {
        return shopEditLocks.containsKey(scopeKey(ownerUuid, worldName));
    }

    /** 清除指定编辑者持有的全部背包锁（退出/换世界/重载时兜底，防止永久锁定） */
    public void clearShopEditLocks(UUID editor) {
        shopEditLocks.values().removeIf(editor::equals);
    }

    // ===== 实体生成 / 移除 =====

    public void spawnNPC(PhilosNPC npc) {
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        // 清理崩溃残留的孤儿实体：服务器非正常关停时持久化实体留在区块里，
        // 重启后这里删除未被管理的同ID实体，避免与新生成的NPC重复
        cleanupOrphanEntities(npc.getId());

        World world = loc.getWorld();

        if (npc.isSystem() && npc.getEntityTypeName() != null) {
            spawnSystemNPC(npc, loc, world);
        } else {
            spawnPersonalNPC(npc, loc, world);
        }
    }

    private void spawnPersonalNPC(PhilosNPC npc, Location loc, World world) {
        // 玩家形态：PacketEvents虚拟玩家实体，完整玩家模型
        // 旧盔甲架时代的玩家头颅头盔不再需要（自带玩家头部）
        ItemStack[] equipment = npc.getEquipment();
        if (equipment[0] != null && equipment[0].getType() == Material.PLAYER_HEAD) {
            equipment[0] = null;
        }

        // 皮肤兜底：无皮肤数据且创建者在线时抓取
        if (npc.getSkinValue() == null) {
            Player owner = Bukkit.getPlayer(npc.getOwnerUuid());
            if (owner != null) {
                captureSkin(npc, owner);
            }
            // 离线服PlayerProfile不带纹理（客户端不上报皮肤），按名字从Mojang拉取
            fetchSkinAsync(npc, npc.getOwnerName());
        }

        playerNpcSpawner.spawn(npc);
    }

    /**
     * 从玩家捕获皮肤纹理
     */
    public void captureSkin(PhilosNPC npc, Player player) {
        for (var prop : player.getPlayerProfile().getProperties()) {
            if ("textures".equals(prop.getName())) {
                npc.setSkinValue(prop.getValue());
                npc.setSkinSignature(prop.getSignature());
                return;
            }
        }
    }

    // 进行中的异步皮肤拉取（按npcId去重，避免区块加载反复触发）
    private final java.util.Set<String> pendingSkinFetch = new java.util.HashSet<>();

    /**
     * 异步按玩家名从Mojang拉取皮肤（离线服PlayerProfile无纹理时的兜底）。
     * 直接走Mojang公开API：名字→正版UUID→带签名的纹理属性。
     * 拉取成功后保存数据并重建虚拟NPC外观。
     */
    public void fetchSkinAsync(PhilosNPC npc, String playerName) {
        if (npc.getSkinValue() != null || playerName == null || playerName.isBlank()) return;
        if (!pendingSkinFetch.add(npc.getId())) return;
        String npcId = npc.getId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String value = null;
            String signature = null;
            try {
                String id = extractJsonString(
                        httpGet("https://api.mojang.com/users/profiles/minecraft/"
                                + java.net.URLEncoder.encode(playerName, java.nio.charset.StandardCharsets.UTF_8)),
                        "id", 32);
                if (id != null) {
                    String texJson = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/"
                            + id + "?unsigned=false");
                    value = extractJsonString(texJson, "value", 100);
                    signature = extractJsonString(texJson, "signature", 100);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("拉取玩家皮肤失败: " + playerName + " - " + t.getMessage());
            }
            if (value == null) {
                plugin.getLogger().info("未能获取玩家 " + playerName
                        + " 的正版皮肤（非正版账号名或网络不可达），NPC将保持默认皮肤");
            }
            final String v = value;
            final String s = signature;
            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingSkinFetch.remove(npcId);
                PhilosNPC current = npcs.get(npcId);
                if (current == null || current.getSkinValue() != null || v == null) return;
                current.setSkinValue(v);
                current.setSkinSignature(s);
                saveAll();
                playerNpcSpawner.updateSkin(current);
            });
        });
    }

    /**
     * 简易GET请求，失败或非200时返回null
     */
    private static String httpGet(String url) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("User-Agent", "PhilosNPC")
                .timeout(java.time.Duration.ofSeconds(5))
                .GET()
                .build();
        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 ? response.body() : null;
    }

    /**
     * 从扁平JSON提取指定key的字符串值（要求长度下限，避免误匹配）
     */
    private static String extractJsonString(String json, String key, int minLength) {
        if (json == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*\"([^\"]{" + minLength + ",})\"")
                .matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private void spawnSystemNPC(PhilosNPC npc, Location loc, World world) {
        String typeName = npc.getEntityTypeName();

        if (typeName.startsWith("PLAYER:")) {
            // 玩家型系统NPC：虚拟玩家实体，指定玩家名的皮肤（在线时抓取）
            String playerName = typeName.substring(7);
            ItemStack[] equipment = npc.getEquipment();
            if (equipment != null && equipment[0] != null && equipment[0].getType() == Material.PLAYER_HEAD) {
                equipment[0] = null;
            }

            if (npc.getSkinValue() == null) {
                Player target = Bukkit.getPlayerExact(playerName);
                if (target != null) {
                    captureSkin(npc, target);
                }
                // 离线服抓不到纹理时按名字从Mojang拉取
                fetchSkinAsync(npc, playerName);
            }

            playerNpcSpawner.spawn(npc);
        } else {
            // 生物型系统NPC：生成实际生物实体
            try {
                EntityType entityType = EntityType.valueOf(typeName.toUpperCase());
                var entity = world.spawnEntity(loc, entityType, SpawnReason.CUSTOM);

                if (entity instanceof LivingEntity living) {
                    living.setCustomName(PhilosNPCPlugin.cc(npc.getDisplayName()));
                    living.setCustomNameVisible(true);
                    living.setInvulnerable(true);
                    living.setSilent(true);
                    living.setPersistent(true);
                    living.setRemoveWhenFarAway(false);

                    // 尝试关闭AI
                    try {
                        living.setAI(false);
                    } catch (Exception ignored) {}

                    // 设置装备
                    EntityEquipment eq = living.getEquipment();
                    if (eq != null) {
                        ItemStack[] equipment = npc.getEquipment();
                        if (equipment != null) {
                            if (equipment[0] != null) eq.setHelmet(equipment[0], true);
                            if (equipment[1] != null) eq.setChestplate(equipment[1], true);
                            if (equipment[2] != null) eq.setLeggings(equipment[2], true);
                            if (equipment[3] != null) eq.setBoots(equipment[3], true);
                            if (equipment[4] != null) eq.setItemInMainHand(equipment[4], true);
                        }
                    }

                    // 设置大小
                    applyScale(living, npc.getScale());
                }

                entity.getPersistentDataContainer().set(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING, npc.getId());
                registerEntity(entity, npc.getId());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无法创建系统NPC，未知实体类型: " + typeName);
            }
        }
    }

    public PlayerNpcSpawner playerNpcSpawner() { return playerNpcSpawner; }

    // ===== 重新生成NPC以应用装备/外观变更 =====

    public void respawnNPC(PhilosNPC npc) {
        despawnNPC(npc);
        spawnNPC(npc);
    }

    public void despawnNPC(PhilosNPC npc) {
        // 玩家形态NPC：销毁虚拟实体
        playerNpcSpawner.despawn(npc);

        // 找到对应的实体并移除（生物型）
        Iterator<Map.Entry<Integer, String>> iterator = entityIdMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, String> entry = iterator.next();
            if (entry.getValue().equals(npc.getId())) {
                int entityId = entry.getKey();
                // 遍历所有已加载的实体来查找
                for (World world : Bukkit.getWorlds()) {
                    for (Entity e : world.getEntities()) {
                        if (e.getEntityId() == entityId) {
                            e.remove();
                            break;
                        }
                    }
                }
                headStates.remove(entityId);
                entityRefs.remove(entityId);
                iterator.remove();
            }
        }
    }

    /**
     * 移除世界中带NPC标记但未被本插件管理的实体（崩溃残留）。
     */
    private void cleanupOrphanEntities(String npcId) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                String id = entity.getPersistentDataContainer()
                        .get(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING);
                if (npcId.equals(id) && !entityIdMap.containsKey(entity.getEntityId())) {
                    entity.remove();
                }
            }
        }
    }

    public void despawnAll() {
        playerNpcSpawner.despawnAll();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                if (pdc.has(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
        entityIdMap.clear();
        headStates.clear();
        entityRefs.clear();
    }

    // 重新生成指定区块内的NPC
    public void respawnNPCsInChunk(Chunk chunk) {
        for (PhilosNPC npc : npcs.values()) {
            Location loc = npc.getLocation();
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().equals(chunk.getWorld())) continue;

            int npcChunkX = loc.getBlockX() >> 4;
            int npcChunkZ = loc.getBlockZ() >> 4;

            if (npcChunkX == chunk.getX() && npcChunkZ == chunk.getZ()) {
                // 检查是否已经有实体了
                if (!entityIdMap.containsValue(npc.getId())) {
                    spawnNPC(npc);
                }
            }
        }
    }

    // 移除指定区块内的NPC实体（保留数据）
    public void despawnNPCsInChunk(Chunk chunk) {
        for (PhilosNPC npc : npcs.values()) {
            Location loc = npc.getLocation();
            if (loc == null || loc.getWorld() == null) continue;
            if (!loc.getWorld().equals(chunk.getWorld())) continue;

            int npcChunkX = loc.getBlockX() >> 4;
            int npcChunkZ = loc.getBlockZ() >> 4;

            if (npcChunkX == chunk.getX() && npcChunkZ == chunk.getZ()) {
                despawnNPC(npc);
            }
        }
    }

    // ===== 村民式头部动画 =====

    /**
     * 单个NPC的头部动画状态
     */
    private static class HeadState {
        double yawOff = 0;      // 当前头部偏航偏移
        double pitchOff = 0;    // 当前俯仰偏移
        double targetYawOff = 0;
        double targetPitchOff = 0;
        int idleCooldown = 0;   // 闲置随机张望的间隔
        long nextSense = 0;     // 下次感知玩家的tick
    }

    /**
     * 启动村民式头部动画：
     * - 附近有玩家时平滑转头看向玩家（含抬头低头）
     * - 无玩家时偶尔随机张望
     * - 头部偏转超过阈值时身体缓慢转向，像村民转身
     */
    public void startHeadAnimation() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long tick = Bukkit.getCurrentTick();
            for (Map.Entry<Integer, String> entry : entityIdMap.entrySet()) {
                Entity entity = entityRefs.get(entry.getKey());
                if (!(entity instanceof LivingEntity living) || !living.isValid()) continue;
                PhilosNPC npc = npcs.get(entry.getValue());
                if (npc == null) continue;

                HeadState st = headStates.computeIfAbsent(entry.getKey(), k -> new HeadState());

                if (tick >= st.nextSense) {
                    st.nextSense = tick + 5;
                    updateLookTarget(living, st);
                }
                animateHead(living, st);
            }
            // 清理已消失实体的状态
            headStates.keySet().removeIf(id -> {
                Entity e = entityRefs.get(id);
                return e == null || !e.isValid();
            });
        }, 20L, 1L);
    }

    private final Random animRandom = new Random();

    /**
     * 更新注视目标：5格内最近玩家优先；否则进入闲置随机张望
     */
    private void updateLookTarget(LivingEntity living, HeadState st) {
        Location eye = living.getEyeLocation();
        Player nearest = null;
        double best = 25.0; // 5格距离的平方

        for (Entity nearby : living.getNearbyEntities(5, 4, 5)) {
            if (nearby instanceof Player p && !p.isDead()
                    && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                double dist = p.getLocation().distanceSquared(eye);
                if (dist < best) {
                    best = dist;
                    nearest = p;
                }
            }
        }

        if (nearest != null) {
            org.bukkit.util.Vector toPlayer = nearest.getEyeLocation().toVector().subtract(eye.toVector());
            double targetYaw = Math.toDegrees(Math.atan2(-toPlayer.getX(), toPlayer.getZ()));
            double horizontal = Math.sqrt(toPlayer.getX() * toPlayer.getX() + toPlayer.getZ() * toPlayer.getZ());
            double targetPitch = -Math.toDegrees(Math.atan2(toPlayer.getY(), horizontal));

            double yawDiff = wrapDegrees(targetYaw - living.getLocation().getYaw());
            st.targetYawOff = Math.toRadians(clamp(yawDiff, -75, 75));
            st.targetPitchOff = Math.toRadians(clamp(targetPitch, -40, 40));
        } else if (st.idleCooldown <= 0) {
            // 闲置：3~8秒随机看一个方向
            st.idleCooldown = 60 + animRandom.nextInt(140);
            st.targetYawOff = (animRandom.nextDouble() - 0.5) * Math.toRadians(60);
            st.targetPitchOff = (animRandom.nextDouble() - 0.5) * Math.toRadians(16);
        }
    }

    /**
     * 每tick插值执行转头；头部偏转过大时身体跟随转向
     */
    private void animateHead(LivingEntity living, HeadState st) {
        if (st.idleCooldown > 0) st.idleCooldown--;

        double maxStep = Math.toRadians(5.0); // 每tick最多5度，平滑转头
        st.yawOff = approach(st.yawOff, st.targetYawOff, maxStep);
        st.pitchOff = approach(st.pitchOff, st.targetPitchOff, maxStep * 0.8);

        if (living instanceof ArmorStand stand) {
            // 头偏超过55度时身体慢慢转过去，头相对回中
            double yawOffDeg = Math.toDegrees(st.yawOff);
            if (Math.abs(yawOffDeg) > 55) {
                double turn = Math.signum(yawOffDeg) * 2.5;
                living.setRotation((float) (living.getLocation().getYaw() + turn), 0);
                st.yawOff = Math.toRadians(yawOffDeg - turn);
            }
            stand.setHeadPose(new EulerAngle(st.pitchOff, st.yawOff, 0));
        } else {
            // 非盔甲架生物NPC：头身一体，整体朝向插值转向玩家
            double yawOffDeg = Math.toDegrees(st.targetYawOff);
            if (Math.abs(yawOffDeg) > 1.0) {
                double step = clamp(yawOffDeg, -4, 4);
                living.setRotation((float) (living.getLocation().getYaw() + step), 0);
                st.targetYawOff = Math.toRadians(yawOffDeg - step);
                st.yawOff = st.targetYawOff;
            }
        }
    }

    private static double approach(double current, double target, double maxStep) {
        double diff = target - current;
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.signum(diff) * maxStep;
    }

    private static double wrapDegrees(double angle) {
        angle %= 360.0;
        if (angle > 180.0) angle -= 360.0;
        if (angle < -180.0) angle += 360.0;
        return angle;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 应用模型缩放：使用1.20.5+的SCALE属性（0.0625~16）
     */
    private void applyScale(LivingEntity entity, double scale) {
        AttributeInstance attr = entity.getAttribute(Attribute.SCALE);
        if (attr != null) {
            attr.setBaseValue(Math.max(0.0625, Math.min(16.0, scale)));
        }
    }
}
