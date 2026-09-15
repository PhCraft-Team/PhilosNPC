package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.UUID;

public class ShopHandler {

    private final PhilosNPCPlugin plugin;
    private final NPCManager npcManager;

    public ShopHandler() {
        this.plugin = PhilosNPCPlugin.instance();
        this.npcManager = plugin.npcManager();
    }

    // ===== 添加交易配方 =====

    /**
     * 添加交易配方到NPC
     * @param npc NPC对象
     * @param result 产出物品
     * @param price1 价格物品1
     * @param price2 价格物品2（可选，传null）
     * @param maxUses 最大交易次数（-1表示无限）
     * @return 是否添加成功
     */
    public boolean addTrade(PhilosNPC npc, ItemStack result, ItemStack price1, ItemStack price2, int maxUses) {
        if (npc == null || result == null || price1 == null) {
            return false;
        }

        ShopTrade trade = new ShopTrade(result.clone(), price1.clone(),
                price2 != null ? price2.clone() : null, maxUses);
        npc.getTrades().add(trade);
        npcManager.saveAll();
        return true;
    }

    // ===== 删除交易配方 =====

    /**
     * 删除指定索引的交易配方
     * @param npc NPC对象
     * @param index 交易索引
     * @return 是否删除成功
     */
    public boolean removeTrade(PhilosNPC npc, int index) {
        if (npc == null) return false;
        List<ShopTrade> trades = npc.getTrades();
        if (index < 0 || index >= trades.size()) {
            return false;
        }
        trades.remove(index);
        npcManager.saveAll();
        return true;
    }

    // ===== 执行交易 =====

    /**
     * 执行玩家与NPC的交易
     * @param customer 顾客玩家
     * @param npc NPC对象
     * @param tradeIndex 交易索引
     * @return 交易结果（SUCCESS/失败原因）
     */
    public TradeResult executeTrade(Player customer, PhilosNPC npc, int tradeIndex) {
        if (customer == null || npc == null) {
            return TradeResult.FAILED;
        }

        List<ShopTrade> trades = npc.getTrades();
        if (tradeIndex < 0 || tradeIndex >= trades.size()) {
            return TradeResult.FAILED;
        }

        ShopTrade trade = trades.get(tradeIndex);

        // 检查交易次数
        if (!trade.canUse()) {
            return TradeResult.OUT_OF_STOCK;
        }

        PlayerInventory playerInv = customer.getInventory();
        boolean isSystem = npc.isSystem();

        if (trade.isUseCurrency()) {
            // 金币交易
            if (PhilosNPCPlugin.economy() == null) {
                return TradeResult.FAILED;
            }
            if (!PhilosNPCPlugin.economy().has(customer, trade.getCurrencyPrice())) {
                return TradeResult.NOT_ENOUGH_MONEY;
            }
            // 检查玩家背包是否有足够空间
            if (!playerInv.addItem(trade.getResult().clone()).isEmpty()) {
                return TradeResult.INVENTORY_FULL;
            }
            // 扣除金币
            PhilosNPCPlugin.economy().withdrawPlayer(customer, trade.getCurrencyPrice());
            // 给予物品
            playerInv.addItem(trade.getResult().clone());
        } else {
            // 物物交换
            if (!hasEnoughItem(playerInv, trade.getPrice1())) {
                return TradeResult.NOT_ENOUGH_PRICE1;
            }
            if (trade.getPrice2() != null && !hasEnoughItem(playerInv, trade.getPrice2())) {
                return TradeResult.NOT_ENOUGH_PRICE2;
            }

            if (!isSystem) {
                // 个人NPC：检查商店背包库存
                ItemStack[] shopInv = npcManager.getSharedShopInventory(npc.getOwnerUuid());
                if (!hasEnoughItemInInventory(shopInv, trade.getResult())) {
                    return TradeResult.SHOP_OUT_OF_STOCK;
                }
            }

            // 检查玩家背包是否有足够空间
            if (!playerInv.addItem(trade.getResult().clone()).isEmpty()) {
                return TradeResult.INVENTORY_FULL;
            }

            // 扣除玩家的价格物品
            removeItem(playerInv, trade.getPrice1());
            if (trade.getPrice2() != null) {
                removeItem(playerInv, trade.getPrice2());
            }

            // 个人NPC：从商店背包中扣除产出物品
            if (!isSystem) {
                ItemStack[] shopInv = npcManager.getSharedShopInventory(npc.getOwnerUuid());
                removeItemFromInventory(shopInv, trade.getResult());
                npcManager.setSharedShopInventory(npc.getOwnerUuid(), shopInv);
            }
        }

        // 更新交易次数
        trade.incrementUses();
        npcManager.saveAll();

        return TradeResult.SUCCESS;
    }

