package com.phcraft.philosnpc.gui;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.FeatureType;
import com.phcraft.philosnpc.npc.NPCPose;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class NPCGui {

    // ===== 主界面 =====

    public static Inventory mainGui(PhilosNPC npc, InventoryHolder holder) {
        String typePrefix = npc.isSystem() ? "&d&l" : "&b&l";
        Inventory inv = Bukkit.createInventory(holder, 54,
                PhilosNPCPlugin.cc(typePrefix + "NPC管理 - " + npc.getDisplayName()));

        inv.setItem(13, createNPCHead(npc));

        // slot 19: 姿势调整按钮 (ARMOR_STAND)
        inv.setItem(19, createItem(
                Material.ARMOR_STAND,
                "&a姿势调整",
                "&7当前姿势: &f" + npc.getPose().displayName(),
                "&7左键点击修改姿势"
        ));

        // slot 20: 大小调整按钮 (SLIME_BALL)
        inv.setItem(20, createItem(
                Material.SLIME_BALL,
                "&a大小调整",
                "&7当前大小: &f" + String.format("%.1f", npc.getScale()),
                "&7左键点击调整大小"
        ));

        // slot 21: 移动NPC (COMPASS)
        inv.setItem(21, createItem(
                Material.COMPASS,
                "&a移动NPC",
                "&7将NPC移动到当前位置",
                "&7左键点击开始移动"
        ));

        // slot 22: 删除NPC (BARRIER) - 红色警告
        inv.setItem(22, createItem(
                Material.BARRIER,
                "&c删除NPC",
                "&c警告：此操作不可撤销！",
                "&7左键点击删除NPC"
        ));

        // slot 23: 装备编辑 (DIAMOND_CHESTPLATE)
        inv.setItem(23, createItem(
                Material.DIAMOND_CHESTPLATE,
                "&a装备编辑",
                "&7修改NPC的装备外观",
                "&7头盔/胸甲/护腿/靴子/主手",
                "&7左键点击编辑装备"
        ));

        // slot 24: 传送至NPC (ENDER_PEARL) - 花费10元
        inv.setItem(24, createItem(
                Material.ENDER_PEARL,
                "&a传送至NPC",
                "&7花费: &6" + PhilosNPCPlugin.TP_TO_NPC_COST + " 金币",
                "&7左键点击传送"
        ));

        // slot 28: 修改名字按钮 (NAME_TAG)
        inv.setItem(28, createItem(
                Material.NAME_TAG,
                "&a修改名字",
                "&7当前名字: &f" + npc.getDisplayName(),
                "&7左键点击后在聊天框输入新名字"
        ));

        // slot 31: "已启用功能" 标题 (BOOK)
        inv.setItem(31, createItem(
                Material.BOOK,
                "&b已启用功能",
                "&7共 " + npc.getFeatures().size() + " / " + PhilosNPCPlugin.MAX_FEATURES + " 个功能"
        ));

        // slot 38-41: 4个功能槽位
        int[] featureSlots = {38, 39, 40, 41};
        List<FeatureType> features = npc.getFeatures();
        for (int i = 0; i < 4; i++) {
            if (i < features.size()) {
                FeatureType feature = features.get(i);
                inv.setItem(featureSlots[i], createFeatureItem(feature));
            } else {
                inv.setItem(featureSlots[i], createEmptyFeatureSlot());
            }
        }

        // slot 45: 返回列表按钮 (ARROW)
        inv.setItem(45, createItem(
                Material.ARROW,
                "&a返回列表",
                "&7左键点击返回NPC列表"
        ));

        // slot 49: 关闭按钮 (BARRIER)
        inv.setItem(49, createItem(
                Material.BARRIER,
                "&c关闭界面",
                "&7左键点击关闭"
        ));

        // 填充空白玻璃
        fillEmptySlots(inv, 54);

        return inv;
    }

    // ===== 装备编辑界面 =====

    public static Inventory equipmentGui(PhilosNPC npc, InventoryHolder holder) {
        String typePrefix = npc.isSystem() ? "&d&l" : "&b&l";
        Inventory inv = Bukkit.createInventory(holder, 45,
                PhilosNPCPlugin.cc(typePrefix + "装备编辑 - " + npc.getDisplayName()));

        ItemStack[] equipment = npc.getEquipment();

        // 布局参考村民/铁砧：左侧垂直排列装备槽
        // slot 10: 头盔
        // slot 19: 胸甲
        // slot 28: 护腿
        // slot 37: 靴子
        // slot 24: 主手

        // 头盔槽
        if (equipment[0] != null && equipment[0].getType() != Material.AIR) {
            inv.setItem(10, equipment[0].clone());
        } else {
            inv.setItem(10, GuiManager.markPlaceholder(createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "&7头盔槽",
                    "&7放入头盔或头颅物品"
            )));
        }

        // 胸甲槽
        if (equipment[1] != null && equipment[1].getType() != Material.AIR) {
            inv.setItem(19, equipment[1].clone());
        } else {
            inv.setItem(19, GuiManager.markPlaceholder(createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "&7胸甲槽",
                    "&7放入胸甲物品"
            )));
        }

        // 护腿槽
        if (equipment[2] != null && equipment[2].getType() != Material.AIR) {
            inv.setItem(28, equipment[2].clone());
        } else {
            inv.setItem(28, GuiManager.markPlaceholder(createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "&7护腿槽",
                    "&7放入护腿物品"
            )));
        }

        // 靴子槽
        if (equipment[3] != null && equipment[3].getType() != Material.AIR) {
            inv.setItem(37, equipment[3].clone());
        } else {
            inv.setItem(37, GuiManager.markPlaceholder(createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "&7靴子槽",
                    "&7放入靴子物品"
            )));
        }

        // 主手槽（右侧）
        if (equipment[4] != null && equipment[4].getType() != Material.AIR) {
            inv.setItem(24, equipment[4].clone());
        } else {
            inv.setItem(24, GuiManager.markPlaceholder(createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "&7主手槽",
                    "&7放入手持物品"
            )));
        }

        // 说明牌
        inv.setItem(16, createItem(
                Material.BOOK,
                "&b使用说明",
                "&7将物品拖入对应槽位",
                "&7点击保存后实时更新NPC外观",
                "&7取出槽位物品可清空装备"
        ));

        // slot 40: 保存按钮
        inv.setItem(40, createItem(
                Material.EMERALD_BLOCK,
                "&a&l保存并关闭",
                "&7保存装备并更新NPC外观",
                "&e左键点击保存"
        ));

        // slot 36: 返回按钮
        inv.setItem(36, createItem(
                Material.ARROW,
                "&a返回",
                "&7左键点击返回主界面"
        ));

        // 填充空白玻璃
        fillEmptySlots(inv, 45);

        return inv;
    }

    // ===== 功能选择界面 =====

    public static Inventory featureSelectGui(PhilosNPC npc, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 27, PhilosNPCPlugin.cc("&b&l选择功能 - " + npc.getDisplayName()));

        FeatureType[] features = FeatureType.values();
        int[] slots = {10, 12, 14, 16};

        for (int i = 0; i < features.length && i < slots.length; i++) {
            FeatureType feature = features[i];
            if (npc.hasFeature(feature)) {
                // 已启用 - 灰色状态
                inv.setItem(slots[i], createDisabledFeatureItem(feature));
            } else {
                inv.setItem(slots[i], createFeatureItem(feature));
            }
        }

        // slot 18: 返回按钮
        inv.setItem(18, createItem(
                Material.ARROW,
                "&a返回",
                "&7左键点击返回主界面"
        ));

        fillEmptySlots(inv, 27);

        return inv;
    }

    // ===== 姿势选择界面 =====

    public static Inventory poseSelectGui(PhilosNPC npc, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 27, PhilosNPCPlugin.cc("&b&l选择姿势 - " + npc.getDisplayName()));

        NPCPose[] poses = NPCPose.values();
        // 13种姿势：第2行 slot 10-16（7个），第3行 slot 19-25（6个）
        for (int i = 0; i < poses.length; i++) {
            NPCPose pose = poses[i];
            Material material = poseMaterial(pose);
            boolean isCurrent = npc.getPose() == pose;

            int slot;
            if (i < 7) {
                slot = 10 + i;
            } else {
                slot = 19 + (i - 7);
            }

            if (isCurrent) {
                inv.setItem(slot, createItem(
                        material,
                        "&a&l" + pose.displayName() + " (当前)",
                        "&7" + pose.emoji(),
                        "&a左键点击选择此姿势"
                ));
            } else {
                inv.setItem(slot, createItem(
                        material,
                        "&f" + pose.displayName(),
                        "&7" + pose.emoji(),
                        "&7左键点击选择此姿势"
                ));
            }
        }

        // slot 22: 返回按钮
        inv.setItem(22, createItem(
                Material.ARROW,
                "&a返回",
                "&7左键点击返回主界面"
        ));

        fillEmptySlots(inv, 27);

        return inv;
    }

    // ===== 大小调整界面 =====

    public static Inventory sizeAdjustGui(PhilosNPC npc, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 27, PhilosNPCPlugin.cc("&b&l调整大小 - " + npc.getDisplayName()));

        double scale = npc.getScale();

        // slot 11: 缩小按钮
        boolean canShrink = scale > 0.2;
        inv.setItem(11, createItem(
                canShrink ? Material.REDSTONE_TORCH : Material.LEVER,
                canShrink ? "&c缩小 (-0.1)" : "&7已达最小",
                "&7最小: 0.2",
                canShrink ? "&7左键点击缩小" : "&c无法继续缩小"
        ));

        // slot 13: 当前大小显示
        inv.setItem(13, createItem(
                Material.SLIME_BALL,
                "&b当前大小",
                "&f" + String.format("%.1f", scale),
                "&7范围: 0.2 - 5.0"
        ));

        // slot 15: 放大按钮
        boolean canGrow = scale < 5.0;
        inv.setItem(15, createItem(
                canGrow ? Material.GLOWSTONE_DUST : Material.LEVER,
                canGrow ? "&a放大 (+0.1)" : "&7已达最大",
                "&7最大: 5.0",
                canGrow ? "&7左键点击放大" : "&c无法继续放大"
        ));

        // slot 18: 返回按钮
        inv.setItem(18, createItem(
                Material.ARROW,
                "&a返回",
                "&7左键点击返回主界面"
        ));

        fillEmptySlots(inv, 27);

        return inv;
    }

    // ===== NPC列表界面 =====

    public static Inventory npcListGui(List<PhilosNPC> npcs, int page, int totalPages, boolean isSystemList, InventoryHolder holder) {
        String title = isSystemList
                ? "&d&l系统NPC列表 - 第" + (page + 1) + "/" + totalPages + "页"
                : "&b&l我的NPC - 第" + (page + 1) + "/" + totalPages + "页";

        Inventory inv = Bukkit.createInventory(holder, 54, PhilosNPCPlugin.cc(title));

        int perPage = 28;
        int start = page * perPage;
        int end = Math.min(start + perPage, npcs.size());

        // NPC槽位：第1-4行，左右各留1列边距，共4行x7列=28个
        int slot = 10; // 从第2行第2列开始
        int count = 0;

        for (int i = start; i < end; i++) {
            PhilosNPC npc = npcs.get(i);
            if (count >= 28) break;

            // 跳过边缘列
            while (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
            }
            // 超过第5行（slot 44+）也停止
            if (slot > 43) break;

            ItemStack head = createNPCListHead(npc);
            inv.setItem(slot, head);
            slot++;
            count++;
        }

        // 上一页
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "&a上一页", "&7第 " + page + " 页"));
        } else {
            inv.setItem(45, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // 总数/页码信息
        inv.setItem(49, createItem(
                Material.BOOK,
                "&b总数: &f" + npcs.size() + " &7个",
                "&7第 &f" + (page + 1) + "&7 / &f" + totalPages + " &7页"
        ));

        // 下一页
        if (page < totalPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "&a下一页", "&7第 " + (page + 2) + " 页"));
        } else {
            inv.setItem(53, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // 关闭按钮
        inv.setItem(50, createItem(
                Material.BARRIER,
                "&c关闭",
                "&7左键点击关闭"
        ));

        // 填充空白
        fillEmptySlots(inv, 54);

        return inv;
    }

    // ===== 辅助方法 =====

    private static ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PhilosNPCPlugin.cc(name));
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(PhilosNPCPlugin.cc(line));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNPCHead(PhilosNPC npc) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            String color = npc.isSystem() ? "&d&l" : "&b&l";
            meta.setDisplayName(PhilosNPCPlugin.cc(color + npc.getDisplayName()));
            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7类型: &f" + npc.getNpcType().displayName()));
            lore.add(PhilosNPCPlugin.cc("&7ID: &f" + displayId(npc.getId())));
            lore.add(PhilosNPCPlugin.cc("&7主人: &f" + npc.getOwnerName()));
            if (npc.isSystem() && npc.getEntityTypeName() != null) {
                lore.add(PhilosNPCPlugin.cc("&7实体类型: &f" + npc.getEntityTypeName()));
            }
            lore.add(PhilosNPCPlugin.cc("&7姿势: &f" + npc.getPose().displayName()));
            lore.add(PhilosNPCPlugin.cc("&7大小: &f" + String.format("%.1f", npc.getScale())));
            meta.setLore(lore);
            // 设置头颅主人
            if (npc.isSystem() && npc.getEntityTypeName() != null && npc.getEntityTypeName().startsWith("PLAYER:")) {
                String playerName = npc.getEntityTypeName().substring(7);
                meta.setOwner(playerName);
            } else if (npc.getOwnerName() != null) {
                meta.setOwner(npc.getOwnerName());
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack createNPCListHead(PhilosNPC npc) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            String color = npc.isSystem() ? "&d" : "&b";
            meta.setDisplayName(PhilosNPCPlugin.cc(color + npc.getDisplayName()));
            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7类型: &f" + npc.getNpcType().displayName()));
            lore.add(PhilosNPCPlugin.cc("&7ID: &f" + displayId(npc.getId())));
            lore.add(PhilosNPCPlugin.cc("&7姿势: &f" + npc.getPose().displayName()));
            lore.add(PhilosNPCPlugin.cc("&7功能: &f" + npc.getFeatures().size() + "/4"));

            if (npc.getLocation() != null && npc.getLocation().getWorld() != null) {
                lore.add(PhilosNPCPlugin.cc("&7位置: &f" + npc.getLocation().getWorld().getName()
                        + " " + npc.getLocation().getBlockX()
                        + "," + npc.getLocation().getBlockY()
                        + "," + npc.getLocation().getBlockZ()));
            }

            lore.add(PhilosNPCPlugin.cc("&e左键点击编辑"));
            meta.setLore(lore);

            // 设置头颅皮肤
            if (npc.isSystem() && npc.getEntityTypeName() != null && npc.getEntityTypeName().startsWith("PLAYER:")) {
                meta.setOwner(npc.getEntityTypeName().substring(7));
            } else if (npc.getOwnerName() != null) {
                meta.setOwner(npc.getOwnerName());
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    private static ItemStack createFeatureItem(FeatureType feature) {
        Material material = parseMaterial(feature.icon(), Material.STONE);
        return createItem(
                material,
                "&a" + feature.displayName(),
                "&7" + feature.description(),
                "&7左键点击管理此功能"
        );
    }

    private static ItemStack createDisabledFeatureItem(FeatureType feature) {
        Material material = parseMaterial(feature.icon(), Material.STONE);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PhilosNPCPlugin.cc("&7" + feature.displayName() + " (已启用)"));
            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7" + feature.description()));
            lore.add(PhilosNPCPlugin.cc("&a已启用"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createEmptyFeatureSlot() {
        return createItem(
                Material.LIME_STAINED_GLASS_PANE,
                "&a点击添加功能",
                "&7左键点击选择要添加的功能"
        );
    }

    private static Material poseMaterial(NPCPose pose) {
        switch (pose) {
            case STANDING:
                return Material.ARMOR_STAND;
            case SNEAKING:
                return Material.LEATHER_LEGGINGS;
            case SITTING:
                return Material.OAK_STAIRS;
            case LYING:
                return Material.WHITE_BED;
            case DANCING:
                return Material.JUKEBOX;
            case WAVE:
                return Material.FEATHER;
            case ARMS_CROSSED:
                return Material.SHIELD;
            case THUMBS_UP:
                return Material.EMERALD;
            case BOWING:
                return Material.BOW;
            case SUPERMAN:
                return Material.ELYTRA;
            case POINTING:
                return Material.SPYGLASS;
            case MEDITATION:
                return Material.AMETHYST_SHARD;
            case FACEPALM:
                return Material.PAPER;
            default:
                return Material.STONE;
        }
    }

    /**
     * ID显示：短ID（如 Notch_1）完整显示，旧UUID截断前8位
     */
    private static String displayId(String id) {
        return id.length() > 16 ? id.substring(0, 8) + "..." : id;
    }

    private static Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static void fillEmptySlots(Inventory inv, int size) {
        ItemStack glass = createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "&r "
        );
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }
    }
}
