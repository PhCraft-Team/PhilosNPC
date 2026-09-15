package com.phcraft.philosnpc.gui;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.FeatureType;
import com.phcraft.philosnpc.npc.NPCPose;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class NPCGui {

    // ===== 主界面 =====

    public static Inventory mainGui(PhilosNPC npc) {
        Inventory inv = Bukkit.createInventory(null, 54, PhilosNPCPlugin.cc("&b&lNPC管理 - " + npc.getDisplayName()));

        // slot 13: NPC头颅 + 名称 + pose/size信息
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

        // slot 24: 传送至NPC (ENDER_PEARL) - 花费10元
        inv.setItem(24, createItem(
                Material.ENDER_PEARL,
                "&a传送至NPC",
                "&7花费: &6" + PhilosNPCPlugin.TP_TO_NPC_COST + " 金币",
                "&7左键点击传送"
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

    // ===== 功能选择界面 =====

    public static Inventory featureSelectGui(PhilosNPC npc) {
        Inventory inv = Bukkit.createInventory(null, 27, PhilosNPCPlugin.cc("&b&l选择功能 - " + npc.getDisplayName()));

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

    public static Inventory poseSelectGui(PhilosNPC npc) {
        Inventory inv = Bukkit.createInventory(null, 27, PhilosNPCPlugin.cc("&b&l选择姿势 - " + npc.getDisplayName()));

        NPCPose[] poses = NPCPose.values();
        int startSlot = 9; // slot 9-13

        for (int i = 0; i < poses.length; i++) {
            NPCPose pose = poses[i];
            Material material = poseMaterial(pose);
            boolean isCurrent = npc.getPose() == pose;

            if (isCurrent) {
                // 当前使用的高亮
                inv.setItem(startSlot + i, createItem(
                        material,
                        "&a&l" + pose.displayName() + " (当前)",
                        "&7" + pose.emoji(),
                        "&a左键点击选择此姿势"
                ));
            } else {
                inv.setItem(startSlot + i, createItem(
                        material,
                        "&f" + pose.displayName(),
                        "&7" + pose.emoji(),
                        "&7左键点击选择此姿势"
                ));
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

    // ===== 大小调整界面 =====

    public static Inventory sizeAdjustGui(PhilosNPC npc) {
        Inventory inv = Bukkit.createInventory(null, 27, PhilosNPCPlugin.cc("&b&l调整大小 - " + npc.getDisplayName()));

        double scale = npc.getScale();

        // slot 11: 缩小按钮
        boolean canShrink = scale > 0.5;
        inv.setItem(11, createItem(
                canShrink ? Material.REDSTONE_TORCH : Material.LEVER,
                canShrink ? "&c缩小 (-0.1)" : "&7已达最小",
                "&7最小: 0.5",
                canShrink ? "&7左键点击缩小" : "&c无法继续缩小"
        ));

        // slot 13: 当前大小显示
        inv.setItem(13, createItem(
                Material.SLIME_BALL,
                "&b当前大小",
                "&f" + String.format("%.1f", scale),
                "&7范围: 0.5 - 2.0"
        ));

        // slot 15: 放大按钮
        boolean canGrow = scale < 2.0;
        inv.setItem(15, createItem(
                canGrow ? Material.GLOWSTONE_DUST : Material.LEVER,
                canGrow ? "&a放大 (+0.1)" : "&7已达最大",
                "&7最大: 2.0",
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
            meta.setDisplayName(PhilosNPCPlugin.cc("&b&l" + npc.getDisplayName()));
            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7NPC ID: &f" + npc.getId().substring(0, 8) + "..."));
            lore.add(PhilosNPCPlugin.cc("&7主人: &f" + npc.getOwnerName()));
            lore.add(PhilosNPCPlugin.cc("&7姿势: &f" + npc.getPose().displayName()));
            lore.add(PhilosNPCPlugin.cc("&7大小: &f" + String.format("%.1f", npc.getScale())));
            meta.setLore(lore);
            // 尝试设置头颅主人
            if (npc.getOwnerName() != null) {
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
            default:
                return Material.STONE;
        }
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
