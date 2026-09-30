package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.gui.GuiManager;
import com.phcraft.philosnpc.npc.FeatureType;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 各功能的设置/编辑 GUI 界面工厂
 * 提供传送设置、留言编辑、顾客功能选择等界面
 */
public class FeatureGuiFactory {

    // ===== 传送设置界面（36格） =====

    /**
     * 传送设置界面
     * @param npc NPC对象
     * @return 传送设置Inventory
     */
    public static Inventory teleportSettingsGui(PhilosNPC npc, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 36,
                PhilosNPCPlugin.cc("&b&l传送设置 - " + npc.getDisplayName()));

        // slot 13: 当前目标点信息 (COMPASS)
        Location target = npc.getTeleportTarget();
        if (target != null && target.getWorld() != null) {
            inv.setItem(13, createItem(
                    Material.COMPASS,
                    "&a当前传送目标点",
                    "&7世界: &f" + target.getWorld().getName(),
                    "&7X: &f" + String.format("%.2f", target.getX()),
                    "&7Y: &f" + String.format("%.2f", target.getY()),
                    "&7Z: &f" + String.format("%.2f", target.getZ()),
                    "&7Yaw: &f" + String.format("%.1f", target.getYaw()),
                    "&7Pitch: &f" + String.format("%.1f", target.getPitch())
            ));
        } else {
            inv.setItem(13, createItem(
                    Material.COMPASS,
                    "&c当前传送目标点",
                    "&7未设置",
                    "&c请先设置传送目标点"
            ));
        }

        // slot 19: "设为当前位置"按钮 (COMPASS)
        inv.setItem(19, createItem(
                Material.COMPASS,
                "&a设为当前位置",
                "&7将传送目标点设置为",
                "&7你当前所在的位置",
                "&e左键点击设置"
        ));

        // slot 22: 价格显示
        if (npc.isSystem()) {
            // 系统NPC：管理员可自定义价格
            double cost = npc.getEffectiveTeleportCost();
            inv.setItem(22, createItem(
                    Material.GOLD_INGOT,
                    npc.getCustomTeleportCost() >= 0 ? "&6传送价格" : "&7传送价格",
                    "&f" + cost + " 金币",
                    npc.getCustomTeleportCost() >= 0 ? "&7已自定义价格" : "&7默认价格",
                    "&e左键点击修改价格"
            ));
        } else {
            inv.setItem(22, createItem(
                    Material.GOLD_INGOT,
                    "&7传送价格",
                    "&f" + TeleportFeature.getTeleportCost() + " 金币",
                    "&7固定价格，不可修改"
            ));
        }

        // slot 27: 返回按钮
        inv.setItem(27, createItem(
                Material.ARROW,
                "&a返回",
                "&7左键点击返回主界面"
        ));

        // slot 31: "移除功能"按钮 (BARRIER，红色)
        inv.setItem(31, createItem(
                Material.BARRIER,
                "&c移除传送功能",
                "&c警告：移除后需要重新添加",
                "&7左键点击移除此功能"
        ));

        // 填充空白玻璃
        fillEmptySlots(inv, 36);

