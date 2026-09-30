package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.gui.GuiManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 礼包发放功能的GUI界面工厂：
 * 管理员礼包列表、单个礼包编辑、容器类型与颜色选择、玩家领取界面
 */
public class GiftPackGui {

    private static final int PER_PAGE = 28; // 4行x7列

    // ===== 管理员礼包列表（54格） =====

    public static Inventory listGui(PhilosNPC npc, int page, InventoryHolder holder) {
        List<GiftPack> packs = npc.getGiftPacks();
        int totalPages = Math.max(1, (packs.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(holder, 54,
                PhilosNPCPlugin.cc("&d&l礼包管理 - 第" + (page + 1) + "/" + totalPages + "页"));

        if (packs.isEmpty()) {
            inv.setItem(22, createItem(
                    Material.BOOK,
                    "&b暂无礼包",
                    "&7点击下方绿色按钮创建礼包"
            ));
        } else {
            int start = page * PER_PAGE;
            int slot = 10;
            int count = 0;
            for (int i = start; i < packs.size() && count < PER_PAGE; i++) {
                GiftPack pack = packs.get(i);
                while (slot % 9 == 0 || slot % 9 == 8) slot++;
                if (slot > 43) break;

                ItemStack item = GiftPackFeature.buildContainerItem(pack);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(PhilosNPCPlugin.cc("&7已领取: &f" + pack.getClaimedCount() + " 人"));
                    lore.add(PhilosNPCPlugin.cc("&e左键点击编辑"));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
                slot++;
                count++;
            }
        }

        // slot 45: 上一页
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "&a上一页", "&7第 " + page + " 页"));
        } else {
            inv.setItem(45, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // slot 47: 移除功能
        inv.setItem(47, createItem(
                Material.BARRIER,
                "&c移除礼包发放功能",
                "&7礼包数据会保留",
                "&7重新添加功能后可恢复",
                "&e左键点击移除"
        ));

        // slot 49: 新建礼包
        inv.setItem(49, createItem(
                Material.LIME_DYE,
                "&a新建礼包",
                "&7创建一个新的空礼包",
                "&7创建后可编辑名字/内容物/容器",
                "&e左键点击创建"
        ));

        // slot 50: 返回主界面
        inv.setItem(50, createItem(
                Material.ARROW,
                "&a返回主界面",
                "&7左键点击返回"
        ));

        // slot 53: 下一页
        if (page < totalPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "&a下一页", "&7第 " + (page + 2) + " 页"));
        } else {
            inv.setItem(53, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        fillEmptySlots(inv, 54);
        return inv;
    }

    // ===== 单个礼包编辑（54格） =====

    /**
     * 编辑界面布局：
     * - slot 2: 内容物统计
     * - slot 4: 礼包名字（聊天输入）
     * - slot 6: 容器类型与颜色（打开选择界面）
     * - slot 18-44: 内容物编辑区（27格，关闭自动保存）
     * - slot 45: 返回 / slot 49: 删除礼包 / slot 53: 保存提示
     */
    public static Inventory editGui(PhilosNPC npc, GiftPack pack, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 54,
                PhilosNPCPlugin.cc("&d&l编辑礼包 - " + pack.getName()));

        inv.setItem(2, createItem(
                Material.BOOK,
                "&b内容物",
                "&7共 &f" + pack.getContents().size() + " &7组物品",
                "&7将物品放入下方槽位",
                "&7关闭界面自动保存",
                "&7收纳袋容量有限(64单位)"
        ));

        inv.setItem(4, createItem(
                Material.NAME_TAG,
                "&a礼包名字",
                "&7当前: &f" + pack.getName(),
                "&e左键点击修改名字"
        ));

        inv.setItem(6, createItem(
                pack.containerMaterial(),
                "&a容器类型与颜色",
                "&7当前: &f" + colorDisplayName(pack.getColor()) + (pack.isShulker() ? "潜影盒" : "收纳袋"),
                "&e左键点击更换"
        ));

        // 内容物区 18-44：先全部放置占位符，再覆盖已存内容物
        for (int i = 18; i <= 44; i++) {
            inv.setItem(i, GuiManager.markPlaceholder(createItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    "&7内容物槽位",
                    "&7放入物品",
                    "&7关闭界面自动保存"
            )));
        }
        int slot = 18;
        for (ItemStack content : pack.getContents()) {
            inv.setItem(slot++, content.clone());
        }

        inv.setItem(45, createItem(
                Material.ARROW,
                "&a返回礼包列表",
                "&7内容物已自动保存"
        ));

        inv.setItem(49, createItem(
                Material.BARRIER,
                "&c删除此礼包",
                "&c警告：删除后无法恢复",
                "&7内容物将退还给你",
                "&e左键点击删除"
        ));

        inv.setItem(53, createItem(
                Material.EMERALD_BLOCK,
                "&a&l保存",
                "&7关闭或返回时自动保存"
        ));

