package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.PhilosNPC;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ShopGui {

    // ===== 商店背包编辑界面（45格 = 5行） =====

    public static Inventory shopInventoryGui(PhilosNPC npc) {
        Inventory inv = Bukkit.createInventory(null, 45, PhilosNPCPlugin.cc("&b&l商店背包（共享）"));

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
                "&7左键点击返回商店编辑界面"
        ));

        // slot 40: "保存并关闭" (EMERALD_BLOCK)
        inv.setItem(40, createItem(
                Material.EMERALD_BLOCK,
                "&a&l保存并关闭",
                "&7点击保存背包内容并关闭",
                "&e这是你所有NPC共享的商店背包"
        ));

        return inv;
    }

    // ===== 交易配方编辑界面（54格） =====

    public static Inventory tradeEditGui(PhilosNPC npc, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, PhilosNPCPlugin.cc("&b&l交易配方编辑 - 第" + (page + 1) + "页"));

        List<ShopTrade> trades = npc.getTrades();
        int tradesPerPage = 9;
        int startIndex = page * tradesPerPage;

        // 前3行（slot 0-26）：已有的交易配方展示
        // 简化版：每格显示一个配方的产出物品，Lore显示价格，点击删除
        for (int i = 0; i < tradesPerPage; i++) {
            int tradeIndex = startIndex + i;
            if (tradeIndex < trades.size()) {
                ShopTrade trade = trades.get(tradeIndex);
                inv.setItem(i, createTradeDisplayItem(trade, tradeIndex));
            }
        }

        // 第4行："添加新交易" 区域
        // slot 27: 价格1槽位提示
        inv.setItem(27, createItem(
                Material.PAPER,
                "&e价格物品1",
                "&7将价格物品1放入此槽位",
                "&7（点击下方添加按钮时读取）"
        ));

        // slot 28: 价格2槽位提示
        inv.setItem(28, createItem(
                Material.PAPER,
                "&e价格物品2（可选）",
                "&7将价格物品2放入此槽位",
                "&7不需要则留空"
        ));

        // slot 29: 产出物品槽位提示
        inv.setItem(29, createItem(
                Material.PAPER,
                "&e产出物品",
                "&7将产出物品放入此槽位"
        ));

        // slot 30: 添加按钮
        inv.setItem(30, createItem(
                Material.EMERALD,
                "&a&l点击添加交易",
                "&7读取上方三个槽位的物品",
                "&7创建新的交易配方"
        ));

        // slot 31: 最大交易次数设置
        inv.setItem(31, createItem(
                Material.ANVIL,
                "&6最大交易次数",
                "&7当前: &f-1 (无限)",
                "&7点击修改最大交易次数"
        ));

        // 填充第4行剩余槽位
        for (int i = 32; i < 36; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // 第5行：分页和返回
        // slot 36: 上一页
        boolean hasPrev = page > 0;
        inv.setItem(36, createItem(
                hasPrev ? Material.ARROW : Material.LEVER,
                hasPrev ? "&a上一页" : "&7已是第一页",
                hasPrev ? "&7左键点击上一页" : "&c没有上一页了"
        ));

        // slot 40: 下一页
        int totalPages = Math.max(1, (trades.size() + tradesPerPage - 1) / tradesPerPage);
        boolean hasNext = page < totalPages - 1;
        inv.setItem(40, createItem(
                hasNext ? Material.ARROW : Material.LEVER,
                hasNext ? "&a下一页" : "&7已是最后一页",
                hasNext ? "&7左键点击下一页" : "&c没有下一页了"
        ));

        // slot 44: 返回按钮
        inv.setItem(44, createItem(
                Material.BARRIER,
                "&c返回",
                "&7左键点击返回主界面"
        ));

        // 填充第5行剩余槽位
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

    // ===== 顾客视角的交易界面（27格） =====

    public static Inventory tradeViewGui(PhilosNPC npc, Player customer) {
        Inventory inv = Bukkit.createInventory(null, 27, PhilosNPCPlugin.cc("&b&l" + npc.getDisplayName() + " 的商店"));

        List<ShopTrade> trades = npc.getTrades();

        // slot 0-8: 交易选项列表（9个，显示产出物品，Lore显示价格）
        int displayCount = Math.min(9, trades.size());
        for (int i = 0; i < displayCount; i++) {
            ShopTrade trade = trades.get(i);
            inv.setItem(i, createCustomerTradeItem(trade, i));
        }

        // 填充剩余交易槽位
        for (int i = displayCount; i < 9; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // slot 9-12: 分隔线
        for (int i = 9; i < 13; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // slot 13: 选中的交易详情（大图标显示）
        if (!trades.isEmpty()) {
            inv.setItem(13, createTradeDetailItem(trades.get(0), 0));
        } else {
            inv.setItem(13, createItem(
                    Material.BARRIER,
                    "&c暂无商品",
                    "&7该商店还没有上架任何商品"
            ));
        }

        // slot 14-17: 分隔线
        for (int i = 14; i < 18; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // slot 18: 关闭按钮
        inv.setItem(18, createItem(
                Material.BARRIER,
                "&c关闭商店",
                "&7左键点击关闭"
        ));

        // slot 19-21: 分隔/装饰
        for (int i = 19; i < 22; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        // slot 22: 购买按钮
        inv.setItem(22, createItem(
                Material.EMERALD,
                "&a&l点击购买",
                "&7先选择上方的交易",
                "&7然后点击此处购买"
        ));

        // slot 23-26: 玩家余额显示
        Economy eco = PhilosNPCPlugin.economy();
        String balanceStr = eco != null ? String.format("%.2f", eco.getBalance(customer)) : "N/A";
        inv.setItem(23, createItem(
                Material.GOLD_INGOT,
                "&6你的余额",
                "&f" + balanceStr + " 金币"
        ));

        for (int i = 24; i < 27; i++) {
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
     * 显示产出物品，Lore显示价格和使用次数
     */
    private static ItemStack createTradeDisplayItem(ShopTrade trade, int index) {
        ItemStack result = trade.getResult().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            // 修改名称，加上编号
            String originalName = meta.hasDisplayName() ? meta.getDisplayName() : result.getType().name();
            meta.setDisplayName(PhilosNPCPlugin.cc("&a交易 #" + (index + 1) + " - " + originalName));

            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7&m-------------------"));
            lore.add(PhilosNPCPlugin.cc("&e价格1: &f" + formatItem(trade.getPrice1())));
            if (trade.getPrice2() != null) {
                lore.add(PhilosNPCPlugin.cc("&e价格2: &f" + formatItem(trade.getPrice2())));
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
     * 创建顾客视角的交易物品
     */
    private static ItemStack createCustomerTradeItem(ShopTrade trade, int index) {
        ItemStack result = trade.getResult().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            String originalName = meta.hasDisplayName() ? meta.getDisplayName() : result.getType().name();
            meta.setDisplayName(PhilosNPCPlugin.cc("&a" + originalName));

            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7&m-------------------"));
            lore.add(PhilosNPCPlugin.cc("&e价格: &f" + formatItem(trade.getPrice1())));
            if (trade.getPrice2() != null) {
                lore.add(PhilosNPCPlugin.cc("&e     + &f" + formatItem(trade.getPrice2())));
            }
            lore.add(PhilosNPCPlugin.cc("&7&m-------------------"));
            if (!trade.canUse()) {
                lore.add(PhilosNPCPlugin.cc("&c&l已售罄"));
            } else if (trade.isInfinite()) {
                lore.add(PhilosNPCPlugin.cc("&b库存: &f无限"));
            } else {
                int remaining = trade.getMaxUses() - trade.getUses();
                lore.add(PhilosNPCPlugin.cc("&b库存: &f" + remaining));
            }
            lore.add(PhilosNPCPlugin.cc("&a左键点击查看详情"));
            meta.setLore(lore);
            result.setItemMeta(meta);
        }
        return result;
    }

    /**
     * 创建交易详情大图标物品
     */
    private static ItemStack createTradeDetailItem(ShopTrade trade, int index) {
        ItemStack result = trade.getResult().clone();
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            String originalName = meta.hasDisplayName() ? meta.getDisplayName() : result.getType().name();
            meta.setDisplayName(PhilosNPCPlugin.cc("&a&l" + originalName));

            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7&m-------------------"));
            lore.add(PhilosNPCPlugin.cc("&e&l所需物品:"));
            lore.add(PhilosNPCPlugin.cc("&f  " + formatItem(trade.getPrice1())));
            if (trade.getPrice2() != null) {
                lore.add(PhilosNPCPlugin.cc("&f  " + formatItem(trade.getPrice2())));
            }
            lore.add(PhilosNPCPlugin.cc("&7&m-------------------"));
            lore.add(PhilosNPCPlugin.cc("&e&l你将获得:"));
            lore.add(PhilosNPCPlugin.cc("&f  " + formatItem(trade.getResult())));
            lore.add(PhilosNPCPlugin.cc("&7&m-------------------"));
            if (!trade.canUse()) {
                lore.add(PhilosNPCPlugin.cc("&c&l此商品已售罄"));
            } else if (trade.isInfinite()) {
                lore.add(PhilosNPCPlugin.cc("&b库存: &f无限"));
            } else {
                int remaining = trade.getMaxUses() - trade.getUses();
                lore.add(PhilosNPCPlugin.cc("&b剩余库存: &f" + remaining));
            }
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
