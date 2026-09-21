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

        // slot 42: 收购背包 (HOPPER)
        inv.setItem(42, createItem(
                Material.HOPPER,
                "&d收购背包",
                "&7物物交易收到的物品",
                "&7会自动存入这里",
                "&7你所有的NPC共享此背包",
                "&7容量无限，随时可取回",
                "&e左键点击查看/取回"
        ));

        return inv;
    }

    // ===== 交易配方编辑界面（54格，支持金币/物品两种交易模式） =====

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

        // 第4行：价格槽（27/28）+ 产出槽（29）+ 添加（30）+ 模式开关（31）+ 说明（32）
        boolean itemMode = npc.isShopItemTradeMode();
        if (itemMode) {
            inv.setItem(27, GuiManager.markPlaceholder(createItem(
                    Material.PAPER,
                    "&e价格物品槽位 1",
                    "&7放入第一种价格物品",
                    "&7（物物交换必填）"
            )));
            inv.setItem(28, GuiManager.markPlaceholder(createItem(
                    Material.PAPER,
                    "&e价格物品槽位 2",
                    "&7放入第二种价格物品",
                    "&7（可选，最多两种价格）"
            )));
        } else {
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
        }

        inv.setItem(29, GuiManager.markPlaceholder(createItem(
                Material.PAPER,
                "&e产出物品槽位",
                "&7将产出物品放入此槽位",
                itemMode ? "&7点击添加即完成物物交换" : "&7点击添加后在聊天框输入价格"
        )));

        inv.setItem(30, createItem(
                Material.EMERALD,
                "&a&l点击添加交易",
                itemMode ? "&7读取价格物品(槽27/28)" : "&7读取左侧产出物品",
                itemMode ? "&7与产出物品(槽29)" : "&7然后在聊天框输入金币价格"
        ));

        // 交易模式开关：物品交易(以物换物) / 金币交易
        inv.setItem(31, createItem(
                itemMode ? Material.CHEST : Material.GOLD_INGOT,
                itemMode ? "&6交易模式：&b物品交易" : "&6交易模式：&e金币交易",
                itemMode ? "&7新增交易以物换物结算" : "&7新增交易以金币结算",
                itemMode ? "&e点击切换为金币交易" : "&e点击切换为物品交易(以物换物)",
                "&7已添加的交易不受影响"
        ));

        if (npc.isSystem()) {
            inv.setItem(32, createItem(
                    Material.BOOK,
                    "&b说明",
                    "&7金币模式：放入产出点击添加，",
                    "&7聊天框输入价格",
                    "&7物品模式：价格槽27/28+产出槽29，",
                    "&7点击添加即完成",
                    "&7产出物品由系统直接生成"
            ));
        } else {
            inv.setItem(32, createItem(
                    Material.BOOK,
                    "&b库存说明",
                    "&7出售的产出物品将从你的",
                    "&7共享商店背包中扣除",
                    "&7物物交易收到的物品",
                    "&7会自动存入你的收购背包",
                    "&7请在商店背包界面补充库存"
            ));
        }

        for (int i = 33; i < 36; i++) {
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

    // ===== 收购背包界面（54格，物物交易收入，无限容量可翻页） =====

    /**
     * 收购背包界面：0-44展示物品（45个/页），
     * 45上一页 / 49一键取回全部 / 51下一页 / 53返回
     */
    public static Inventory collectionBackpackGui(PhilosNPC npc, int page, InventoryHolder holder) {
        List<ItemStack> backpack = PhilosNPCPlugin.instance().npcManager()
                .getCollectionBackpack(npc.getOwnerUuid());

        int itemsPerPage = 45;
        int totalPages = Math.max(1, (backpack.size() + itemsPerPage - 1) / itemsPerPage);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(holder, 54,
                PhilosNPCPlugin.cc("&b&l收购背包 - 第" + (page + 1) + "/" + totalPages + "页"));

        int start = page * itemsPerPage;
        for (int i = 0; i < itemsPerPage; i++) {
            int index = start + i;
            if (index < backpack.size()) {
                inv.setItem(i, backpack.get(index).clone());
            }
        }

        boolean hasPrev = page > 0;
        inv.setItem(45, createItem(
                hasPrev ? Material.ARROW : Material.LEVER,
                hasPrev ? "&a上一页" : "&7已是第一页",
                hasPrev ? "&7左键点击上一页" : "&c没有上一页了"
        ));

        if (backpack.isEmpty()) {
            inv.setItem(49, createItem(
                    Material.CHEST,
                    "&7收购背包是空的",
                    "&7玩家NPC物物交易收到的物品",
                    "&7会自动存入这里"
            ));
        } else {
            inv.setItem(49, createItem(
                    Material.CHEST,
                    "&a&l一键取回全部",
                    "&7共有 &f" + backpack.size() + " &7组物品",
                    "&7超出背包容量的物品",
                    "&7将掉落在你脚下",
                    "&e左键点击取回"
            ));
        }

        boolean hasNext = page < totalPages - 1;
        inv.setItem(51, createItem(
                hasNext ? Material.ARROW : Material.LEVER,
                hasNext ? "&a下一页" : "&7已是最后一页",
                hasNext ? "&7左键点击下一页" : "&c没有下一页了"
        ));

        inv.setItem(53, createItem(
                Material.BARRIER,
                "&c返回",
                "&7左键点击返回商店背包界面"
        ));

        for (int i = 46; i < 49; i++) {
            inv.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }
        inv.setItem(50, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        inv.setItem(52, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));

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