        fillEmptySlots(inv, 54);
        return inv;
    }

    // ===== 容器类型与颜色选择（45格） =====

    /**
     * 布局：
     * - 行1 (slot 0-8): 收纳袋 前9色
     * - 行2 (slot 9-15): 收纳袋 后7色
     * - 行3 (slot 18-26): 潜影盒 前9色
     * - 行4 (slot 27-33): 潜影盒 后7色
     * - slot 40: 返回
     */
    public static Inventory containerGui(PhilosNPC npc, GiftPack pack, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 45,
                PhilosNPCPlugin.cc("&d&l选择容器 - " + pack.getName()));

        DyeColor[] colors = DyeColor.values();
        for (int i = 0; i < colors.length && i < 16; i++) {
            DyeColor color = colors[i];
            boolean curBundle = !pack.isShulker() && pack.getColor().equals(color.name());
            boolean curShulker = pack.isShulker() && pack.getColor().equals(color.name());

            inv.setItem(i, createItem(
                    containerMaterial(color, false),
                    (curBundle ? "&a&l✓ " : "&f") + colorDisplayName(color.name()) + "收纳袋",
                    curBundle ? "&a当前使用" : "&e左键点击选择"
            ));

            inv.setItem(18 + i, createItem(
                    containerMaterial(color, true),
                    (curShulker ? "&a&l✓ " : "&f") + colorDisplayName(color.name()) + "潜影盒",
                    curShulker ? "&a当前使用" : "&e左键点击选择"
            ));
        }

        inv.setItem(40, createItem(
                Material.ARROW,
                "&a返回",
                "&7左键点击返回编辑界面"
        ));

        fillEmptySlots(inv, 45);
        return inv;
    }

    // ===== 玩家领取界面（54格） =====

    /**
     * 玩家只显示未领取的礼包；管理员显示全部（已领的标注可重复领取）
     */
    public static Inventory claimGui(PhilosNPC npc, Player player, int page, InventoryHolder holder) {
        boolean admin = player.hasPermission("philosnpc.admin");
        List<GiftPack> visible = new ArrayList<>();
        for (GiftPack pack : npc.getGiftPacks()) {
            if (admin || !pack.isClaimedBy(player.getUniqueId())) {
                visible.add(pack);
            }
        }

        int totalPages = Math.max(1, (visible.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(holder, 54,
                PhilosNPCPlugin.cc("&b&l礼包中心 - 第" + (page + 1) + "/" + totalPages + "页"));

        if (visible.isEmpty()) {
            inv.setItem(22, createItem(
                    Material.BOOK,
                    "&b暂无可领取的礼包",
                    "&7所有礼包都已领取过了"
            ));
        } else {
            int start = page * PER_PAGE;
            int slot = 10;
            int count = 0;
            for (int i = start; i < visible.size() && count < PER_PAGE; i++) {
                GiftPack pack = visible.get(i);
                while (slot % 9 == 0 || slot % 9 == 8) slot++;
                if (slot > 43) break;

                ItemStack item = GiftPackFeature.buildContainerItem(pack);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    if (admin && pack.isClaimedBy(player.getUniqueId())) {
                        lore.add(PhilosNPCPlugin.cc("&7已领取（管理员可重复领取）"));
                    }
                    lore.add(PhilosNPCPlugin.cc("&e左键点击领取"));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(slot, item);
                slot++;
                count++;
            }
        }

        // slot 45: 上一页
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "&a上一页", "&7第 " + page + " 页"));
        } else {
            inv.setItem(45, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // slot 49: 关闭
        inv.setItem(49, createItem(
                Material.BARRIER,
                "&c关闭",
                "&7左键点击关闭界面"
        ));

        // slot 53: 下一页
        if (page < totalPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "&a下一页", "&7第 " + (page + 2) + " 页"));
        } else {
            inv.setItem(53, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        fillEmptySlots(inv, 54);
        return inv;
    }

    // ===== 辅助方法 =====

    /**
     * 按页码和槽位反查礼包（列表布局：4行x7列）
     */
    public static GiftPack packAtSlot(List<GiftPack> packs, int slot, int page) {
        int row = (slot / 9) - 1;
        int col = slot % 9 - 1;
        if (row < 0 || row > 3 || col < 0 || col > 6) return null;
        int index = page * PER_PAGE + row * 7 + col;
        return index >= 0 && index < packs.size() ? packs.get(index) : null;
    }

    private static Material containerMaterial(DyeColor color, boolean shulker) {
        try {
            return Material.valueOf(color.name() + (shulker ? "_SHULKER_BOX" : "_BUNDLE"));
        } catch (IllegalArgumentException e) {
            return shulker ? Material.SHULKER_BOX : Material.BUNDLE;
        }
    }

    private static String colorDisplayName(String colorName) {
        try {
            return colorDisplayName(DyeColor.valueOf(colorName));
        } catch (IllegalArgumentException e) {
            return colorName;
        }
    }

    private static String colorDisplayName(DyeColor color) {
        switch (color) {
            case WHITE: return "白色";
            case ORANGE: return "橙色";
            case MAGENTA: return "品红色";
            case LIGHT_BLUE: return "淡蓝色";
            case YELLOW: return "黄色";
            case LIME: return "黄绿色";
            case PINK: return "粉红色";
            case GRAY: return "灰色";
            case LIGHT_GRAY: return "淡灰色";
            case CYAN: return "青色";
            case PURPLE: return "紫色";
            case BLUE: return "蓝色";
            case BROWN: return "棕色";
            case GREEN: return "绿色";
            case RED: return "红色";
            case BLACK: return "黑色";
            default: return color.name();
        }
    }

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
