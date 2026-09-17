package com.phcraft.philosnpc.gui;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.features.FeatureGuiFactory;
import com.phcraft.philosnpc.features.JukeboxFeature;
import com.phcraft.philosnpc.features.MessageFeature;
import com.phcraft.philosnpc.features.ShopGui;
import com.phcraft.philosnpc.features.ShopTrade;
import com.phcraft.philosnpc.features.TeleportFeature;
import com.phcraft.philosnpc.npc.FeatureType;
import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.npc.NPCPose;
import com.phcraft.philosnpc.npc.PhilosNPC;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
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

    public void openTpSettingsGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.TP_SETTINGS, npc.getId(), 0, new HashMap<>());
        Inventory inv = FeatureGuiFactory.teleportSettingsGui(npc, state);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    public void openJukeboxEditGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.JUKEBOX_EDIT, npc.getId(), 0, new HashMap<>());
        Inventory inv = FeatureGuiFactory.jukeboxEditGui(npc, state);
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
                    EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, price);
                    if (!resp.transactionSuccess()) {
                        player.sendMessage(PhilosNPCPlugin.cc("&c金币不足"));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        return;
                    }
                    // 个人NPC：货款付给NPC主人（自购时钱不过手，净零流动）
                    if (!isSystem && !selfPurchase && ownerUuid != null) {
                        PhilosNPCPlugin.economy().depositPlayer(Bukkit.getOfflinePlayer(ownerUuid), price);
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
                    player.sendMessage(PhilosNPCPlugin.cc("&a购买成功，花费 " + price + " 金币"));
                } else {
                    // 物物交换（旧数据兼容）：校验商人槽位中的价格物品
                    if (!ingredientReady(mi.getItem(0), trade.getPrice1())
                            || !ingredientReady(mi.getItem(1), trade.getPrice2())) {
                        return; // 价格物品未放齐，静默忽略
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
                    // 个人NPC：从共享商店背包扣除产出物品
                    if (!session.npc.isSystem()) {
                        ItemStack[] shopInv = npcManager.getSharedShopInventory(session.npc.getOwnerUuid());
                        removeFromArray(shopInv, trade.getResult());
                        npcManager.setSharedShopInventory(session.npc.getOwnerUuid(), shopInv);
                    }
                    trade.incrementUses();
                    npcManager.saveAll();
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                    player.sendMessage(PhilosNPCPlugin.cc("&a交易完成"));
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
                case JUKEBOX_EDIT:
                    handleJukeboxEditClick(player, npc, slot);
                    break;
                case MESSAGE_EDIT:
                    handleMessageEditClick(player, npc, slot);
                    break;
                case CUSTOMER:
                    handleCustomerClick(player, npc, slot);
                    break;
                case JUKEBOX_SELECT:
                    handleJukeboxSelectClick(player, npc, slot);
                    break;
                case NPC_LIST:
                    handleNpcListClick(player, slot, state);
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
                // 交易配方编辑（54格）：统一金币交易，仅产出槽29可交互
                return slot == 29;
            }
            case JUKEBOX_EDIT: {
                // 点歌台编辑：9-17唱片槽可交互
                return slot >= 9 && slot <= 17;
            }
            case EQUIPMENT_EDIT: {
                // 装备编辑：5个装备槽可交互
                for (int s : EQUIPMENT_SLOTS) {
                    if (slot == s) return true;
                }
                return false;
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
            case JUKEBOX_EDIT -> {
                // 关闭时自动保存唱片
                if (npc != null) {
                    var discs = npc.getJukeboxDiscs();
                    for (int i = 0; i < 9; i++) {
                        ItemStack item = topInv.getItem(9 + i);
                        discs[i] = (item != null && !item.getType().isAir() && !isPlaceholder(item))
                                ? item.clone() : null;
                    }
                    npcManager.saveAll();
                }
            }
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
                player.sendMessage(PhilosNPCPlugin.cc("&a请走到目标位置，然后输入 /philosnpc move " + npc.getId()));
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
            case 31: // 已启用功能标题
                break;
            case 38:
            case 39:
            case 40:
            case 41:
                handleFeatureSlotClick(player, npc, slot - 38);
                break;
            case 45: // 返回列表
                openNPCListGui(player, 0);
                break;
            case 49: // 关闭
                player.closeInventory();
                break;
        }
    }

    private void handleTeleportToNPC(Player player, PhilosNPC npc) {
        if (PhilosNPCPlugin.economy() != null) {
            EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, PhilosNPCPlugin.TP_TO_NPC_COST);
            if (!resp.transactionSuccess()) {
                player.sendMessage(PhilosNPCPlugin.cc("&c金币不足！传送需要 " + PhilosNPCPlugin.TP_TO_NPC_COST + " 金币"));
                return;
            }
        }
        player.teleport(npc.getLocation());
        player.sendMessage(PhilosNPCPlugin.cc("&a已传送到NPC，花费 " + PhilosNPCPlugin.TP_TO_NPC_COST + " 金币"));
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
                case JUKEBOX:
                    openJukeboxEditGui(player, npc);
                    break;
                case MESSAGE:
                    openMessageEditGui(player, npc);
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
        // slot 10, 12, 14, 16 对应 4 个功能
        int featureIndex = -1;
        switch (slot) {
            case 10: featureIndex = 0; break;
            case 12: featureIndex = 1; break;
            case 14: featureIndex = 2; break;
            case 16: featureIndex = 3; break;
        }

        if (featureIndex >= 0 && featureIndex < features.length) {
            FeatureType feature = features[featureIndex];
            if (!npc.hasFeature(feature)) {
                if (npc.addFeature(feature)) {
                    npcManager.saveAll();
                    player.sendMessage(PhilosNPCPlugin.cc("&a已添加功能: " + feature.displayName()));
                } else {
                    player.sendMessage(PhilosNPCPlugin.cc("&c功能已达上限"));
                }
            }
            openMainGui(player, npc);
        }
    }

    // ===== 姿势选择界面点击处理 =====

    private void handlePoseSelectClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;

        if (slot == 18) { // 返回按钮
            openMainGui(player, npc);
            return;
        }

        NPCPose[] poses = NPCPose.values();
        // 5种姿势，slot 9-13
        int poseIndex = slot - 9;
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
                if (currentScale > 0.5) {
                    npc.setScale(Math.max(0.5, currentScale - 0.1));
                    changed = true;
                }
                break;
            case 15: // 放大
                if (currentScale < 2.0) {
                    npc.setScale(Math.min(2.0, currentScale + 0.1));
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
        var slot29Item = inv.getItem(29);

        if (slot29Item == null || slot29Item.getType().isAir() || isPlaceholder(slot29Item)) {
            player.sendMessage(PhilosNPCPlugin.cc("&c请先将产出物品放入槽位29"));
            return;
        }

        ItemStack result = slot29Item.clone();

        // 金币交易：返还样品并转入聊天输入价格（统一模式，个人/系统NPC一致）
        returnSampleItems(player, inv, 29);
        player.closeInventory();
        player.sendMessage(PhilosNPCPlugin.cc("&a请在聊天框输入金币价格（输入 &ccancel &a取消）："));
        pendingCurrencyResult.put(player.getUniqueId(), result);
        pendingCurrencyTrade.put(player.getUniqueId(), npc.getId());
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

    // ===== 点歌编辑点击处理 =====

    private void handleJukeboxEditClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;
        if (slot >= 9 && slot <= 17) {
            // 可交互槽位，不处理
            return;
        }
        switch (slot) {
            case 27: // 返回（关闭时自动保存）
                openMainGui(player, npc);
                break;
            case 31: // 移除功能
                npc.removeFeature(FeatureType.JUKEBOX);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&c已移除点歌台功能"));
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
        // slot 10, 12, 14, 16 对应 4 个功能
        var features = npc.getFeatures();
        int[] slots = {10, 12, 14, 16};
        int featureIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) { featureIndex = i; break; }
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
                case JUKEBOX -> {
                    openJukeboxSelectGui(player, npc);
                }
                case MESSAGE -> {
                    MessageFeature.showMessage(player, npc);
                    player.closeInventory();
                }
            }
        }
    }

    // ===== 点歌选择（顾客视角）点击处理 =====

    private void openJukeboxSelectGui(Player player, PhilosNPC npc) {
        GuiState state = new GuiState(GuiState.Screen.JUKEBOX_SELECT, npc.getId(), 0, new HashMap<>());
        var inv = Bukkit.createInventory(state, 27,
                PhilosNPCPlugin.cc("&b&l点歌 - " + npc.getDisplayName()));
        var discs = npc.getJukeboxDiscs();
        for (int i = 0; i < 9; i++) {
            if (discs[i] != null && discs[i].getType() != Material.AIR) {
                var item = discs[i].clone();
                var meta = item.getItemMeta();
                if (meta != null) {
                    var lore = new ArrayList<String>();
                    lore.add(PhilosNPCPlugin.cc("&e左键点击播放"));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                inv.setItem(9 + i, item);
            }
        }
        inv.setItem(18, createButton(Material.BARRIER, "&c返回", "&7点击返回"));
        fillEmpty(inv);
        state.setInventory(inv);
        player.openInventory(inv);
    }

    private void handleJukeboxSelectClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;
        if (slot == 18) {
            openCustomerGui(player, npc);
            return;
        }
        // 播放唱片（slot 9-17）
        if (slot >= 9 && slot <= 17) {
            JukeboxFeature.playDisc(player, npc, slot - 9);
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

    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        var player = event.getPlayer();
        String msg = event.getMessage();

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
            JUKEBOX_EDIT,
            MESSAGE_EDIT,
            CUSTOMER,
            JUKEBOX_SELECT,
            NPC_LIST
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