        return inv;
    }

    // ===== 留言编辑界面（27格） =====

    /**
     * 留言编辑界面
     * @param npc NPC对象
     * @return 留言编辑Inventory
     */
    public static Inventory messageEditGui(PhilosNPC npc, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 27,
                PhilosNPCPlugin.cc("&b&l留言编辑 - " + npc.getDisplayName()));

        String message = npc.getMessage();

        // slot 13: 留言显示 (PAPER)，Lore显示当前留言（多行）
        if (message != null && !message.isEmpty()) {
            String[] lines = message.split("\\\\n|\\n");
            String[] loreLines = new String[lines.length + 1];
            loreLines[0] = "&7当前留言:";
            for (int i = 0; i < lines.length; i++) {
                loreLines[i + 1] = "&f" + lines[i];
            }
            inv.setItem(13, createItem(
                    Material.PAPER,
                    "&a留言内容",
                    loreLines
            ));
        } else {
            inv.setItem(13, createItem(
                    Material.PAPER,
                    "&a留言内容",
                    "&7当前留言: &c无",
                    "&7点击下方按钮设置留言"
            ));
        }

        // slot 11: "修改留言"按钮 (WRITABLE_BOOK)
        inv.setItem(11, createItem(
                Material.WRITABLE_BOOK,
                "&a修改留言",
                "&7点击后在聊天框输入留言",
                "&7支持 &/n &7换行",
                "&e左键点击开始修改"
        ));

        // slot 15: "清除留言"按钮 (BARRIER)
        inv.setItem(15, createItem(
                Material.BARRIER,
                "&c清除留言",
                "&7清除当前留言内容",
                "&e左键点击清除"
        ));

        // slot 18: 返回按钮
        inv.setItem(18, createItem(
                Material.ARROW,
                "&a返回",
                "&7左键点击返回主界面"
        ));

        // slot 22: "移除功能"按钮
        inv.setItem(22, createItem(
                Material.BARRIER,
                "&c移除留言功能",
                "&c警告：移除后留言内容将会丢失",
                "&7左键点击移除此功能"
        ));

        // 填充空白玻璃
        fillEmptySlots(inv, 27);

        return inv;
    }

    // ===== 顾客功能选择界面（27格） =====

    /**
     * 顾客右键NPC时的功能选择界面
     * 根据 npc.getFeatures() 动态显示已启用的功能按钮
     * @param npc NPC对象
     * @param customer 顾客玩家
     * @return 功能选择Inventory
     */
    public static Inventory customerFeatureGui(PhilosNPC npc, Player customer, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 27,
                PhilosNPCPlugin.cc("&b&l" + npc.getDisplayName()));

        List<FeatureType> features = npc.getFeatures();
        // 槽位必须与 GuiManager.handleCustomerClick 的 centeredRowSlots 完全一致
        int[] featureSlots = com.phcraft.philosnpc.gui.NPCGui.centeredRowSlots(features.size());

        for (int i = 0; i < features.size() && i < featureSlots.length; i++) {
            FeatureType feature = features.get(i);
            inv.setItem(featureSlots[i], createCustomerFeatureItem(feature, npc));
        }

        // slot 22: NPC名称显示 (玩家头颅)
        inv.setItem(22, createNPCHead(npc));

        // slot 18: 关闭按钮
        inv.setItem(18, createItem(
                Material.BARRIER,
                "&c关闭",
                "&7左键点击关闭界面"
        ));

        // 填充空白玻璃
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

    /**
     * 创建顾客视角的功能按钮物品
     */
    private static ItemStack createCustomerFeatureItem(FeatureType feature, PhilosNPC npc) {
        Material material = parseMaterial(feature.icon(), Material.STONE);
        String name = "&a" + feature.displayName();
        String desc = "&7" + feature.description();
        String clickHint = "&e左键点击使用";

        // 传送功能额外显示价格
        if (feature == FeatureType.TELEPORT) {
            return createItem(
                    material,
                    name,
                    desc,
                    "&7价格: &6" + npc.getEffectiveTeleportCost() + " 金币",
                    clickHint
            );
        }

        return createItem(material, name, desc, clickHint);
    }

    /**
     * 创建NPC头颅物品
     */
    private static ItemStack createNPCHead(PhilosNPC npc) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PhilosNPCPlugin.cc("&b&l" + npc.getDisplayName()));
            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7主人: &f" + npc.getOwnerName()));
            lore.add(PhilosNPCPlugin.cc("&7已启用功能: &f" + npc.getFeatures().size() + " 个"));
            meta.setLore(lore);
            if (npc.getOwnerName() != null) {
                meta.setOwner(npc.getOwnerName());
            }
            head.setItemMeta(meta);
        }
        return head;
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