    // ===== 商店背包操作 =====

    /**
     * 保存商店背包内容
     * @param ownerUuid 店主UUID
     * @param items 背包物品数组
     */
    public void saveShopInventory(UUID ownerUuid, ItemStack[] items) {
        npcManager.setSharedShopInventory(ownerUuid, items);
        npcManager.saveAll();
    }

    /**
     * 获取商店背包内容
     * @param ownerUuid 店主UUID
     * @return 背包物品数组
     */
    public ItemStack[] getShopInventory(UUID ownerUuid) {
        return npcManager.getSharedShopInventory(ownerUuid);
    }

    // ===== 辅助方法 =====

    /**
     * 检查玩家背包中是否有足够的指定物品
     */
    private boolean hasEnoughItem(PlayerInventory inv, ItemStack target) {
        if (target == null) return true;
        int amount = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(target)) {
                amount += item.getAmount();
            }
        }
        return amount >= target.getAmount();
    }

    /**
     * 检查物品数组中是否有足够的指定物品
     */
    private boolean hasEnoughItemInInventory(ItemStack[] inv, ItemStack target) {
        if (target == null) return true;
        int amount = 0;
        for (ItemStack item : inv) {
            if (item != null && item.isSimilar(target)) {
                amount += item.getAmount();
            }
        }
        return amount >= target.getAmount();
    }

    /**
     * 从玩家背包中移除指定数量的物品
     */
    private void removeItem(PlayerInventory inv, ItemStack target) {
        if (target == null) return;
        int remaining = target.getAmount();
        ItemStack[] contents = inv.getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(target)) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    contents[i] = null;
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }

        inv.setContents(contents);
    }

    /**
     * 从物品数组中移除指定数量的物品
     */
    private void removeItemFromInventory(ItemStack[] inv, ItemStack target) {
        if (target == null) return;
        int remaining = target.getAmount();

        for (int i = 0; i < inv.length && remaining > 0; i++) {
            ItemStack item = inv[i];
            if (item != null && item.isSimilar(target)) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    inv[i] = null;
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }

    // ===== 交易结果枚举 =====

    public enum TradeResult {
        SUCCESS,          // 交易成功
        FAILED,           // 未知错误
        OUT_OF_STOCK,     // 交易次数用完
        NOT_ENOUGH_PRICE1, // 价格1物品不足
        NOT_ENOUGH_PRICE2, // 价格2物品不足
        NOT_ENOUGH_MONEY,  // 金币不足
        SHOP_OUT_OF_STOCK, // 商店库存不足
        INVENTORY_FULL;    // 玩家背包已满

        public String getMessage() {
            return switch (this) {
                case SUCCESS -> PhilosNPCPlugin.cc("&a交易完成");
                case OUT_OF_STOCK -> PhilosNPCPlugin.cc("&c已售罄");
                case NOT_ENOUGH_PRICE1 -> PhilosNPCPlugin.cc("&c价格物品1不足");
                case NOT_ENOUGH_PRICE2 -> PhilosNPCPlugin.cc("&c价格物品2不足");
                case NOT_ENOUGH_MONEY -> PhilosNPCPlugin.cc("&c金币不足");
                case SHOP_OUT_OF_STOCK -> PhilosNPCPlugin.cc("&c商店库存不足");
                case INVENTORY_FULL -> PhilosNPCPlugin.cc("&c背包已满");
                default -> PhilosNPCPlugin.cc("&c交易失败");
            };
        }
    }
}
