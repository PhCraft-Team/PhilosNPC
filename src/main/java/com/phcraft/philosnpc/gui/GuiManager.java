package com.phcraft.philosnpc.gui;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.FeatureType;
import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.npc.NPCPose;
import com.phcraft.philosnpc.npc.PhilosNPC;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GuiManager implements Listener {

    private final PhilosNPCPlugin plugin;
    private final NPCManager npcManager;
    private final Map<Player, GuiState> openGuis;

    public GuiManager() {
        this.plugin = PhilosNPCPlugin.instance();
        this.npcManager = plugin.npcManager();
        this.openGuis = new HashMap<>();
    }

    // ===== 打开界面方法 =====

    public void openMainGui(Player player, PhilosNPC npc) {
        Inventory inv = NPCGui.mainGui(npc);
        openGuis.put(player, new GuiState(GuiState.Screen.MAIN, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openFeatureSelectGui(Player player, PhilosNPC npc) {
        Inventory inv = NPCGui.featureSelectGui(npc);
        openGuis.put(player, new GuiState(GuiState.Screen.FEATURE_SELECT, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openPoseSelectGui(Player player, PhilosNPC npc) {
        Inventory inv = NPCGui.poseSelectGui(npc);
        openGuis.put(player, new GuiState(GuiState.Screen.POSE_SELECT, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openSizeAdjustGui(Player player, PhilosNPC npc) {
        Inventory inv = NPCGui.sizeAdjustGui(npc);
        openGuis.put(player, new GuiState(GuiState.Screen.SIZE_ADJUST, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openShopEditGui(Player player, PhilosNPC npc) {
        if (npc.isSystem()) {
            // 系统NPC直接打开交易配方编辑界面
            openTradeEditGui(player, npc, 0);
        } else {
            // 个人NPC打开商店背包编辑界面
            var inv = com.phcraft.philosnpc.features.ShopGui.shopInventoryGui(npc);
            openGuis.put(player, new GuiState(GuiState.Screen.SHOP_EDIT, npc.getId(), 0, new HashMap<>()));
            player.openInventory(inv);
        }
    }

    public void openTradeEditGui(Player player, PhilosNPC npc, int page) {
        var inv = com.phcraft.philosnpc.features.ShopGui.tradeEditGui(npc, page);
        openGuis.put(player, new GuiState(GuiState.Screen.SHOP_EDIT, npc.getId(), page, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openTpSettingsGui(Player player, PhilosNPC npc) {
        var inv = com.phcraft.philosnpc.features.FeatureGuiFactory.teleportSettingsGui(npc);
        openGuis.put(player, new GuiState(GuiState.Screen.TP_SETTINGS, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openJukeboxEditGui(Player player, PhilosNPC npc) {
        var inv = com.phcraft.philosnpc.features.FeatureGuiFactory.jukeboxEditGui(npc);
        openGuis.put(player, new GuiState(GuiState.Screen.JUKEBOX_EDIT, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openMessageEditGui(Player player, PhilosNPC npc) {
        var inv = com.phcraft.philosnpc.features.FeatureGuiFactory.messageEditGui(npc);
        openGuis.put(player, new GuiState(GuiState.Screen.MESSAGE_EDIT, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openCustomerGui(Player player, PhilosNPC npc) {
        var inv = com.phcraft.philosnpc.features.FeatureGuiFactory.customerFeatureGui(npc, player);
        openGuis.put(player, new GuiState(GuiState.Screen.SHOP_TRADE, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    public void openNPCListGui(Player player, int page) {
        var npcs = npcManager.getNPCsByOwner(player.getUniqueId());
        int perPage = 28;
        int totalPages = Math.max(1, (npcs.size() + perPage - 1) / perPage);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(null, 54,
                PhilosNPCPlugin.cc("&b&l我的NPC列表 - 第" + (page + 1) + "/" + totalPages + "页"));

        int start = page * perPage;
        int end = Math.min(start + perPage, npcs.size());
        int slot = 10;

        for (int i = start; i < end; i++) {
            PhilosNPC npc = npcs.get(i);
            if (slot > 43) break;
            while (slot % 9 == 0 || slot % 9 == 8) slot++;

            var head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                String color = npc.isSystem() ? "&d" : "&b";
                meta.setDisplayName(PhilosNPCPlugin.cc(color + npc.getDisplayName()));
                var lore = new ArrayList<String>();
                lore.add(PhilosNPCPlugin.cc("&7类型: &f" + npc.getNpcType().displayName()));
                lore.add(PhilosNPCPlugin.cc("&7ID: &f" + npc.getId().substring(0, 8)));
                lore.add(PhilosNPCPlugin.cc("&7姿势: &f" + npc.getPose().displayName()));
                lore.add(PhilosNPCPlugin.cc("&7功能: &f" + npc.getFeatures().size() + "/4"));
                lore.add(PhilosNPCPlugin.cc("&e左键点击编辑"));
                meta.setLore(lore);
                if (npc.isSystem() && npc.getEntityTypeName() != null && npc.getEntityTypeName().startsWith("PLAYER:")) {
                    meta.setOwner(npc.getEntityTypeName().substring(7));
                } else if (npc.getOwnerName() != null) {
                    meta.setOwner(npc.getOwnerName());
                }
                head.setItemMeta(meta);
            }
            inv.setItem(slot, head);
            slot++;
        }

        if (page > 0) {
            inv.setItem(45, createButton(Material.ARROW, "&a上一页", "&7点击上一页"));
        }
        inv.setItem(49, createButton(Material.BARRIER, "&c关闭", "&7点击关闭"));
        if (page < totalPages - 1) {
            inv.setItem(53, createButton(Material.ARROW, "&a下一页", "&7点击下一页"));
        }

        fillEmpty(inv);
        openGuis.put(player, new GuiState(GuiState.Screen.NPC_LIST, null, page, new HashMap<>()));
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

    private void fillEmpty(Inventory inv) {
        var glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = glass.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); glass.setItemMeta(meta); }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }

    // ===== 点击事件分发 =====

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        GuiState state = openGuis.get(player);
        if (state == null) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            // 玩家点击自己的背包
            event.setCancelled(true);
            return;
        }

        PhilosNPC npc = state.getNpcId() != null ? npcManager.getNPC(state.getNpcId()) : null;

        // 部分界面允许物品交互
        boolean allowInteract = false;
        if (state.getScreen() == GuiState.Screen.SHOP_EDIT && slot < 36) {
            int invSize = event.getInventory().getSize();
            if (invSize == 45 && npc != null && !npc.isSystem()) {
                // 个人NPC商店背包编辑界面
                allowInteract = true;
            } else if (invSize == 54 && (slot == 27 || slot == 28 || slot == 29)) {
                // 交易配方编辑界面的价格/产出槽位
                allowInteract = true;
            }
        } else if (state.getScreen() == GuiState.Screen.JUKEBOX_EDIT && slot >= 9 && slot <= 17) {
            allowInteract = true;
        }

        if (allowInteract) {
            // 允许物品放置/取出
            return;
        }

        event.setCancelled(true);

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
            case SHOP_TRADE:
                handleCustomerClick(player, npc, slot);
                break;
            case NPC_LIST:
                handleNpcListClick(player, slot, state.getPage());
                break;
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        openGuis.remove(player);
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
                // TODO: 打开NPC列表
                player.closeInventory();
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
                    player.sendMessage(PhilosNPCPlugin.cc("&c功能数量已达上限"));
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
            player.sendMessage(PhilosNPCPlugin.cc("&a姿势已设置为: " + poses[poseIndex].displayName()));
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

    // ===== 商店编辑点击处理 =====

    private void handleShopEditClick(Player player, PhilosNPC npc, int slot, GuiState state) {
        if (npc == null) return;
        int invSize = player.getOpenInventory().getTopInventory().getSize();

        // 交易配方编辑界面（54格）
        if (invSize == 54) {
            handleTradeEditClick(player, npc, slot, state);
            return;
        }

        // 商店背包编辑界面（45格）
        if (slot == 36) { // 返回
            openMainGui(player, npc);
            return;
        }
        if (slot == 40) { // 保存
            if (npc.isSystem()) {
                openMainGui(player, npc);
                return;
            }
            var inv = player.getOpenInventory().getTopInventory();
            var items = new org.bukkit.inventory.ItemStack[36];
            for (int i = 0; i < 36; i++) {
                items[i] = inv.getItem(i);
            }
            npcManager.setSharedShopInventory(npc.getOwnerUuid(), items);
            npcManager.saveAll();
            player.sendMessage(PhilosNPCPlugin.cc("&a商店背包已保存"));
            openMainGui(player, npc);
            return;
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
            case 27: // 金币交易模式（系统NPC）或价格物品1提示（个人NPC）
                if (npc.isSystem()) {
                    // 切换为金币交易模式
                    state.getData().put("useCurrency", true);
                    player.sendMessage(PhilosNPCPlugin.cc("&6已切换为金币交易模式"));
                    player.sendMessage(PhilosNPCPlugin.cc("&7请将产出物品放入槽位29，然后点击添加按钮"));
                    player.sendMessage(PhilosNPCPlugin.cc("&7之后在聊天框输入金币价格"));
                }
                break;
            case 28: // 物物交换模式（系统NPC）或价格物品2提示（个人NPC）
                if (npc.isSystem()) {
                    state.getData().put("useCurrency", false);
                    player.sendMessage(PhilosNPCPlugin.cc("&a已切换为物物交换模式"));
                    player.sendMessage(PhilosNPCPlugin.cc("&7请将价格物品和产出物品放入对应槽位"));
                }
                break;
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
                    openMainGui(player, npc);
                }
                break;
        }
    }

    private void handleAddTradeClick(Player player, PhilosNPC npc, GuiState state) {
        var inv = player.getOpenInventory().getTopInventory();
        var slot29Item = inv.getItem(29);

        if (slot29Item == null || slot29Item.getType() == Material.AIR) {
            player.sendMessage(PhilosNPCPlugin.cc("&c请先将产出物品放入槽位29"));
            return;
        }

        ItemStack result = slot29Item.clone();

        if (npc.isSystem() && Boolean.TRUE.equals(state.getData().get("useCurrency"))) {
            // 金币交易：提示输入价格
            player.closeInventory();
            player.sendMessage(PhilosNPCPlugin.cc("&a请在聊天框输入金币价格（输入 &ccancel &a取消）："));
            state.getData().put("pendingResult", result);
            pendingCurrencyTrade.put(player.getUniqueId(), npc.getId());
            return;
        }

        // 物物交换
        var price1Item = inv.getItem(27);
        var price2Item = inv.getItem(28);

        ItemStack price1 = (price1Item != null && price1Item.getType() != Material.AIR) ? price1Item.clone() : null;
        ItemStack price2 = (price2Item != null && price2Item.getType() != Material.AIR) ? price2Item.clone() : null;

        if (price1 == null) {
            player.sendMessage(PhilosNPCPlugin.cc("&c请将价格物品放入槽位27"));
            return;
        }

        var handler = new com.phcraft.philosnpc.features.ShopHandler();
        handler.addTrade(npc, result, price1, price2, -1);
        player.sendMessage(PhilosNPCPlugin.cc("&a交易已添加"));

        // 清除槽位
        inv.setItem(27, null);
        inv.setItem(28, null);
        inv.setItem(29, null);

        openTradeEditGui(player, npc, state.getPage());
    }

    // ===== 传送设置点击处理 =====

    private void handleTpSettingsClick(Player player, PhilosNPC npc, int slot) {
        if (npc == null) return;
        switch (slot) {
            case 19: // 设为当前位置
                com.phcraft.philosnpc.features.TeleportFeature.setupTeleport(player, npc);
                npcManager.saveAll();
                openTpSettingsGui(player, npc);
                break;
            case 21: // 奖励命令设置
                player.closeInventory();
                player.sendMessage(PhilosNPCPlugin.cc("&a请在聊天框输入奖励命令（输入 &ccancel &a取消）："));
                player.sendMessage(PhilosNPCPlugin.cc("&7可用占位符: &b{player}"));
                pendingRewardCmd.put(player.getUniqueId(), npc.getId());
                break;
            case 22: // 传送价格（系统NPC可自定义）
                if (npc.isSystem()) {
                    player.closeInventory();
                    player.sendMessage(PhilosNPCPlugin.cc("&a请在聊天框输入传送价格（输入 &ccancel &a取消）："));
                    player.sendMessage(PhilosNPCPlugin.cc("&7输入 0 表示免费，输入 -1 恢复默认(5金币)"));
                    pendingTeleportCost.put(player.getUniqueId(), npc.getId());
                }
                break;
            case 27: // 返回
                openMainGui(player, npc);
                break;
            case 31: // 移除功能
                npc.removeFeature(com.phcraft.philosnpc.npc.FeatureType.TELEPORT);
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
            // 已在 onInventoryClick 中允许交互
            return;
        }
        switch (slot) {
            case 27: // 返回
                // 保存唱片
                var inv = player.getOpenInventory().getTopInventory();
                var discs = npc.getJukeboxDiscs();
                for (int i = 0; i < 9; i++) {
                    discs[i] = inv.getItem(9 + i);
                }
                npcManager.saveAll();
                openMainGui(player, npc);
                break;
            case 31: // 移除功能
                npc.removeFeature(com.phcraft.philosnpc.npc.FeatureType.JUKEBOX);
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
                com.phcraft.philosnpc.features.MessageFeature.setMessage(npc, "");
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&a留言已清除"));
                openMessageEditGui(player, npc);
                break;
            case 18: // 返回
                openMainGui(player, npc);
                break;
            case 22: // 移除功能
                npc.removeFeature(com.phcraft.philosnpc.npc.FeatureType.MESSAGE);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&c已移除留言功能"));
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
                    var inv = com.phcraft.philosnpc.features.ShopGui.tradeViewGui(npc, player);
                    openGuis.put(player, new GuiState(GuiState.Screen.SHOP_TRADE, npc.getId(), 0, new HashMap<>()));
                    player.openInventory(inv);
                }
                case TELEPORT -> {
                    com.phcraft.philosnpc.features.TeleportFeature.executeTeleport(player, npc);
                    player.closeInventory();
                }
                case JUKEBOX -> {
                    // 打开点歌选择界面
                    openJukeboxSelectGui(player, npc);
                }
                case MESSAGE -> {
                    com.phcraft.philosnpc.features.MessageFeature.showMessage(player, npc);
                    player.closeInventory();
                }
            }
        }
    }

    private void openJukeboxSelectGui(Player player, PhilosNPC npc) {
        var inv = Bukkit.createInventory(null, 27,
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
        openGuis.put(player, new GuiState(GuiState.Screen.JUKEBOX_EDIT, npc.getId(), 0, new HashMap<>()));
        player.openInventory(inv);
    }

    // ===== NPC列表点击处理 =====

    private void handleNpcListClick(Player player, int slot, int page) {
        if (slot == 45 && page > 0) {
            openNPCListGui(player, page - 1);
            return;
        }
        if (slot == 53) {
            openNPCListGui(player, page + 1);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        // 查找对应NPC
        var npcs = npcManager.getNPCsByOwner(player.getUniqueId());
        int perPage = 28;
        int start = page * perPage;
        int idx = slot - 10;
        // 简化：直接按slot匹配
        for (int i = start; i < Math.min(start + perPage, npcs.size()); i++) {
            var npc = npcs.get(i);
            // 打开主界面
            openMainGui(player, npc);
            return;
        }
    }

    // ===== 聊天输入处理（留言、奖励命令） =====

    private final java.util.Map<java.util.UUID, String> pendingMessage = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> pendingRewardCmd = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> pendingTeleportCost = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, String> pendingCurrencyTrade = new java.util.HashMap<>();

    @EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        var player = event.getPlayer();
        String msg = event.getMessage();

        if (pendingCurrencyTrade.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String npcId = pendingCurrencyTrade.remove(player.getUniqueId());
            var npc = npcManager.getNPC(npcId);
            if (npc == null) return;

            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(PhilosNPCPlugin.cc("&c已取消添加交易"));
                return;
            }
            try {
                double price = Double.parseDouble(msg);
                if (price <= 0) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c价格必须大于0"));
                    return;
                }
                // 读取存储的产出物品
                var state = openGuis.get(player);
                ItemStack result = (state != null) ? (ItemStack) state.getData().get("pendingResult") : null;
                if (result == null) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c产出物品丢失，请重新操作"));
                    return;
                }
                var trade = new com.phcraft.philosnpc.features.ShopTrade(result, price, -1);
                npc.getTrades().add(trade);
                npcManager.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&a金币交易已添加: &6" + price + " 金币"));
                openTradeEditGui(player, npc, 0);
            } catch (NumberFormatException e) {
                player.sendMessage(PhilosNPCPlugin.cc("&c无效的数字格式"));
            }
            return;
        }

        if (pendingRewardCmd.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String npcId = pendingRewardCmd.remove(player.getUniqueId());
            var npc = npcManager.getNPC(npcId);
            if (npc == null) return;

            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(PhilosNPCPlugin.cc("&c已取消设置"));
                return;
            }
            npc.setTeleportRewardCmd(msg);
            npcManager.saveAll();
            player.sendMessage(PhilosNPCPlugin.cc("&a奖励命令已设置: &f" + msg));
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
            try {
                double cost = Double.parseDouble(msg);
                npc.setCustomTeleportCost(cost);
                npcManager.saveAll();
                if (cost < 0) {
                    player.sendMessage(PhilosNPCPlugin.cc("&a已恢复默认传送价格"));
                } else if (cost == 0) {
                    player.sendMessage(PhilosNPCPlugin.cc("&a传送已设为免费"));
                } else {
                    player.sendMessage(PhilosNPCPlugin.cc("&a传送价格已设为: &6" + cost + " 金币"));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(PhilosNPCPlugin.cc("&c无效的数字格式"));
            }
            return;
        }

        if (pendingMessage.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String npcId = pendingMessage.remove(player.getUniqueId());
            var npc = npcManager.getNPC(npcId);
            if (npc == null) return;

            if (msg.equalsIgnoreCase("cancel")) {
                player.sendMessage(PhilosNPCPlugin.cc("&c已取消设置"));
                return;
            }
            com.phcraft.philosnpc.features.MessageFeature.setMessage(npc, msg);
            npcManager.saveAll();
            player.sendMessage(PhilosNPCPlugin.cc("&a留言已设置"));
        }
    }

    // ===== 获取状态 =====

    public GuiState getGuiState(Player player) {
        return openGuis.get(player);
    }

    // ===== GuiState 内部类 =====

    public static class GuiState {

        public enum Screen {
            MAIN,
            FEATURE_SELECT,
            POSE_SELECT,
            SIZE_ADJUST,
            SHOP_EDIT,
            TP_SETTINGS,
            JUKEBOX_EDIT,
            MESSAGE_EDIT,
            SHOP_TRADE,
            NPC_LIST
        }

        private final Screen screen;
        private final String npcId;
        private final int page;
        private final Map<String, Object> data;

        public GuiState(Screen screen, String npcId, int page, Map<String, Object> data) {
            this.screen = screen;
            this.npcId = npcId;
            this.page = page;
            this.data = data;
        }

        public Screen getScreen() { return screen; }
        public String getNpcId() { return npcId; }
        public int getPage() { return page; }
        public Map<String, Object> getData() { return data; }
    }
}
