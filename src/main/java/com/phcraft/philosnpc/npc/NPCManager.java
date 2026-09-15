package com.phcraft.philosnpc.npc;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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

import java.io.File;
import java.io.IOException;
import java.util.*;

public class NPCManager {

    private final PhilosNPCPlugin plugin;
    private final Map<String, PhilosNPC> npcs;
    private final Map<Integer, String> entityIdMap;
    private final Map<UUID, ItemStack[]> sharedShopInventories;
    private final File dataFile;

    public NPCManager() {
        this.plugin = PhilosNPCPlugin.instance();
        this.npcs = new HashMap<>();
        this.entityIdMap = new HashMap<>();
        this.sharedShopInventories = new HashMap<>();
        this.dataFile = new File(plugin.getDataFolder(), "npcs.yml");
    }

    // ===== 加载 / 保存 =====

    @SuppressWarnings("unchecked")
    public void loadAll() {
        if (!dataFile.exists()) {
            plugin.saveResource("npcs.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        if (!config.contains("npcs")) {
            return;
        }

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

        // 初始化共享商店背包（按玩家UUID汇总）
        for (PhilosNPC npc : npcs.values()) {
            UUID ownerUuid = npc.getOwnerUuid();
            if (!sharedShopInventories.containsKey(ownerUuid)) {
                // 以第一个找到的NPC的商店背包作为共享背包
                sharedShopInventories.put(ownerUuid, npc.getShopInventory().clone());
            }
            // 同步共享背包到该NPC（确保所有NPC数据一致）
            npc.setShopInventory(sharedShopInventories.get(ownerUuid));
        }

        plugin.getLogger().info("已加载 " + npcs.size() + " 个NPC");
    }

    public void saveAll() {
        // 保存前，将共享商店背包同步到所有NPC
        for (PhilosNPC npc : npcs.values()) {
            UUID ownerUuid = npc.getOwnerUuid();
            ItemStack[] sharedInv = sharedShopInventories.get(ownerUuid);
            if (sharedInv != null) {
                npc.setShopInventory(sharedInv.clone());
            }
        }

        FileConfiguration config = new YamlConfiguration();
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
    }

    // ===== 创建 / 删除 =====

    public PhilosNPC createNPC(Player player) {
        // 扣500块
        if (PhilosNPCPlugin.economy() != null) {
            EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, PhilosNPCPlugin.CREATE_COST);
            if (!resp.transactionSuccess()) {
                player.sendMessage(PhilosNPCPlugin.cc("&c金币不足！创建NPC需要 " + PhilosNPCPlugin.CREATE_COST + " 金币"));
                return null;
            }
        }

        PhilosNPC npc = new PhilosNPC(player.getName(), player.getUniqueId(), player.getLocation());
        npcs.put(npc.getId(), npc);

        // 同步共享商店背包到新NPC
        ItemStack[] sharedInv = getSharedShopInventory(player.getUniqueId());
        npc.setShopInventory(sharedInv);

        spawnNPC(npc);
        saveAll();

        player.sendMessage(PhilosNPCPlugin.cc("&aNPC创建成功！花费 " + PhilosNPCPlugin.CREATE_COST + " 金币"));
        return npc;
    }

    public PhilosNPC createSystemNPC(Player admin, String entityTypeName) {
        PhilosNPC npc = new PhilosNPC(admin.getName(), admin.getUniqueId(), admin.getLocation());
        npc.setNpcType(NPCType.SYSTEM);
        npc.setEntityTypeName(entityTypeName);

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

        despawnNPC(npc);
        npcs.remove(id);
        saveAll();
        return true;
    }

    // ===== 查询 =====

