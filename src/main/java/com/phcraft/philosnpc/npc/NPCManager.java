package com.phcraft.philosnpc.npc;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
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
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
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
    private final Map<UUID, ItemStack[]> sharedShopInventories;
    private final File dataFile;

    // 默认皮革装备颜色（史蒂夫配色：蓝青色系）
    private static final Color DEFAULT_LEATHER_COLOR = Color.fromRGB(70, 130, 180); // 钢蓝色

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

        // 设置默认装备：玩家皮肤头颅 + 皮革装备 + 主手物品
        ItemStack[] npcEquip = npc.getEquipment();

        // 头盔：玩家皮肤头颅（必设，保证显示玩家头）
        npcEquip[0] = createPlayerHead(player.getName());

        // 胸甲/护腿/靴子：默认皮革装备（史蒂夫蓝配色），如果玩家有穿则用玩家的
        ItemStack[] playerEquip = player.getEquipment().getArmorContents();
        // playerEquip顺序: 0=靴子, 1=护腿, 2=胸甲, 3=头盔
        // npcEquip顺序: 0=头盔, 1=胸甲, 2=护腿, 3=靴子, 4=主手

        // 胸甲
        if (playerEquip[2] != null && playerEquip[2].getType() != Material.AIR) {
            npcEquip[1] = playerEquip[2].clone();
        } else {
            npcEquip[1] = createLeatherArmor(Material.LEATHER_CHESTPLATE, DEFAULT_LEATHER_COLOR);
        }
        // 护腿
        if (playerEquip[1] != null && playerEquip[1].getType() != Material.AIR) {
            npcEquip[2] = playerEquip[1].clone();
        } else {
            npcEquip[2] = createLeatherArmor(Material.LEATHER_LEGGINGS, DEFAULT_LEATHER_COLOR);
        }
        // 靴子
        if (playerEquip[0] != null && playerEquip[0].getType() != Material.AIR) {
            npcEquip[3] = playerEquip[0].clone();
        } else {
            npcEquip[3] = createLeatherArmor(Material.LEATHER_BOOTS, DEFAULT_LEATHER_COLOR);
        }
        // 主手
        if (player.getEquipment().getItemInMainHand() != null
                && player.getEquipment().getItemInMainHand().getType() != Material.AIR) {
            npcEquip[4] = player.getEquipment().getItemInMainHand().clone();
        } else {
            // 默认手持物品
            npcEquip[4] = new ItemStack(Material.STICK);
        }

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

        // 显示手臂，去掉底座
        entity.setArms(true);
        entity.setBasePlate(false);

        applyScale(entity, npc.getScale());

        // 装备设置：确保头盔是玩家头颅
        EntityEquipment eq = entity.getEquipment();
        if (eq != null) {
            ItemStack[] equipment = npc.getEquipment();

            // 头盔：确保是玩家头颅
            if (equipment[0] != null && equipment[0].getType() == Material.PLAYER_HEAD) {
                eq.setHelmet(equipment[0], true);
            } else {
                // 如果头盔不是玩家头颅或为空，创建一个玩家头颅
                ItemStack head = createPlayerHead(npc.getOwnerName());
                eq.setHelmet(head, true);
                // 同步回数据模型
                equipment[0] = head;
            }

            // 胸甲：如果没有则给默认皮革装备
            if (equipment[1] != null) {
                eq.setChestplate(equipment[1], true);
            } else {
                ItemStack chest = createLeatherArmor(Material.LEATHER_CHESTPLATE, DEFAULT_LEATHER_COLOR);
                eq.setChestplate(chest, true);
                equipment[1] = chest;
            }

            // 护腿
            if (equipment[2] != null) {
                eq.setLeggings(equipment[2], true);
            } else {
                ItemStack legs = createLeatherArmor(Material.LEATHER_LEGGINGS, DEFAULT_LEATHER_COLOR);
                eq.setLeggings(legs, true);
                equipment[2] = legs;
            }

            // 靴子
            if (equipment[3] != null) {
                eq.setBoots(equipment[3], true);
            } else {
                ItemStack boots = createLeatherArmor(Material.LEATHER_BOOTS, DEFAULT_LEATHER_COLOR);
                eq.setBoots(boots, true);
                equipment[3] = boots;
            }

            // 主手
            if (equipment[4] != null) {
                eq.setItemInMainHand(equipment[4], true);
            } else {
                ItemStack hand = new ItemStack(Material.STICK);
                eq.setItemInMainHand(hand, true);
                equipment[4] = hand;
            }
        }

        applyPose(entity, npc.getPose());

        NamespacedKey key = PhilosNPCPlugin.npcIdKey();
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, npc.getId());
        entityIdMap.put(entity.getEntityId(), npc.getId());
    }

    private void spawnSystemNPC(PhilosNPC npc, Location loc, World world) {
        String typeName = npc.getEntityTypeName();

        if (typeName.startsWith("PLAYER:")) {
            // 玩家型系统NPC：用ArmorStand + 指定玩家头颅 + 皮革身体
            String playerName = typeName.substring(7);
            ArmorStand entity = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND, SpawnReason.CUSTOM);

            entity.setCustomName(PhilosNPCPlugin.cc(npc.getDisplayName()));
            entity.setCustomNameVisible(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setPersistent(true);

            // 显示手臂，去掉底座
            entity.setArms(true);
            entity.setBasePlate(false);

            applyScale(entity, npc.getScale());

            EntityEquipment eq = entity.getEquipment();
            if (eq != null) {
                ItemStack[] equipment = npc.getEquipment();
                boolean hasChest = false, hasLeggings = false, hasBoots = false;

                // 头盔：玩家头颅
                if (equipment != null && equipment[0] != null && equipment[0].getType() == Material.PLAYER_HEAD) {
                    eq.setHelmet(equipment[0], true);
                } else {
                    ItemStack skull = createPlayerHead(playerName);
                    eq.setHelmet(skull, true);
                    if (equipment != null) equipment[0] = skull;
                }

                if (equipment != null) {
                    if (equipment[1] != null) { eq.setChestplate(equipment[1], true); hasChest = true; }
                    if (equipment[2] != null) { eq.setLeggings(equipment[2], true); hasLeggings = true; }
                    if (equipment[3] != null) { eq.setBoots(equipment[3], true); hasBoots = true; }
                    if (equipment[4] != null) eq.setItemInMainHand(equipment[4], true);
                }

                // 默认皮革套装作为身体基础显示（深紫色系，系统NPC风格）
                Color sysColor = Color.fromRGB(128, 0, 128); // 紫色
                if (!hasChest) {
                    eq.setChestplate(createLeatherArmor(Material.LEATHER_CHESTPLATE, sysColor), true);
                }
                if (!hasLeggings) {
                    eq.setLeggings(createLeatherArmor(Material.LEATHER_LEGGINGS, sysColor), true);
                }
                if (!hasBoots) {
                    eq.setBoots(createLeatherArmor(Material.LEATHER_BOOTS, sysColor), true);
                }

                // 主手默认物品
                if (equipment == null || equipment[4] == null) {
                    eq.setItemInMainHand(new ItemStack(Material.STICK), true);
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
                    applyScale(living, npc.getScale());
                }

                entity.getPersistentDataContainer().set(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING, npc.getId());
                entityIdMap.put(entity.getEntityId(), npc.getId());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无法创建系统NPC，未知实体类型: " + typeName);
            }
        }
    }

    // ===== 重新生成NPC以应用装备/外观变更 =====

    public void respawnNPC(PhilosNPC npc) {
        despawnNPC(npc);
        spawnNPC(npc);
    }

    public void despawnNPC(PhilosNPC npc) {
        // 找到对应的实体并移除
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

    /**
     * 应用姿势：通过盔甲架身体各部位的EulerAngle旋转实现视觉效果
     */
    private void applyPose(ArmorStand entity, NPCPose pose) {
        switch (pose) {
            case STANDING:
                entity.setArms(true);
                entity.setBodyPose(EulerAngle.ZERO);
                entity.setHeadPose(EulerAngle.ZERO);
                entity.setLeftArmPose(new EulerAngle(Math.toRadians(-10), 0, Math.toRadians(-10)));
                entity.setRightArmPose(new EulerAngle(Math.toRadians(-15), 0, Math.toRadians(10)));
                entity.setLeftLegPose(EulerAngle.ZERO);
                entity.setRightLegPose(EulerAngle.ZERO);
                break;
            case SNEAKING:
                // 身体前倾 + 头部低垂 + 弯腿
                entity.setArms(true);
                entity.setBodyPose(new EulerAngle(Math.toRadians(30), 0, 0));
                entity.setHeadPose(new EulerAngle(Math.toRadians(25), 0, 0));
                entity.setLeftArmPose(new EulerAngle(Math.toRadians(-60), 0, Math.toRadians(-8)));
                entity.setRightArmPose(new EulerAngle(Math.toRadians(-65), 0, Math.toRadians(8)));
                entity.setLeftLegPose(new EulerAngle(Math.toRadians(55), 0, Math.toRadians(-4)));
                entity.setRightLegPose(new EulerAngle(Math.toRadians(55), 0, Math.toRadians(4)));
                break;
            case SITTING:
                // 双腿前伸模拟坐姿
                entity.setArms(true);
                entity.setBodyPose(EulerAngle.ZERO);
                entity.setHeadPose(EulerAngle.ZERO);
                entity.setLeftArmPose(new EulerAngle(Math.toRadians(-10), 0, Math.toRadians(-10)));
                entity.setRightArmPose(new EulerAngle(Math.toRadians(-15), 0, Math.toRadians(10)));
                entity.setLeftLegPose(new EulerAngle(Math.toRadians(-88), 0, Math.toRadians(12)));
                entity.setRightLegPose(new EulerAngle(Math.toRadians(-88), 0, Math.toRadians(-12)));
                break;
            case LYING:
                // 身体放平仰躺
                entity.setArms(false);
                entity.setBodyPose(new EulerAngle(Math.toRadians(90), 0, 0));
                entity.setHeadPose(new EulerAngle(Math.toRadians(20), 0, 0));
                entity.setLeftArmPose(new EulerAngle(Math.toRadians(165), 0, Math.toRadians(15)));
                entity.setRightArmPose(new EulerAngle(Math.toRadians(165), 0, Math.toRadians(-15)));
                entity.setLeftLegPose(new EulerAngle(Math.toRadians(15), 0, Math.toRadians(3)));
                entity.setRightLegPose(new EulerAngle(Math.toRadians(15), 0, Math.toRadians(-3)));
                break;
            case DANCING:
                // 双臂高举 + 扭腰
                entity.setArms(true);
                entity.setBodyPose(new EulerAngle(0, 0, Math.toRadians(-8)));
                entity.setHeadPose(new EulerAngle(Math.toRadians(-12), Math.toRadians(18), 0));
                entity.setLeftArmPose(new EulerAngle(Math.toRadians(160), 0, Math.toRadians(35)));
                entity.setRightArmPose(new EulerAngle(Math.toRadians(160), 0, Math.toRadians(-35)));
                entity.setLeftLegPose(new EulerAngle(0, 0, Math.toRadians(8)));
                entity.setRightLegPose(new EulerAngle(0, 0, Math.toRadians(-8)));
                break;
        }
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

    /**
     * 创建玩家头颅物品
     */
    private ItemStack createPlayerHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwner(playerName);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * 创建染色皮革装备
     */
    private ItemStack createLeatherArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }
}
