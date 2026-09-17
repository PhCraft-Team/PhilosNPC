package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.gui.GuiManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShopGui {

    // ===== 商店背包编辑界面（45格 = 5行，个人NPC） =====

    public static Inventory shopInventoryGui(PhilosNPC npc, InventoryHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 45, PhilosNPCPlugin.cc("&b&l商店背包（共享）"));

        // 前36格：商店背包物品（从共享背包中读取）
        ItemStack[] shopInv = PhilosNPCPlugin.instance().npcManager().getSharedShopInventory(npc.getOwnerUuid());
        for (int i = 0; i < 36 && i < shopInv.length; i++) {
            if (shopInv[i] != null) {
                inv.setItem(i, shopInv[i].clone());
            }
        }

        // 第4行：分隔线 (GRAY_STAINED_GLASS_PANE)
        for (int i = 36; i < 45; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // slot 36: 返回按钮 (ARROW)
        inv.setItem(36, createItem(
                Material.ARROW,
                "&a返回",
                "&7左键点击返回主界面"
        ));

        // slot 38: 交易配方按钮 (EMERALD)
        inv.setItem(38, createItem(
                Material.EMERALD,
                "&a交易配方",
                "&7设置该NPC出售的商品和价格",
                "&7出售的物品将从本背包扣除",
                "&e左键点击编辑交易配方"
        ));

        // slot 40: "保存并关闭" (EMERALD_BLOCK)
        inv.setItem(40, createItem(
                Material.EMERALD_BLOCK,
                "&a&l保存并关闭",
                "&7背包内容关闭时自动保存",
                "&e这是你所有NPC共享的商店背包"
        ));

        return inv;
    }

    // ===== 交易配方编辑界面（54格，统一金币交易） =====

    public static Inventory tradeEditGui(PhilosNPC npc, int page, InventoryHolder holder) {
        String title = npc.isSystem()
                ? "&d&l系统NPC交易编辑 - 第" + (page + 1) + "页"
                : "&b&l交易配方编辑 - 第" + (page + 1) + "页";
        Inventory inv = Bukkit.createInventory(holder, 54, PhilosNPCPlugin.cc(title));

        List<ShopTrade> trades = npc.getTrades();
        int tradesPerPage = 9;
        int startIndex = page * tradesPerPage;

        for (int i = 0; i < tradesPerPage; i++) {
            int tradeIndex = startIndex + i;
            if (tradeIndex < trades.size()) {
                ShopTrade trade = trades.get(tradeIndex);
                inv.setItem(i, createTradeDisplayItem(trade, tradeIndex));
            }
        }

        // 第4行：产出物品槽（29）+ 添加按钮（30）+ 说明（31），价格统一为金币聊天输入
        inv.setItem(27, GuiManager.markPlaceholder(createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "&7金币交易",
                "&7价格无需放入物品",
                "&7添加后在聊天框输入"
        )));
        inv.setItem(28, GuiManager.markPlaceholder(createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "&7金币交易",
                "&7价格在聊天框输入"
        )));
        inv.setItem(29, GuiManager.markPlaceholder(createItem(
                Material.PAPER,
                "&e产出物品槽位",
                "&7将产出物品放入此槽位",
                "&7点击添加后在聊天框输入价格"
        )));

        inv.setItem(30, createItem(
                Material.EMERALD,
                "&a&l点击添加交易",
                "&7读取左侧产出物品",
                "&7然后在聊天框输入金币价格"
        ));

        if (npc.isSystem()) {
            inv.setItem(31, createItem(
                    Material.BOOK,
                    "&b说明",
                    "&7系统NPC交易统一使用金币结算",
                    "&7添加交易：放入产出物品，",
                    "&7点击添加，聊天框输入价格",
                    "&7产出物品由系统直接生成"
            ));
        } else {
            inv.setItem(31, createItem(
                    Material.BOOK,
                    "&b库存说明",
                    "&7交易统一使用金币结算",
                    "&7出售的产出物品将从你的",
                    "&7共享商店背包中扣除",
                    "&7请在商店背包界面补充库存"
            ));
        }

        for (int i = 32; i < 36; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        boolean hasPrev = page > 0;
        inv.setItem(36, createItem(
                hasPrev ? Material.ARROW : Material.LEVER,
                hasPrev ? "&a上一页" : "&7已是第一页",
                hasPrev ? "&7左键点击上一页" : "&c没有上一页了"
        ));

        int totalPages = Math.max(1, (trades.size() + tradesPerPage - 1) / tradesPerPage);
        boolean hasNext = page < totalPages - 1;
        inv.setItem(40, createItem(
                hasNext ? Material.ARROW : Material.LEVER,
                hasNext ? "&a下一页" : "&7已是最后一页",
                hasNext ? "&7左键点击下一页" : "&c没有下一页了"
        ));

        inv.setItem(44, createItem(
                Material.BARRIER,
                "&c返回",
                npc.isSystem() ? "&7左键点击返回主界面" : "&7左键点击返回商店背包"
        ));

        for (int i = 37; i < 40; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }
        for (int i = 41; i < 44; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

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
     * 创建交易配方展示物品（编辑界面用）
     */
    private static ItemStack createTradeDisplayItem(ShopTrade trade, int index) {
        ItemStack result = trade.getResult().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            String originalName = meta.hasDisplayName() ? meta.getDisplayName() : result.getType().name();
            meta.setDisplayName(PhilosNPCPlugin.cc("&a交易 #" + (index + 1) + " - " + originalName));

            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7&m-------------------"));
            if (trade.isUseCurrency()) {
                lore.add(PhilosNPCPlugin.cc("&e价格: &6" + trade.getCurrencyPrice() + " 金币"));
            } else {
                lore.add(PhilosNPCPlugin.cc("&e价格1: &f" + formatItem(trade.getPrice1())));
                if (trade.getPrice2() != null) {
                    lore.add(PhilosNPCPlugin.cc("&e价格2: &f" + formatItem(trade.getPrice2())));
                }
            }
            lore.add(PhilosNPCPlugin.cc("&e产出: &f" + formatItem(trade.getResult())));
            lore.add(PhilosNPCPlugin.cc("&7&m-------------------"));
            if (trade.isInfinite()) {
                lore.add(PhilosNPCPlugin.cc("&b次数: &f无限"));
            } else {
                lore.add(PhilosNPCPlugin.cc("&b次数: &f" + trade.getUses() + " / " + trade.getMaxUses()));
            }
            lore.add(PhilosNPCPlugin.cc("&c左键点击删除此交易"));
            meta.setLore(lore);
            result.setItemMeta(meta);
        }
        return result;
    }

    /**
     * 格式化物品显示文本
     */
    private static String formatItem(ItemStack item) {
        if (item == null) return "无";
        String name = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name();
        return item.getAmount() + "x " + name;
    }
}