    public PhilosNPC getNPC(String id) {
        return npcs.get(id);
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

    public Collection<PhilosNPC> getAllNPCs() {
        return npcs.values();
    }

    // ===== 共享商店背包 =====

    /**
     * 获取指定玩家的共享商店背包
     * @param ownerUuid 玩家UUID
     * @return 商店背包物品数组（36格）
     */
    public ItemStack[] getSharedShopInventory(UUID ownerUuid) {
        ItemStack[] inv = sharedShopInventories.get(ownerUuid);
        if (inv == null) {
            inv = new ItemStack[36];
            sharedShopInventories.put(ownerUuid, inv);
        }
        return inv;
    }

    /**
     * 设置指定玩家的共享商店背包
     * @param ownerUuid 玩家UUID
     * @param inventory 背包物品数组
     */
    public void setSharedShopInventory(UUID ownerUuid, ItemStack[] inventory) {
        sharedShopInventories.put(ownerUuid, inventory);
    }

    // ===== 实体生成 / 移除 =====

    public void spawnNPC(PhilosNPC npc) {
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        World world = loc.getWorld();

        if (npc.isSystem() && npc.getEntityTypeName() != null) {
            spawnSystemNPC(npc, loc, world);
        } else {
            spawnPersonalNPC(npc, loc, world);
        }
    }

    private void spawnPersonalNPC(PhilosNPC npc, Location loc, World world) {
        ArmorStand entity = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND, SpawnReason.CUSTOM);

        entity.setCustomName(PhilosNPCPlugin.cc(npc.getDisplayName()));
        entity.setCustomNameVisible(true);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.setPersistent(true);

        try {
            var method = entity.getClass().getMethod("setScale", float.class);
            method.invoke(entity, (float) npc.getScale());
        } catch (Exception ignored) {
            if (npc.getScale() < 0.75) entity.setSmall(true);
        }

        EntityEquipment eq = entity.getEquipment();
        if (eq != null) {
            ItemStack[] equipment = npc.getEquipment();
            if (equipment[0] != null) eq.setHelmet(equipment[0], true);
            if (equipment[1] != null) eq.setChestplate(equipment[1], true);
            if (equipment[2] != null) eq.setLeggings(equipment[2], true);
            if (equipment[3] != null) eq.setBoots(equipment[3], true);
            if (equipment[4] != null) eq.setItemInMainHand(equipment[4], true);
        }

        applyPose(entity, npc.getPose());

        NamespacedKey key = PhilosNPCPlugin.npcIdKey();
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, npc.getId());
        entityIdMap.put(entity.getEntityId(), npc.getId());
    }

    private void spawnSystemNPC(PhilosNPC npc, Location loc, World world) {
        String typeName = npc.getEntityTypeName();

        if (typeName.startsWith("PLAYER:")) {
            // 玩家型系统NPC：用ArmorStand + 指定玩家头颅
            String playerName = typeName.substring(7);
            ArmorStand entity = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND, SpawnReason.CUSTOM);

            entity.setCustomName(PhilosNPCPlugin.cc(npc.getDisplayName()));
            entity.setCustomNameVisible(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setPersistent(true);

            try {
                var method = entity.getClass().getMethod("setScale", float.class);
                method.invoke(entity, (float) npc.getScale());
            } catch (Exception ignored) {
                if (npc.getScale() < 0.75) entity.setSmall(true);
            }

            // 设置玩家头颅
            var skull = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD);
            var skullMeta = (org.bukkit.inventory.meta.SkullMeta) skull.getItemMeta();
            if (skullMeta != null) {
                skullMeta.setOwner(playerName);
                skull.setItemMeta(skullMeta);
            }
            EntityEquipment eq = entity.getEquipment();
            if (eq != null) {
                eq.setHelmet(skull, true);
                ItemStack[] equipment = npc.getEquipment();
                if (equipment != null) {
                    if (equipment[1] != null) eq.setChestplate(equipment[1], true);
                    if (equipment[2] != null) eq.setLeggings(equipment[2], true);
                    if (equipment[3] != null) eq.setBoots(equipment[3], true);
                    if (equipment[4] != null) eq.setItemInMainHand(equipment[4], true);
                }
            }

            applyPose(entity, npc.getPose());

            entity.getPersistentDataContainer().set(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING, npc.getId());
            entityIdMap.put(entity.getEntityId(), npc.getId());
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
                    try {
                        var method = living.getClass().getMethod("setScale", float.class);
                        method.invoke(living, (float) npc.getScale());
                    } catch (Exception ignored) {}
                }

                entity.getPersistentDataContainer().set(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING, npc.getId());
                entityIdMap.put(entity.getEntityId(), npc.getId());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无法创建系统NPC，未知实体类型: " + typeName);
            }
        }
    }

    public void despawnNPC(PhilosNPC npc) {
        // 找到对应的实体并移除
        Iterator<Map.Entry<Integer, String>> iterator = entityIdMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, String> entry = iterator.next();
            if (entry.getValue().equals(npc.getId())) {
                int entityId = entry.getKey();
                Entity entity = Bukkit.getEntity(java.util.UUID.randomUUID()); // 不能直接通过id查
                // 遍历所有已加载的实体来查找
                for (World world : Bukkit.getWorlds()) {
                    for (Entity e : world.getEntities()) {
                        if (e.getEntityId() == entityId) {
                            e.remove();
                            break;
                        }
                    }
                }
                iterator.remove();
            }
        }
    }

    public void despawnAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                PersistentDataContainer pdc = entity.getPersistentDataContainer();
                if (pdc.has(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
        entityIdMap.clear();
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

    // ===== 头部动画 =====

    public void startHeadAnimation() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    PersistentDataContainer pdc = entity.getPersistentDataContainer();
                    if (!pdc.has(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING)) continue;

                    if (entity instanceof LivingEntity living) {
                        float currentYaw = living.getLocation().getYaw();
                        float delta = (float) (Math.random() * 10.0 - 5.0);
                        float newYaw = currentYaw + delta;

                        Location loc = living.getLocation();
                        loc.setYaw(newYaw);
                        living.teleport(loc);
                    }
                }
            }
        }, 20L, 20L);
    }

    // ===== 辅助方法 =====

    private void applyPose(ArmorStand entity, NPCPose pose) {
        switch (pose) {
            case STANDING:
                entity.setArms(true);
                entity.setBasePlate(true);
                entity.setSmall(false);
                break;
            case SNEAKING:
                entity.setArms(true);
                entity.setBasePlate(true);
                entity.setSmall(false);
                // 略微降低高度模拟潜行
                break;
            case SITTING:
                entity.setArms(true);
                entity.setBasePlate(false);
                // 坐姿可以通过调整身体位置实现
                break;
            case LYING:
                entity.setArms(false);
                entity.setBasePlate(false);
                // 躺卧
                break;
            case DANCING:
                entity.setArms(true);
                entity.setBasePlate(true);
                break;
        }
    }
}
