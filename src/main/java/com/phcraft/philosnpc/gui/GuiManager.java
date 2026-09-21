package com.phcraft.philosnpc.gui;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.PluginSettings;
import com.phcraft.philosnpc.features.FeatureGuiFactory;
import com.phcraft.philosnpc.features.GiftPack;
import com.phcraft.philosnpc.features.GiftPackFeature;
import com.phcraft.philosnpc.features.GiftPackGui;
import com.phcraft.philosnpc.features.MessageFeature;
import com.phcraft.philosnpc.features.ShopGui;
import com.phcraft.philosnpc.features.ShopTrade;
import com.phcraft.philosnpc.features.TeleportFeature;
import com.phcraft.philosnpc.features.UsageNotify;
import com.phcraft.philosnpc.npc.FeatureType;
import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.npc.NPCPose;
import com.phcraft.philosnpc.npc.PhilosNPC;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiManager implements Listener {

    private final PhilosNPCPlugin plugin;
    private final NPCManager npcManager;
    // 记录玩家当前打开的村民交易NPC（含过滤后的交易列表，用于交易事件拦截）
    private final Map<UUID, MerchantSession> merchantSessions = new HashMap<>();

    // 装备编辑界面的可交互槽位
    private static final int[] EQUIPMENT_SLOTS = {10, 19, 28, 37, 24}; // 头盔, 胸甲, 护腿, 靴子, 主手

    // 可交互槽内的占位符标记（防止提示物品被拿起）
    private static NamespacedKey placeholderKey;

    // 商人界面金币交易门票标记（服务端填充，玩家不可拿取/不消耗/关闭时清除）
    private static NamespacedKey ticketKey;

    private static NamespacedKey placeholderKey() {
        if (placeholderKey == null) {
            placeholderKey = new NamespacedKey(PhilosNPCPlugin.instance(), "gui_placeholder");
        }
        return placeholderKey;
    }

    private static NamespacedKey ticketKey() {
        if (ticketKey == null) {
            ticketKey = new NamespacedKey(PhilosNPCPlugin.instance(), "merchant_ticket");
        }
        return ticketKey;
    }

    /**
     * 标记物品为GUI占位符：可交互槽内的占位符不可被拿起，
     * 但光标持有物品点击时会替换占位符
     */
    public static ItemStack markPlaceholder(ItemStack item) {
        if (item != null) {
            var meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(placeholderKey(), PersistentDataType.BYTE, (byte) 1);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    public static boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(placeholderKey(), PersistentDataType.BYTE);
    }

    /**
     * 村民交易会话：NPC + 实际展示的交易列表（售罄已过滤，索引与商人界面对应）
     */
    private static class MerchantSession {
        final PhilosNPC npc;
        final List<ShopTrade> trades;

        MerchantSession(PhilosNPC npc, List<ShopTrade> trades) {
            this.npc = npc;
            this.trades = trades;
        }
    }

    public GuiManager() {
        this.plugin = PhilosNPCPlugin.instance();
        this.npcManager = plugin.npcManager();
    }

    // ===== 打开界面方法 =====

    public void openMainGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.MAIN, npc.getId(), 0, new HashMap<>());
        Inventory inv = NPCGui.mainGui(npc, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openFeatureSelectGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.FEATURE_SELECT, npc.getId(), 0, new HashMap<>());
        Inventory inv = NPCGui.featureSelectGui(npc, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openPoseSelectGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.POSE_SELECT, npc.getId(), 0, new HashMap<>());
        Inventory inv = NPCGui.poseSelectGui(npc, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openSizeAdjustGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.SIZE_ADJUST, npc.getId(), 0, new HashMap<>());
        Inventory inv = NPCGui.sizeAdjustGui(npc, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openEquipmentGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.EQUIPMENT_EDIT, npc.getId(), 0, new HashMap<>());
        Inventory inv = NPCGui.equipmentGui(npc, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openShopEditGui(Player player, PhilosNPC npc) {
        if (npc.isSystem()) {
            openTradeEditGui(player, npc, 0);
        } else {
            GuiState state = new GuiState(GuiState.Screen.SHOP_EDIT, npc.getId(), 0, new HashMap<>());
            Inventory inv = ShopGui.shopInventoryGui(npc, state);
            state.setInventory(inv);
            player.openInventory(inv);
        }
    }

    public void openTradeEditGui(Player player, PhilosNPC npc, int page) {
        GuiState state = new GuiState(GuiState.Screen.SHOP_EDIT, npc.getId(), page, new HashMap<>());
        Inventory inv = ShopGui.tradeEditGui(npc, page, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    // ===== 收购背包界面 =====

    public void openCollectionBackpackGui(Player player, PhilosNPC npc, int page) {
        Map<String, Object> data = new HashMap<>();
        data.put("ownerUuid", npc.getOwnerUuid().toString());
        GuiState state = new GuiState(GuiState.Screen.COLLECTION_BACKPACK, npc.getId(), page, data);
        Inventory inv = ShopGui.collectionBackpackGui(npc, page, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    /**
     * 收购背包点击处理：45上一页 / 49一键取回全部 / 51下一页 / 53返回。
     * 界面纯只读展示，物品槽不可交互。
     */
    private void handleCollectionBackpackClick(Player player, PhilosNPC npc, int slot, GuiState state) {
        int page = state.getPage();

        switch (slot) {
            case 45: // 上一页
                if (npc != null && page > 0) {
                    openCollectionBackpackGui(player, npc, page - 1);
                }
                break;
            case 49: // 一键取回全部
                handleRetrieveAllCollection(player, npc, state);
                break;
            case 51: // 下一页
                if (npc != null) {
                    openCollectionBackpackGui(player, npc, page + 1);
                }
                break;
            case 53: // 返回商店背包界面
                if (npc != null) {
                    openShopEditGui(player, npc);
                }
                break;
        }
    }

    /**
     * 一键取回收购背包全部物品：直接进背包，放不下的掉落在脚下。
     * ownerUuid 存于 GuiState data，即使NPC已被删除也能取回。
     */
    private void handleRetrieveAllCollection(Player player, PhilosNPC npc, GuiState state) {
        UUID owner = resolveOwnerUuid(state, npc);
        if (owner == null) return;

        List<ItemStack> items = npcManager.clearCollectionBackpack(owner);
        if (items.isEmpty()) {
            player.sendMessage(PhilosNPCPlugin.cc("&c收购背包是空的"));
            return;
        }

        int stacks = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            giveResult(player, item);
            stacks++;
        }
        npcManager.saveAll();
        player.sendMessage(PhilosNPCPlugin.cc("&a已取回 &f" + stacks + " &a组物品，超出背包容量的已掉落在脚下"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        if (npc != null) {
            openCollectionBackpackGui(player, npc, 0);
        } else {
            player.closeInventory();
        }
    }

    private UUID resolveOwnerUuid(GuiState state, PhilosNPC npc) {
        Object stored = state.getData().get("ownerUuid");
        if (stored instanceof String s) {
            try {
                return UUID.fromString(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return npc != null ? npc.getOwnerUuid() : null;
    }

    public void openTpSettingsGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.TP_SETTINGS, npc.getId(), 0, new HashMap<>());
        Inventory inv = FeatureGuiFactory.teleportSettingsGui(npc, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openMessageEditGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.MESSAGE_EDIT, npc.getId(), 0, new HashMap<>());
        Inventory inv = FeatureGuiFactory.messageEditGui(npc, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openCustomerGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.CUSTOMER, npc.getId(), 0, new HashMap<>());
        Inventory inv = FeatureGuiFactory.customerFeatureGui(npc, player, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    // ===== 礼包发放界面 =====

    public void openGiftPackListGui(Player player, PhilosNPC npc, int page) {
        GuiState state = new GuiState(GuiState.Screen.GIFT_PACK_LIST, npc.getId(), page, new HashMap<>());
        Inventory inv = GiftPackGui.listGui(npc, page, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openGiftPackEditGui(Player player, PhilosNPC npc, GiftPack pack) {
        // 同界面刷新（点保存）时，新界面在关闭事件保存之前渲染，
        // 必须先把当前界面的内容物存入pack，否则书本统计与面板显示旧数据
        Inventory current = player.getOpenInventory().getTopInventory();
        if (current.getHolder() instanceof GuiState cur
                && cur.getScreen() == GuiState.Screen.GIFT_PACK_EDIT
                && npc.getId().equals(cur.getNpcId())
                && pack == resolvePack(cur, npc)) {
            saveGiftPackContents(player, pack, current);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("packId", pack.getId());
        GuiState state = new GuiState(GuiState.Screen.GIFT_PACK_EDIT, npc.getId(), 0, data);
        Inventory inv = GiftPackGui.editGui(npc, pack, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openGiftPackContainerGui(Player player, PhilosNPC npc, GiftPack pack) {
        Map<String, Object> data = new HashMap<>();
        data.put("packId", pack.getId());
        GuiState state = new GuiState(GuiState.Screen.GIFT_PACK_CONTAINER, npc.getId(), 0, data);
        Inventory inv = GiftPackGui.containerGui(npc, pack, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openGiftPackClaimGui(Player player, PhilosNPC npc, int page) {
        GuiState state = new GuiState(GuiState.Screen.GIFT_PACK_CLAIM, npc.getId(), page, new HashMap<>());
        Inventory inv = GiftPackGui.claimGui(npc, player, page, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    /**
     * 打开真正的村民交易界面
     */
    public void openMerchantShop(Player player, PhilosNPC npc) {
        List<ShopTrade> allTrades = npc.getTrades();
        if (allTrades.isEmpty()) {
            player.sendMessage(PhilosNPCPlugin.cc("&c该商店暂无商品"));
            return;
        }

        Merchant merchant = Bukkit.createMerchant(PhilosNPCPlugin.cc("&b&l" + npc.getDisplayName() + " 的商店"));
        List<MerchantRecipe> recipes = new ArrayList<>();
        List<ShopTrade> sessionTrades = new ArrayList<>();

        for (ShopTrade trade : allTrades) {
            if (!trade.canUse()) continue; // 售罄的交易不显示
            // 脏数据防护：缺少产出或物物价格缺失的交易跳过，避免NPE
            if (trade.getResult() == null) continue;
            if (!trade.isUseCurrency() && trade.getPrice1() == null) continue;

            ItemStack recipeResult = trade.getResult().clone();
            MerchantRecipe recipe;
            if (trade.isUseCurrency()) {
                // 金币交易：1个普通金锭作为"门票"放在输入槽（服务端自动填充，玩家无需自备），
                // 价格显示在结果物品lore上，点击购买时扣Vault余额，门票不消耗
                var displayMeta = recipeResult.getItemMeta();
                if (displayMeta != null) {
                    List<String> lore = displayMeta.hasLore()
                            ? new ArrayList<>(displayMeta.getLore()) : new ArrayList<>();
                    lore.add(PhilosNPCPlugin.cc("&6价格: " + trade.getCurrencyPrice() + " 金币"));
                    lore.add(PhilosNPCPlugin.cc("&e直接点击购买"));
                    lore.add(PhilosNPCPlugin.cc("&7购买时自动扣除金币余额"));
                    displayMeta.setLore(lore);
                    recipeResult.setItemMeta(displayMeta);
                }
                recipe = new MerchantRecipe(recipeResult, Integer.MAX_VALUE);
                recipe.addIngredient(new ItemStack(Material.GOLD_INGOT, 1));
            } else {
                recipe = new MerchantRecipe(recipeResult, Integer.MAX_VALUE);
                recipe.addIngredient(trade.getPrice1().clone());
                if (trade.getPrice2() != null) {
                    recipe.addIngredient(trade.getPrice2().clone());
                }
            }
            recipes.add(recipe);
            sessionTrades.add(trade);
        }

        if (recipes.isEmpty()) {
            player.sendMessage(PhilosNPCPlugin.cc("&c该商店的商品已售罄"));
            return;
        }

        merchant.setRecipes(recipes);
        player.openMerchant(merchant, true);
        // 会话在 openMerchant 之后注册，避免关闭旧界面时被误清理
        merchantSessions.put(player.getUniqueId(), new MerchantSession(npc, sessionTrades));

        // 默认选中的配方若为金币交易，自动填充门票（延迟确保界面已打开）
        if (!sessionTrades.isEmpty() && sessionTrades.get(0).isUseCurrency()) {
            Bukkit.getScheduler().runTask(plugin, () -> fillMerchantTicket(player));
        }
    }

    /**
     * 金币交易门票：服务端放入商人输入槽的金锭，使结果槽自动显示商品。
     * 玩家无法拿取，购买不消耗，关闭/退出时清除，防止刷物品。
     */
    private void fillMerchantTicket(Player player) {
        if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory mi
                && merchantSessions.containsKey(player.getUniqueId())) {
            mi.setItem(0, createMerchantTicket());
        }
    }

    private ItemStack createMerchantTicket() {
        ItemStack ticket = new ItemStack(Material.GOLD_INGOT);
        var meta = ticket.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PhilosNPCPlugin.cc("&6金币交易"));
            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&e直接点击右侧产出格购买"));
            lore.add(PhilosNPCPlugin.cc("&7购买时自动扣除金币余额"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(ticketKey(), PersistentDataType.BYTE, (byte) 1);
            ticket.setItemMeta(meta);
        }
        return ticket;
    }

    private boolean isMerchantTicket(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        var meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(ticketKey(), PersistentDataType.BYTE);
    }

    /**
     * 清除商人界面输入槽中的门票（关闭/退出时调用，防止门票掉落被玩家获取）
     */
    private void clearMerchantTickets(Player player) {
        if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory mi) {
            for (int i = 0; i <= 1; i++) {
                if (isMerchantTicket(mi.getItem(i))) {
                    mi.setItem(i, null);
                }
            }
        }
    }

    /**
     * 玩家在商人界面切换选中的配方：
     * 金币交易 → 自动填充门票；物物交易 → 清除门票让玩家放价格物品
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTradeSelect(org.bukkit.event.inventory.TradeSelectEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MerchantSession session = merchantSessions.get(player.getUniqueId());
        if (session == null) return;

        MerchantInventory mi = event.getInventory();
        int index = event.getIndex();
        if (index < 0 || index >= session.trades.size()) return;

        if (session.trades.get(index).isUseCurrency()) {
            // 玩家已在输入槽放了物品时先退还，避免被门票覆盖丢失
            ItemStack existing = mi.getItem(0);
            if (existing != null && !existing.getType().isAir() && !isMerchantTicket(existing)) {
                mi.setItem(0, null);
                giveResult(player, existing);
            }
            mi.setItem(0, createMerchantTicket());
        } else if (isMerchantTicket(mi.getItem(0))) {
            mi.setItem(0, null);
        }
    }

    public void openNPCListGui(Player player, int page) {
        openNPCListGui(player, page, false);
    }

    public void openNPCListGui(Player player, int page, boolean systemList) {
        List<PhilosNPC> npcs;
        if (systemList) {
            npcs = npcManager.getSystemNPCs();
        } else {
            npcs = npcManager.getNPCsByOwner(player.getUniqueId());
        }

        int perPage = 28;
        int totalPages = Math.max(1, (npcs.size() + perPage - 1) / perPage);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Map<String, Object> data = new HashMap<>();
        data.put("systemList", systemList);
        GuiState state = new GuiState(GuiState.Screen.NPC_LIST, null, page, data);
        Inventory inv = NPCGui.npcListGui(npcs, page, totalPages, systemList, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    private ItemStack createButton(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PhilosNPCPlugin.cc(name));
            if (lore.length > 0) {
                var loreList = new ArrayList<String>();
                for (String l : lore) loreList.add(PhilosNPCPlugin.cc(l));
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ===== 点击事件分发 =====

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        Inventory topInv = event.getView().getTopInventory();

        // ===== 村民交易界面（交易拦截，手动处理扣款/扣库存） =====
        if (topInv instanceof MerchantInventory && merchantSessions.containsKey(player.getUniqueId())) {
            MerchantSession session = merchantSessions.get(player.getUniqueId());
            if (session == null) return;

            int rawSlot = event.getRawSlot();
            ClickType clickType = event.getClick();

            // 门票防护：输入槽中的门票不可被拿取/替换
            if (rawSlot == 0 || rawSlot == 1) {
                if (isMerchantTicket(topInv.getItem(rawSlot))) {
                    event.setCancelled(true);
                    return;
                }
            }
            // 玩家背包shift点击：防止物品被快速移入商人槽与门票发生合并
            if (rawSlot >= 3 && (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT)) {
                event.setCancelled(true);
                return;
            }
            // 双击收集：防止门票被收集到光标
            if (clickType == ClickType.DOUBLE_CLICK
                    && (isMerchantTicket(topInv.getItem(0)) || isMerchantTicket(topInv.getItem(1)))) {
                event.setCancelled(true);
                return;
            }

            // 点击结果槽位（slot 2）= 尝试购买
            if (rawSlot == 2) {
                event.setCancelled(true);
                if (event.getClick() != ClickType.LEFT) return;

                MerchantInventory mi = (MerchantInventory) topInv;
                // 结果槽未显示商品（未放入所需物品）时不可购买
                ItemStack shownResult = mi.getItem(2);
                if (shownResult == null || shownResult.getType().isAir()) return;

                int selected = mi.getSelectedRecipeIndex();
                if (selected < 0 || selected >= session.trades.size()) return;
                ShopTrade trade = session.trades.get(selected);

                if (!trade.canUse()) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c此商品已售罄"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                if (trade.isUseCurrency()) {
                    // 金币交易：直接点击购买，扣Vault余额
                    if (PhilosNPCPlugin.economy() == null) {
                        player.sendMessage(PhilosNPCPlugin.cc("&c经济系统未启用"));
                        return;
                    }
                    boolean isSystem = session.npc.isSystem();
                    UUID ownerUuid = session.npc.getOwnerUuid();
                    boolean selfPurchase = !isSystem && player.getUniqueId().equals(ownerUuid);
                    // 个人NPC：先检查共享商店背包库存
                    if (!isSystem) {
                        ItemStack[] shopInv = npcManager.getSharedShopInventory(ownerUuid);
                        if (!hasEnoughInArray(shopInv, trade.getResult())) {
                            player.sendMessage(PhilosNPCPlugin.cc("&c商店库存不足"));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            return;
                        }
                    }
                    double price = trade.getCurrencyPrice();
                    // 自购：收益本来就是自己的，免单（净零流动）
                    if (!selfPurchase) {
                        EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, price);
                        if (!resp.transactionSuccess()) {
                            player.sendMessage(PhilosNPCPlugin.cc("&c金币不足"));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            return;
                        }
                        // 个人NPC：货款付给NPC主人
                        if (!isSystem && ownerUuid != null) {
                            PhilosNPCPlugin.economy().depositPlayer(Bukkit.getOfflinePlayer(ownerUuid), price);
                        }
                    }
                    // 个人NPC：从共享商店背包扣除产出
                    if (!isSystem) {
                        ItemStack[] shopInv = npcManager.getSharedShopInventory(ownerUuid);
                        removeFromArray(shopInv, trade.getResult());
                        npcManager.setSharedShopInventory(ownerUuid, shopInv);
                    }
                    giveResult(player, trade.getResult().clone());
                    trade.incrementUses();
                    npcManager.saveAll();
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                    player.sendMessage(PhilosNPCPlugin.cc(selfPurchase
                            ? "&a购买成功（自己的商店，无需付款）"
                            : "&a购买成功，花费 " + price + " 金币"));
                    // 使用成功通知主人（自购与系统NPC由notify内部过滤）
                    UsageNotify.notify(session.npc, player,
                            "&e" + player.getName() + " &a在你的 &f" + session.npc.getDisplayName()
                                    + " &a消费了 &f" + price + " &a金币");
                } else {
                    // 物物交换：校验商人槽位中的价格物品
                    if (!ingredientReady(mi.getItem(0), trade.getPrice1())
                            || !ingredientReady(mi.getItem(1), trade.getPrice2())) {
                        return; // 价格物品未放齐，静默忽略
                    }

                    // 个人NPC：禁止店主与自己交易（防止借收购背包无限囤积物品）
                    if (!session.npc.isSystem() && player.getUniqueId().equals(session.npc.getOwnerUuid())) {
                        player.sendMessage(PhilosNPCPlugin.cc("&c不能与自己的商店交易"));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }

                    // 个人NPC：检查共享商店背包库存
                    if (!session.npc.isSystem()) {
                        ItemStack[] shopInv = npcManager.getSharedShopInventory(session.npc.getOwnerUuid());
                        if (!hasEnoughInArray(shopInv, trade.getResult())) {
                            player.sendMessage(PhilosNPCPlugin.cc("&c商店库存不足"));
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            return;
                        }
                    }

                    // 给予产出（放不下则掉落脚下）
                    giveResult(player, trade.getResult().clone());
                    // 消耗价格物品（只扣所需数量）
                    consumeFromMerchantSlot(mi, 0, trade.getPrice1());
                    consumeFromMerchantSlot(mi, 1, trade.getPrice2());
                    // 个人NPC：价格物品存入店主的收购背包，产出从共享商店背包扣除
                    if (!session.npc.isSystem()) {
                        UUID ownerUuid = session.npc.getOwnerUuid();
                        npcManager.addItemToCollectionBackpack(ownerUuid, trade.getPrice1().clone());
                        if (trade.getPrice2() != null) {
                            npcManager.addItemToCollectionBackpack(ownerUuid, trade.getPrice2().clone());
                        }
                        ItemStack[] shopInv = npcManager.getSharedShopInventory(ownerUuid);
                        removeFromArray(shopInv, trade.getResult());
                        npcManager.setSharedShopInventory(ownerUuid, shopInv);
                    }
                    trade.incrementUses();
                    npcManager.saveAll();
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                    player.sendMessage(PhilosNPCPlugin.cc("&a交易完成"));
                    // 使用成功通知主人（系统NPC由notify内部过滤，自购已被拦截）
                    String paidItems = UsageNotify.fmtItem(trade.getPrice1())
                            + (trade.getPrice2() != null ? " + " + UsageNotify.fmtItem(trade.getPrice2()) : "");
                    UsageNotify.notify(session.npc, player,
                            "&e" + player.getName() + " &a在你的 &f" + session.npc.getDisplayName()
                                    + " &a用 &f" + paidItems + " &a兑换了 &f" + UsageNotify.fmtItem(trade.getResult()));
                }
            }
            return;
        }

        if (!(topInv.getHolder() instanceof GuiState)) return;
        GuiState state = (GuiState) topInv.getHolder();

        int rawSlot = event.getRawSlot();
        int topSize = topInv.getSize();
        boolean isTop = rawSlot >= 0 && rawSlot < topSize;
        int slot = isTop ? rawSlot : -1;

        // 默认全部取消
        event.setCancelled(true);

        ClickType click = event.getClick();
        boolean normalClick = click == ClickType.LEFT || click == ClickType.RIGHT;

        // 玩家自己的背包：普通左右键放行（放入编辑槽的前提）
        if (!isTop && normalClick && rawSlot >= 0) {
            event.setCancelled(false);
            return;
        }

        // 顶部可交互槽位：普通左右键且非占位符时放行
        if (isTop && normalClick && isInteractiveSlot(state, slot, true)) {
            ItemStack current = event.getCurrentItem();
            if (isPlaceholder(current)) {
                // 占位符不可被拿起；光标持有物品时手动替换占位符
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.getType().isAir()) {
                    topInv.setItem(slot, cursor.clone());
                    event.setCursor(new ItemStack(Material.AIR));
                }
                // 保持取消，继续执行按钮逻辑
            } else {
                event.setCancelled(false);
                return;
            }
        }

        // 其余全部取消，处理按钮逻辑（仅顶部槽位）
        if (isTop) {
            PhilosNPC npc = state.getNpcId() != null ? npcManager.getNPC(state.getNpcId()) : null;

            switch (state.getScreen()) {
                case MAIN:
                    handleMainClick(player, npc, slot);
                    break;
                case FEATURE_SELECT:
                    handleFeatureSelectClick(player, npc, slot);
                    break;
                case POSE_SELECT:
                    handlePoseSelectClick(player, npc, slot);
                    break;
                case SIZE_ADJUST:
                    handleSizeAdjustClick(player, npc, slot);
                    break;
                case EQUIPMENT_EDIT:
                    handleEquipmentClick(player, npc, slot);
                    break;
                case SHOP_EDIT:
                    handleShopEditClick(player, npc, slot, state);
                    break;
                case TP_SETTINGS:
                    handleTpSettingsClick(player, npc, slot);
                    break;
                case MESSAGE_EDIT:
                    handleMessageEditClick(player, npc, slot);
                    break;
                case CUSTOMER:
                    handleCustomerClick(player, npc, slot);
                    break;
                case NPC_LIST:
                    handleNpcListClick(player, slot, state);
                    break;
                case GIFT_PACK_LIST:
                    handleGiftPackListClick(player, npc, slot, state);
                    break;
                case GIFT_PACK_EDIT:
                    handleGiftPackEditClick(player, npc, slot, state);
                    break;
                case GIFT_PACK_CONTAINER:
                    handleGiftPackContainerClick(player, npc, slot, state);
                    break;
                case GIFT_PACK_CLAIM:
                    handleGiftPackClaimClick(player, npc, slot, state);
                    break;
                case COLLECTION_BACKPACK:
                    handleCollectionBackpackClick(player, npc, slot, state);
                    break;
            }
        }
    }

    /**
     * 检查顶部容器指定槽位是否允许物品交互（放入/取出）
     * @param slot 原始槽位号
     * @param isTop 是否为顶部容器槽位
     */
    private boolean isInteractiveSlot(GuiState state, int slot, boolean isTop) {
        if (!isTop) return false;

        switch (state.getScreen()) {
            case SHOP_EDIT: {
                Inventory inv = state.getInventory();
                if (inv != null && inv.getSize() == 45) {
                    // 个人NPC商店背包（45格）：0-35可交互
                    return slot >= 0 && slot < 36;
                }
                // 交易配方编辑（54格）：产出槽29始终可交互；
                // 物品交易模式下价格槽27/28也可交互
                PhilosNPC npc = state.getNpcId() != null ? npcManager.getNPC(state.getNpcId()) : null;
                if (npc != null && npc.isShopItemTradeMode()) {
                    return slot == 27 || slot == 28 || slot == 29;
                }
                return slot == 29;
            }
            case EQUIPMENT_EDIT: {
                // 装备编辑：5个装备槽可交互
                for (int s : EQUIPMENT_SLOTS) {
                    if (slot == s) return true;
                }
                return false;
            }
            case GIFT_PACK_EDIT: {
                // 礼包编辑：18-44内容物槽可交互
                return slot >= 18 && slot <= 44;
            }
            default:
                // 其他所有界面：所有槽位都不可交互
                return false;
        }
    }

    // ===== 商人交易辅助 =====

    private boolean ingredientReady(ItemStack slotItem, ItemStack required) {
        if (required == null) return true;
        return slotItem != null && slotItem.isSimilar(required) && slotItem.getAmount() >= required.getAmount();
    }

    private void consumeFromMerchantSlot(Inventory inv, int slot, ItemStack required) {
        if (required == null) return;
        ItemStack item = inv.getItem(slot);
        if (item == null || !item.isSimilar(required)) return;
        int remaining = item.getAmount() - required.getAmount();
        if (remaining <= 0) {
            inv.setItem(slot, null);
        } else {
            item.setAmount(remaining);
            inv.setItem(slot, item);
        }
    }

    private boolean hasEnoughInArray(ItemStack[] items, ItemStack target) {
        if (target == null) return true;
        int amount = 0;
        for (ItemStack item : items) {
            if (item != null && item.isSimilar(target)) amount += item.getAmount();
        }
        return amount >= target.getAmount();
    }

    private void removeFromArray(ItemStack[] items, ItemStack target) {
        if (target == null) return;
        int remaining = target.getAmount();
        for (int i = 0; i < items.length && remaining > 0; i++) {
            ItemStack item = items[i];
            if (item != null && item.isSimilar(target)) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    items[i] = null;
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
        }
    }

    private void giveResult(Player player, ItemStack result) {
        var leftover = player.getInventory().addItem(result);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Inventory topInv = event.getView().getTopInventory();

        // 商人界面：涉及顶部输入槽的拖拽一律取消（门票防护）
        if (topInv instanceof MerchantInventory
                && event.getWhoClicked() instanceof Player player
                && merchantSessions.containsKey(player.getUniqueId())) {
            for (int slot : event.getRawSlots()) {
                if (slot < 3) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        if (!(topInv.getHolder() instanceof GuiState)) return;
        GuiState state = (GuiState) topInv.getHolder();

        // 任何涉及顶部容器非交互槽位的拖拽，全部取消
        int topSize = topInv.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize && !isInteractiveSlot(state, slot, true)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        // 村民交易会话清理（清除门票防止掉落，防止污染之后的村民交易）
        if (event.getView().getTopInventory() instanceof MerchantInventory) {
            clearMerchantTickets(player);
            merchantSessions.remove(player.getUniqueId());
            return;
        }

        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof GuiState)) return;
        GuiState state = (GuiState) topInv.getHolder();
        PhilosNPC npc = state.getNpcId() != null ? npcManager.getNPC(state.getNpcId()) : null;

        switch (state.getScreen()) {
            case EQUIPMENT_EDIT -> {
                // 关闭时自动保存装备
                if (npc != null) {
                    saveEquipmentFromInventory(npc, topInv);
                }
            }
            case SHOP_EDIT -> {
                if (topInv.getSize() == 45) {
                    // 个人NPC商店背包：关闭时自动保存
                    if (npc != null && !npc.isSystem()) {
                        var items = new ItemStack[36];
                        for (int i = 0; i < 36; i++) {
                            items[i] = topInv.getItem(i);
                        }
                        npcManager.setSharedShopInventory(npc.getOwnerUuid(), items);
                        npcManager.saveAll();
                    }
                } else {
                    // 交易配方编辑：关闭时返还样品物品
                    returnSampleItems(player, topInv, 27);
                    returnSampleItems(player, topInv, 28);
                    returnSampleItems(player, topInv, 29);
                }
            }
            case GIFT_PACK_EDIT -> {
                // 礼包编辑：关闭时自动保存内容物（收纳袋超容量的物品退还）
                GiftPack pack = resolvePack(state, npc);
                if (npc != null && pack != null) {
                    saveGiftPackContents(player, pack, topInv);
                }
            }
            default -> { }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();
        // 商人界面还开着时清除门票，防止退出时掉落被他人拾取
        clearMerchantTickets(player);
        merchantSessions.remove(uuid);
        pendingMessage.remove(uuid);
        pendingTeleportCost.remove(uuid);
        pendingCurrencyTrade.remove(uuid);
        pendingCurrencyResult.remove(uuid);
        pendingRename.remove(uuid);
    }

    // ===== 主界面点击处理 =====

    private void handleMainClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;

        switch (slot) {
            case 19: // 姿势调整
                openPoseSelectGui(player, npc);
                break;
            case 20: // 大小调整
                openSizeAdjustGui(player, npc);
                break;
            case 21: // 移动NPC
                player.closeInventory();
                player.sendMessage(PhilosNPCPlugin.cc("&a走到目标位置，然后输入："));
                player.sendMessage(PhilosNPCPlugin.cc("&e/pnpc move " + npc.getId()));
                player.sendMessage(PhilosNPCPlugin.cc("&7点击上方命令可直接复制"));
                break;
            case 22: // 删除NPC
                player.closeInventory();
                if (npcManager.deleteNPC(npc.getId())) {
                    player.sendMessage(PhilosNPCPlugin.cc("&cNPC已删除"));
                }
                break;
            case 23: // 装备编辑
                openEquipmentGui(player, npc);
                break;
            case 24: // 传送至NPC
                handleTeleportToNPC(player, npc);
                break;
            case 28: // 修改名字
                player.closeInventory();
                player.sendMessage(PhilosNPCPlugin.cc("&a在聊天输入新名字，输入 &ccancel &a取消"));
                pendingRename.put(player.getUniqueId(), npc.getId());
                break;
            case 31: // 已启用功能标题
                break;
            case 45: // 返回列表
                openNPCListGui(player, 0);
                break;
            case 49: // 关闭
                player.closeInventory();
                break;
            default: {
                // 功能槽位（36-44行居中排列，数量随配置上限）
                int[] featureSlots = NPCGui.mainFeatureSlots(npc);
                for (int i = 0; i < featureSlots.length; i++) {
                    if (slot == featureSlots[i]) {
                        handleFeatureSlotClick(player, npc, i);
                        return;
                    }
                }
                break;
            }
        }
    }

    private void handleTeleportToNPC(Player player, PhilosNPC npc) {
        if (PhilosNPCPlugin.economy() != null && PluginSettings.tpToNpcCost() > 0) {
            EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, PluginSettings.tpToNpcCost());
            if (!resp.transactionSuccess()) {
                player.sendMessage(PhilosNPCPlugin.cc("&c金币不足！传送需要 " + PluginSettings.tpToNpcCost() + " 金币"));
                return;
            }
        }
        player.teleport(npc.getLocation());
        player.sendMessage(PhilosNPCPlugin.cc("&a已传送到NPC"
                + (PluginSettings.tpToNpcCost() > 0 && PhilosNPCPlugin.economy() != null ? "，花费 " + PluginSettings.tpToNpcCost() + " 金币" : "")));
        player.closeInventory();
    }

    private void handleFeatureSlotClick(Player player, PhilosNPC npc, int featureIndex) {
        if (featureIndex < npc.getFeatures().size()) {
            // 已启用的功能 - 打开对应编辑界面
            FeatureType feature = npc.getFeatures().get(featureIndex);
            switch (feature) {
                case SHOP:
                    openShopEditGui(player, npc);
                    break;
                case TELEPORT:
                    openTpSettingsGui(player, npc);
                    break;
                case MESSAGE:
                    openMessageEditGui(player, npc);
                    break;
                case GIFT_PACK:
                    openGiftPackListGui(player, npc, 0);
                    break;
            }
        } else {
            // 空槽位 - 打开功能选择界面
            openFeatureSelectGui(player, npc);
        }
    }

    // ===== 功能选择界面点击处理 =====

    private void handleFeatureSelectClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;

        if (slot == 18) { // 返回按钮
            openMainGui(player, npc);
            return;
        }

        FeatureType[] features = FeatureType.values();
        // 功能按钮按数量在第2行(9-17)居中排列
        int[] slots = NPCGui.centeredRowSlots(features.length);
        int featureIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                featureIndex = i;
                break;
            }
        }

        if (featureIndex >= 0 && featureIndex < features.length) {
            FeatureType feature = features[featureIndex];
            // 礼包功能仅系统NPC可添加
            if (feature == FeatureType.GIFT_PACK && !npc.isSystem()) {
                player.sendMessage(PhilosNPCPlugin.cc("&c礼包功能仅系统NPC可添加"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                openMainGui(player, npc);
                return;
            }
            if (!npc.hasFeature(feature)) {
                // 添加功能收费：个人NPC扣费，系统NPC免费（金额见 config.yml）
                if (!npc.isSystem() && PhilosNPCPlugin.economy() != null && PluginSettings.featureAddCost() > 0) {
                    EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, PluginSettings.featureAddCost());
                    if (!resp.transactionSuccess()) {
                        player.sendMessage(PhilosNPCPlugin.cc("&c金币不足！添加功能需要 " + PluginSettings.featureAddCost() + " 金币"));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                }
                if (npc.addFeature(feature)) {
                    npcManager.saveAll();
                    if (!npc.isSystem() && PluginSettings.featureAddCost() > 0) {
                        player.sendMessage(PhilosNPCPlugin.cc("&a已添加功能: " + feature.displayName()
                                + "&a，花费 &6" + PluginSettings.featureAddCost() + " 金币"));
                    } else {
                        player.sendMessage(PhilosNPCPlugin.cc("&a已添加功能: " + feature.displayName()));
                    }
                } else {
                    // 上限已满时退还刚扣的费用
                    if (!npc.isSystem() && PhilosNPCPlugin.economy() != null && PluginSettings.featureAddCost() > 0) {
                        PhilosNPCPlugin.economy().depositPlayer(player, PluginSettings.featureAddCost());
                    }
                    player.sendMessage(PhilosNPCPlugin.cc("&c功能已达上限"));
                }
            }
            openMainGui(player, npc);
        }
    }

    // ===== 姿势选择界面点击处理 =====

    private void handlePoseSelectClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;

        if (slot == 22) { // 返回按钮
            openMainGui(player, npc);
            return;
        }

        NPCPose[] poses = NPCPose.values();
        // 2种姿势：slot 10（站立）、12（坐着）
        int poseIndex = -1;
        if (slot == 10) {
            poseIndex = 0;
        } else if (slot == 12) {
            poseIndex = 1;
        }
        if (poseIndex >= 0 && poseIndex < poses.length) {
            npc.setPose(poses[poseIndex]);
            npcManager.saveAll();
            // 重新生成NPC以应用新姿势
            npcManager.despawnNPC(npc);
            npcManager.spawnNPC(npc);
            player.sendMessage(PhilosNPCPlugin.cc("&a姿势已设为: " + poses[poseIndex].displayName()));
            openPoseSelectGui(player, npc);
        }
    }

    // ===== 大小调整界面点击处理 =====

    private void handleSizeAdjustClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;

        if (slot == 18) { // 返回按钮
            openMainGui(player, npc);
            return;
        }

        double currentScale = npc.getScale();
        boolean changed = false;

        switch (slot) {
            case 11: // 缩小
                if (currentScale > 0.2) {
                    npc.setScale(Math.max(0.2, currentScale - 0.1));
                    changed = true;
                }
                break;
            case 15: // 放大
                if (currentScale < 5.0) {
                    npc.setScale(Math.min(5.0, currentScale + 0.1));
                    changed = true;
                }
                break;
        }

        if (changed) {
            npcManager.saveAll();
            // 重新生成NPC以应用新大小
            npcManager.despawnNPC(npc);
            npcManager.spawnNPC(npc);
            openSizeAdjustGui(player, npc);
        }
    }

    // ===== 装备编辑点击处理 =====

    private void handleEquipmentClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;

        // 检查是否点击的是装备槽（可交互，不处理按钮逻辑）
        for (int s : EQUIPMENT_SLOTS) {
            if (slot == s) return;
        }

        switch (slot) {
            case 36: // 返回（关闭时自动保存）
                openMainGui(player, npc);
                break;
            case 40: // 保存（关闭时自动保存）
                player.sendMessage(PhilosNPCPlugin.cc("&a装备已保存，NPC外观已更新"));
                openMainGui(player, npc);
                break;
        }
    }

    private void saveEquipmentFromInventory(PhilosNPC npc, Inventory inv) {
        ItemStack[] equipment = npc.getEquipment();
        int[] slots = {10, 19, 28, 37, 24}; // 头盔, 胸甲, 护腿, 靴子, 主手
        for (int i = 0; i < slots.length; i++) {
            ItemStack item = inv.getItem(slots[i]);
            equipment[i] = (item != null && !item.getType().isAir() && !isPlaceholder(item))
                    ? item.clone() : null;
        }

        npcManager.saveAll();
        // 重新生成NPC以应用新装备
        npcManager.respawnNPC(npc);
    }

    // ===== 商店编辑点击处理 =====

    private void handleShopEditClick(Player player, PhilosNPC npc, int slot, GuiState state) {
        if (npc == null) return;
        Inventory guiInv = state.getInventory();
        if (guiInv == null) return;
        int invSize = guiInv.getSize();

        // 交易配方编辑界面（54格）
        if (invSize == 54) {
            handleTradeEditClick(player, npc, slot, state);
            return;
        }

        // 商店背包编辑界面（45格，个人NPC）
        switch (slot) {
            case 36: // 返回（关闭时自动保存）
                openMainGui(player, npc);
                break;
            case 38: // 交易配方
                openTradeEditGui(player, npc, 0);
                break;
            case 40: // 保存并关闭（关闭时自动保存）
                player.sendMessage(PhilosNPCPlugin.cc("&a商店背包已保存"));
                openMainGui(player, npc);
                break;
            case 42: // 收购背包（物物交易收入）
                openCollectionBackpackGui(player, npc, 0);
                break;
        }
    }

    // ===== 交易配方编辑界面点击处理 =====

    private void handleTradeEditClick(Player player, PhilosNPC npc, int slot, GuiState state) {
        int page = state.getPage();

        // slot 0-8: 删除交易配方
        if (slot >= 0 && slot < 9) {
            int tradeIndex = page * 9 + slot;
            if (tradeIndex < npc.getTrades().size()) {
                npc.getTrades().remove(tradeIndex);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&c交易已删除"));
                openTradeEditGui(player, npc, page);
            }
            return;
        }

        switch (slot) {
            case 30: // 添加交易
                handleAddTradeClick(player, npc, state);
                break;
            case 31: // 交易模式开关（物品交易/金币交易）
                toggleTradeMode(player, npc, state);
                break;
            case 36: // 上一页
                if (page > 0) openTradeEditGui(player, npc, page - 1);
                break;
            case 40: // 下一页
                openTradeEditGui(player, npc, page + 1);
                break;
            case 44: // 返回
                if (npc.isSystem()) {
                    openMainGui(player, npc);
                } else {
                    openShopEditGui(player, npc); // 返回商店背包
                }
                break;
        }
    }

    private void handleAddTradeClick(Player player, PhilosNPC npc, GuiState state) {
        var inv = state.getInventory();
        if (inv == null) return;

        // 物品交易（以物换物）：读取价格槽27/28与产出槽29，直接完成添加
        if (npc.isShopItemTradeMode()) {
            var price1Item = inv.getItem(27);
            var price2Item = inv.getItem(28);
            var slot29Item = inv.getItem(29);

            if (slot29Item == null || slot29Item.getType().isAir() || isPlaceholder(slot29Item)) {
                player.sendMessage(PhilosNPCPlugin.cc("&c请先将产出物品放入槽位29"));
                return;
            }
            if (price1Item == null || price1Item.getType().isAir() || isPlaceholder(price1Item)) {
                player.sendMessage(PhilosNPCPlugin.cc("&c请将价格物品放入槽位27（槽位28可选）"));
                return;
            }

            ItemStack result = slot29Item.clone();
            ItemStack price1 = price1Item.clone();
            ItemStack price2 = null;
            if (price2Item != null && !price2Item.getType().isAir() && !isPlaceholder(price2Item)) {
                price2 = price2Item.clone();
            }

            // 返还样品物品，避免重开界面时丢失
            returnSampleItems(player, inv, 27);
            returnSampleItems(player, inv, 28);
            returnSampleItems(player, inv, 29);

            npc.getTrades().add(new ShopTrade(result, price1, price2, -1));
            npcManager.saveAll();
            player.sendMessage(PhilosNPCPlugin.cc("&a物物交易已添加（以物换物）"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            openTradeEditGui(player, npc, state.getPage());
            return;
        }

        // 金币交易：读取产出物品并转入聊天输入价格
        var slot29Item = inv.getItem(29);

        if (slot29Item == null || slot29Item.getType().isAir() || isPlaceholder(slot29Item)) {
            player.sendMessage(PhilosNPCPlugin.cc("&c请先将产出物品放入槽位29"));
            return;
        }

        ItemStack result = slot29Item.clone();

        // 金币交易：返还样品并转入聊天输入价格
        returnSampleItems(player, inv, 29);
        player.closeInventory();
        player.sendMessage(PhilosNPCPlugin.cc("&a在聊天输入出售价格（金币数），输入 &ccancel &a取消"));
        pendingCurrencyResult.put(player.getUniqueId(), result);
        pendingCurrencyTrade.put(player.getUniqueId(), npc.getId());
    }

    /**
     * 切换交易模式：物品交易(以物换物) <-> 金币交易。
     * 切换前返还槽内样品物品，避免重开界面时丢失。
     */
    private void toggleTradeMode(Player player, PhilosNPC npc, GuiState state) {
        Inventory inv = state.getInventory();
        if (inv != null) {
            returnSampleItems(player, inv, 27);
            returnSampleItems(player, inv, 28);
            returnSampleItems(player, inv, 29);
        }
        boolean itemMode = !npc.isShopItemTradeMode();
        npc.setShopItemTradeMode(itemMode);
        npcManager.saveAll();
        player.sendMessage(PhilosNPCPlugin.cc(itemMode
                ? "&a交易模式已切换为：&b物品交易（以物换物）"
                : "&a交易模式已切换为：&e金币交易"));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        openTradeEditGui(player, npc, state.getPage());
    }

    /**
     * 返还编辑槽中的样品物品给玩家（避免物品被销毁）
     */
    private void returnSampleItems(Player player, Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        inv.setItem(slot, null);
        if (item == null || item.getType().isAir() || isPlaceholder(item)) return;
        giveResult(player, item);
    }

    // ===== 传送设置点击处理 =====

    private void handleTpSettingsClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;
        switch (slot) {
            case 19: // 设为当前位置
                TeleportFeature.setupTeleport(player, npc);
                npcManager.saveAll();
                openTpSettingsGui(player, npc);
                break;
            case 22: // 传送价格（系统NPC可自定义）
                if (npc.isSystem()) {
                    player.closeInventory();
                    player.sendMessage(PhilosNPCPlugin.cc("&a聊天框输入传送价格，输入cancel取消"));
                    player.sendMessage(PhilosNPCPlugin.cc("&70=免费，-1=恢复默认(5金币)"));
                    pendingTeleportCost.put(player.getUniqueId(), npc.getId());
                }
                break;
            case 27: // 返回
                openMainGui(player, npc);
                break;
            case 31: // 移除功能
                npc.removeFeature(FeatureType.TELEPORT);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&c已移除传送功能"));
                openMainGui(player, npc);
                break;
        }
    }

    // ===== 留言编辑点击处理 =====

    private void handleMessageEditClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;
        switch (slot) {
            case 11: // 修改留言
                player.closeInventory();
                player.sendMessage(PhilosNPCPlugin.cc("&a请在聊天框输入留言内容（输入 &ccancel &a取消）："));
                player.sendMessage(PhilosNPCPlugin.cc("&7支持 &b/n &7换行"));
                pendingMessage.put(player.getUniqueId(), npc.getId());
                break;
            case 15: // 清除留言
                MessageFeature.setMessage(npc, "");
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&a已清除"));
                openMessageEditGui(player, npc);
                break;
            case 18: // 返回
                openMainGui(player, npc);
                break;
            case 22: // 移除功能
                npc.removeFeature(FeatureType.MESSAGE);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&c留言功能已移除"));
                openMainGui(player, npc);
                break;
        }
    }

    // ===== 顾客界面点击处理 =====

    private void handleCustomerClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;
        if (slot == 18) { // 关闭
            player.closeInventory();
            return;
        }
        // 功能按钮按数量在第2行(9-17)居中排列
        var features = npc.getFeatures();
        int[] slots = NPCGui.centeredRowSlots(features.size());
        int featureIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                featureIndex = i;
                break;
            }
        }
        if (featureIndex >= 0 && featureIndex < features.size()) {
            var feature = features.get(featureIndex);
            switch (feature) {
                case SHOP -> {
                    openMerchantShop(player, npc);
                }
                case TELEPORT -> {
                    TeleportFeature.executeTeleport(player, npc);
                    player.closeInventory();
                }
                case MESSAGE -> {
                    MessageFeature.showMessage(player, npc);
                    player.closeInventory();
                }
                case GIFT_PACK -> {
                    openGiftPackClaimGui(player, npc, 0);
                }
            }
        }
    }

    // ===== 礼包发放点击处理 =====

    /**
     * 从GuiState解析当前编辑的礼包
     */
    private GiftPack resolvePack(GuiState state, PhilosNPC npc) {
        if (state == null || npc == null) return null;
        String packId = (String) state.getData().get("packId");
        return packId != null ? npc.getGiftPack(packId) : null;
    }

    private void handleGiftPackListClick(Player player, PhilosNPC npc, int slot, GuiState state) {
        if (npc == null) return;
        int page = state.getPage();

        switch (slot) {
            case 45: // 上一页
                if (page > 0) openGiftPackListGui(player, npc, page - 1);
                return;
            case 47: // 移除功能（礼包数据保留，重新添加功能后恢复）
                npc.removeFeature(FeatureType.GIFT_PACK);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&c已移除礼包发放功能（礼包数据已保留）"));
                openMainGui(player, npc);
                return;
            case 49: { // 新建礼包
                GiftPack pack = new GiftPack(UUID.randomUUID().toString().substring(0, 8));
                npc.getGiftPacks().add(pack);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&a已创建新礼包，请编辑名字与内容物"));
                openGiftPackEditGui(player, npc, pack);
                return;
            }
            case 50: // 返回主界面
                openMainGui(player, npc);
                return;
            case 53: { // 下一页
                int totalPages = Math.max(1, (npc.getGiftPacks().size() + 27) / 28);
                if (page < totalPages - 1) openGiftPackListGui(player, npc, page + 1);
                return;
            }
            default: {
                GiftPack pack = GiftPackGui.packAtSlot(npc.getGiftPacks(), slot, page);
                if (pack != null) openGiftPackEditGui(player, npc, pack);
            }
        }
    }

    private void handleGiftPackEditClick(Player player, PhilosNPC npc, int slot, GuiState state) {
        if (npc == null) return;
        GiftPack pack = resolvePack(state, npc);
        if (pack == null) {
            openGiftPackListGui(player, npc, 0);
            return;
        }

        // 内容物槽位（18-44）为可交互槽，不走按钮逻辑
        if (slot >= 18 && slot <= 44) return;

        switch (slot) {
            case 4: // 修改名字（聊天输入，关闭时已自动保存内容物）
                player.closeInventory();
                player.sendMessage(PhilosNPCPlugin.cc("&a在聊天输入礼包名字，输入 &ccancel &a取消"));
                pendingPackRename.put(player.getUniqueId(), npc.getId() + "|" + pack.getId());
                break;
            case 6: // 容器类型与颜色
                openGiftPackContainerGui(player, npc, pack);
                break;
            case 45: // 返回列表（关闭时自动保存）
                openGiftPackListGui(player, npc, 0);
                break;
            case 49: { // 删除礼包（内容物退还）
                Inventory guiInv = state.getInventory();
                if (guiInv != null) {
                    for (int i = 18; i <= 44; i++) {
                        ItemStack item = guiInv.getItem(i);
                        guiInv.setItem(i, null);
                        if (item != null && !item.getType().isAir() && !isPlaceholder(item)) {
                            giveResult(player, item);
                        }
                    }
                }
                npc.getGiftPacks().remove(pack);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&c礼包已删除，内容物已退还给你"));
                openGiftPackListGui(player, npc, 0);
                break;
            }
            case 53: // 保存（关闭时自动保存）
                player.sendMessage(PhilosNPCPlugin.cc("&a礼包已保存"));
                openGiftPackEditGui(player, npc, pack);
                break;
        }
    }

    private void handleGiftPackContainerClick(Player player, PhilosNPC npc, int slot, GuiState state) {
        if (npc == null) return;
        GiftPack pack = resolvePack(state, npc);
        if (pack == null) {
            openGiftPackListGui(player, npc, 0);
            return;
        }

        DyeColor[] colors = DyeColor.values();
        int colorCount = Math.min(16, colors.length);
        String choice = null;

        if (slot >= 0 && slot < colorCount) {
            // 收纳袋
            pack.setShulker(false);
            pack.setColor(colors[slot].name());
            choice = colors[slot].name() + "收纳袋";
        } else if (slot >= 18 && slot < 18 + colorCount) {
            // 潜影盒
            pack.setShulker(true);
            pack.setColor(colors[slot - 18].name());
            choice = colors[slot - 18].name() + "潜影盒";
        } else if (slot == 40) {
            // 返回
            openGiftPackEditGui(player, npc, pack);
            return;
        } else {
            return;
        }

        npcManager.saveAll();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
        player.sendMessage(PhilosNPCPlugin.cc("&a容器已设为: &f" + choice));
        openGiftPackEditGui(player, npc, pack);
    }

    private void handleGiftPackClaimClick(Player player, PhilosNPC npc, int slot, GuiState state) {
        if (npc == null) return;
        int page = state.getPage();

        if (slot == 49) { // 关闭
            player.closeInventory();
            return;
        }
        if (slot == 45 && page > 0) { // 上一页
            openGiftPackClaimGui(player, npc, page - 1);
            return;
        }

        // 与claimGui一致的可视列表（普通玩家过滤已领取）
        boolean admin = player.hasPermission("philosnpc.admin");
        List<GiftPack> visible = new ArrayList<>();
        for (GiftPack pack : npc.getGiftPacks()) {
            if (admin || !pack.isClaimedBy(player.getUniqueId())) {
                visible.add(pack);
            }
        }

        if (slot == 53) { // 下一页
            int totalPages = Math.max(1, (visible.size() + 27) / 28);
            if (page < totalPages - 1) openGiftPackClaimGui(player, npc, page + 1);
            return;
        }

        GiftPack pack = GiftPackGui.packAtSlot(visible, slot, page);
        if (pack == null) return;

        if (GiftPackFeature.claim(player, pack)) {
            npcManager.saveAll();
        }
        openGiftPackClaimGui(player, npc, page);
    }

    /**
     * 保存礼包编辑界面中的内容物（关闭/返回时调用）。
     * 收纳袋容量为16组（1.21.2+按组计，与堆叠数无关），超出部分退还给玩家。
     */
    private void saveGiftPackContents(Player player, GiftPack pack, Inventory inv) {
        List<ItemStack> contents = new ArrayList<>();
        boolean overflow = false;
        int stacks = 0;

        for (int i = 18; i <= 44; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir() || isPlaceholder(item)) continue;

            if (!pack.isShulker()) {
                if (stacks >= 16) {
                    giveResult(player, item);
                    overflow = true;
                    continue;
                }
                stacks++;
            }
            contents.add(item.clone());
        }

        pack.setContents(contents);
        npcManager.saveAll();
        if (overflow) {
            player.sendMessage(PhilosNPCPlugin.cc("&c部分物品超出收纳袋容量，已退还给你"));
        }
    }

    // ===== NPC列表点击处理 =====

    private void handleNpcListClick(Player player, int slot, GuiState state) {
        boolean systemList = Boolean.TRUE.equals(state.getData().get("systemList"));
        int page = state.getPage();

        List<PhilosNPC> npcs = systemList
                ? npcManager.getSystemNPCs()
                : npcManager.getNPCsByOwner(player.getUniqueId());

        if (slot == 45 && page > 0) {
            openNPCListGui(player, page - 1, systemList);
            return;
        }
        if (slot == 53 && page < Math.max(1, (npcs.size() + 27) / 28) - 1) {
            openNPCListGui(player, page + 1, systemList);
            return;
        }
        if (slot == 50) {
            player.closeInventory();
            return;
        }

        // 计算点击的NPC索引
        int perPage = 28;
        int start = page * perPage;

        // NPC槽位分布：slot 10-16, 19-25, 28-34, 37-43 (4行x7列)
        int row = (slot / 9) - 1; // 第1-4行对应row 0-3
        int col = slot % 9 - 1;   // 第1-7列对应col 0-6

        if (row >= 0 && row < 4 && col >= 0 && col < 7) {
            int indexInPage = row * 7 + col;
            int npcIndex = start + indexInPage;
            if (npcIndex < npcs.size()) {
                PhilosNPC npc = npcs.get(npcIndex);
                openMainGui(player, npc);
            }
        }
    }

    // ===== 聊天输入处理（留言、传送价格、金币交易价格） =====

    private final Map<UUID, String> pendingMessage = new HashMap<>();
    private final Map<UUID, String> pendingTeleportCost = new HashMap<>();
    private final Map<UUID, String> pendingCurrencyTrade = new HashMap<>();
    private final Map<UUID, ItemStack> pendingCurrencyResult = new HashMap<>();
    private final Map<UUID, String> pendingRename = new HashMap<>();
    private final Map<UUID, String> pendingPackRename = new HashMap<>();

    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        var player = event.getPlayer();
        String msg = event.getMessage();

        if (pendingPackRename.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String key = pendingPackRename.remove(player.getUniqueId());
            String[] parts = key.split("\\|", 2);
            PhilosNPC npc = parts.length == 2 ? npcManager.getNPC(parts[0]) : null;
            GiftPack pack = npc != null ? npc.getGiftPack(parts[1]) : null;
            if (npc == null || pack == null) return;

            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(PhilosNPCPlugin.cc("&c已取消改名"));
                return;
            }
            String name = msg.trim();
            if (name.isEmpty() || name.length() > 32) {
                pendingPackRename.put(player.getUniqueId(), key);
                player.sendMessage(PhilosNPCPlugin.cc("&c名字长度需为1-32个字符，请重新输入（或输入cancel取消）"));
                return;
            }
            // 异步线程不能修改NPC数据/打开GUI，回到主线程执行
            Bukkit.getScheduler().runTask(plugin, () -> {
                pack.setName(name);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&a礼包名字已修改为: &d" + name));
                openGiftPackEditGui(player, npc, pack);
            });
            return;
        }

        if (pendingCurrencyTrade.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String npcId = pendingCurrencyTrade.remove(player.getUniqueId());
            var npc = npcManager.getNPC(npcId);
            if (npc == null) {
                pendingCurrencyResult.remove(player.getUniqueId());
                return;
            }

            if (msg.equalsIgnoreCase("cancel")) {
                pendingCurrencyResult.remove(player.getUniqueId());
                player.sendMessage(PhilosNPCPlugin.cc("&c已取消添加交易"));
                return;
            }
            double price;
            try {
                price = Double.parseDouble(msg);
            } catch (NumberFormatException e) {
                // 放回会话让玩家重新输入，避免样品物品丢失
                pendingCurrencyTrade.put(player.getUniqueId(), npcId);
                player.sendMessage(PhilosNPCPlugin.cc("&c无效的数字格式，请重新输入（或输入cancel取消）"));
                return;
            }
            if (price <= 0) {
                pendingCurrencyTrade.put(player.getUniqueId(), npcId);
                player.sendMessage(PhilosNPCPlugin.cc("&c价格需大于0，请重新输入（或输入cancel取消）"));
                return;
            }
            ItemStack result = pendingCurrencyResult.remove(player.getUniqueId());
            if (result == null) {
                player.sendMessage(PhilosNPCPlugin.cc("&c产出物品丢失，重新操作"));
                return;
            }
            // 异步线程不能修改NPC数据/打开GUI，回到主线程执行
            Bukkit.getScheduler().runTask(plugin, () -> {
                npc.getTrades().add(new ShopTrade(result, price, -1));
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&a金币交易已添加: &6" + price + " 金币"));
                openTradeEditGui(player, npc, 0);
            });
            return;
        }

        if (pendingTeleportCost.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String npcId = pendingTeleportCost.remove(player.getUniqueId());
            var npc = npcManager.getNPC(npcId);
            if (npc == null) return;

            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(PhilosNPCPlugin.cc("&c已取消设置"));
                return;
            }
            double cost;
            try {
                cost = Double.parseDouble(msg);
            } catch (NumberFormatException e) {
                player.sendMessage(PhilosNPCPlugin.cc("&c无效的数字格式"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                npc.setCustomTeleportCost(cost);
                npcManager.saveAll();
                if (cost < 0) {
                    player.sendMessage(PhilosNPCPlugin.cc("&a已恢复默认传送价格"));
                } else if (cost == 0) {
                    player.sendMessage(PhilosNPCPlugin.cc("&a传送已设为免费"));
                } else {
                    player.sendMessage(PhilosNPCPlugin.cc("&a传送价格已设为: &6" + cost + " 金币"));
                }
            });
            return;
        }

        if (pendingRename.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String npcId = pendingRename.remove(player.getUniqueId());
            var npc = npcManager.getNPC(npcId);
            if (npc == null) return;

            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(PhilosNPCPlugin.cc("&c已取消改名"));
                return;
            }
            String name = msg.trim();
            if (name.isEmpty() || name.length() > 32) {
                pendingRename.put(player.getUniqueId(), npcId);
                player.sendMessage(PhilosNPCPlugin.cc("&c名字长度需为1-32个字符，请重新输入（或输入cancel取消）"));
                return;
            }
            // 异步线程不能修改NPC数据，回到主线程执行
            Bukkit.getScheduler().runTask(plugin, () -> {
                npc.setDisplayName(name);
                npcManager.saveAll();
                // 重新生成NPC以更新头顶名字
                npcManager.despawnNPC(npc);
                npcManager.spawnNPC(npc);
                player.sendMessage(PhilosNPCPlugin.cc("&a名字已修改为: &f" + name));
            });
            return;
        }

        if (pendingMessage.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String npcId = pendingMessage.remove(player.getUniqueId());
            var npc = npcManager.getNPC(npcId);
            if (npc == null) return;

            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(PhilosNPCPlugin.cc("&c已取消"));
                return;
            }
            String content = msg;
            Bukkit.getScheduler().runTask(plugin, () -> {
                MessageFeature.setMessage(npc, content);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&a留言已保存"));
            });
        }
    }

    // ===== 辅助方法 =====

    private void fillEmpty(Inventory inv) {
        var glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = glass.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); glass.setItemMeta(meta); }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    // ===== 获取状态 =====

    public GuiState getGuiState(Player player) {
        var top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof GuiState) {
            return (GuiState) top.getHolder();
        }
        return null;
    }

    // ===== GuiState 内部类 =====

    public static class GuiState implements InventoryHolder {

        public enum Screen {
            MAIN,
            FEATURE_SELECT,
            POSE_SELECT,
            SIZE_ADJUST,
            EQUIPMENT_EDIT,
            SHOP_EDIT,
            TP_SETTINGS,
            MESSAGE_EDIT,
            CUSTOMER,
            NPC_LIST,
            GIFT_PACK_LIST,
            GIFT_PACK_EDIT,
            GIFT_PACK_CONTAINER,
            GIFT_PACK_CLAIM,
            COLLECTION_BACKPACK
        }

        private final Screen screen;
        private final String npcId;
        private final int page;
        private final Map<String, Object> data;
        private Inventory inventory;

        public GuiState(Screen screen, String npcId, int page, Map<String, Object> data) {
            this.screen = screen;
            this.npcId = npcId;
            this.page = page;
            this.data = data;
        }

        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inv) { this.inventory = inv; }

        public Screen getScreen() { return screen; }
        public String getNpcId() { return npcId; }
        public int getPage() { return page; }
        public Map<String, Object> getData() { return data; }
    }
}
