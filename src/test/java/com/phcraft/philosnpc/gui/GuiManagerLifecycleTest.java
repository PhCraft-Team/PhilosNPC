package com.phcraft.philosnpc.gui;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.features.ShopGui;
import com.phcraft.philosnpc.features.ShopTrade;
import com.phcraft.philosnpc.features.UsageNotify;
import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.PlayerInventory;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuiManagerLifecycleTest {
    private final UUID ownerId = UUID.randomUUID();
    private final UUID editorId = UUID.randomUUID();
    private PhilosNPCPlugin plugin;
    private NPCManager npcManager;
    private GuiManager gui;
    private MockedStatic<PhilosNPCPlugin> pluginStatic;

    @BeforeEach
    void setUp() {
        plugin = mock(PhilosNPCPlugin.class);
        npcManager = mock(NPCManager.class);
        pluginStatic = mockStatic(PhilosNPCPlugin.class, CALLS_REAL_METHODS);
        pluginStatic.when(PhilosNPCPlugin::instance).thenReturn(plugin);
        when(plugin.npcManager()).thenReturn(npcManager);
        gui = new GuiManager();
    }

    @AfterEach
    void tearDown() {
        pluginStatic.close();
    }

    @Test
    void reopeningShopInventoryFlushesOldSnapshotBeforeCreatingNewOne() {
        Player player = playerIn("world");
        PhilosNPC npc = personalNpc("npc-a", "world");
        Inventory oldTop = inventory(45);
        Inventory newTop = inventory(45);
        GuiManager.GuiState oldState = shopState(npc, "world");
        when(oldTop.getHolder()).thenReturn(oldState);
        when(oldTop.getSize()).thenReturn(45);
        when(npcManager.getNPC("npc-a")).thenReturn(npc);
        when(npcManager.tryLockShopInventory(ownerId, "world", editorId)).thenReturn(true);

        List<String> sequence = new ArrayList<>();
        doAnswer(invocation -> {
            sequence.add("write-old-snapshot");
            return null;
        }).when(npcManager).setSharedShopInventory(eq(ownerId), eq("world"), any());
        doAnswer(invocation -> {
            sequence.add("unlock-old-session");
            return null;
        }).when(npcManager).unlockShopInventory(ownerId, "world", editorId);
        doAnswer(invocation -> {
            sequence.add("save-old-snapshot");
            return null;
        }).when(npcManager).saveAll();

        InventoryView oldView = view(oldTop);
        when(player.getOpenInventory()).thenReturn(oldView);
        doAnswer(invocation -> {
            gui.onInventoryClose(closeEvent(player, oldView));
            sequence.add("closed-old-view");
            return null;
        }).when(player).closeInventory();

        InventoryView newView = view(newTop);
        when(player.openInventory(newTop)).thenAnswer(invocation -> {
            sequence.add("opened-new-view");
            return newView;
        });
        try (MockedStatic<ShopGui> shopGui = mockStatic(ShopGui.class)) {
            shopGui.when(() -> ShopGui.shopInventoryGui(eq(npc), any())).thenAnswer(invocation -> {
                sequence.add("snapshot-new-view");
                return newTop;
            });

            gui.openShopEditGui(player, npc);
        }

        assertTrue(sequence.indexOf("write-old-snapshot") < sequence.indexOf("snapshot-new-view"), sequence.toString());
        assertTrue(sequence.indexOf("unlock-old-session") < sequence.indexOf("snapshot-new-view"), sequence.toString());
        verify(npcManager).tryLockShopInventory(ownerId, "world", editorId);
        verify(player).openInventory(newTop);
    }

    @Test
    void cancelledShopInventoryOpenReleasesTheNewLock() {
        Player player = playerIn("world");
        PhilosNPC npc = personalNpc("npc-a", "world");
        Inventory newTop = inventory(45);
        Inventory noTop = inventory(0);
        when(noTop.getHolder()).thenReturn(null);
        InventoryView noView = view(noTop);
        when(player.getOpenInventory()).thenReturn(noView);
        when(npcManager.tryLockShopInventory(ownerId, "world", editorId)).thenReturn(true);
        when(player.openInventory(newTop)).thenReturn(null); // Bukkit returns null when InventoryOpenEvent is cancelled.

        try (MockedStatic<ShopGui> shopGui = mockStatic(ShopGui.class)) {
            shopGui.when(() -> ShopGui.shopInventoryGui(eq(npc), any())).thenReturn(newTop);
            gui.openShopEditGui(player, npc);
        }

        verify(npcManager).unlockShopInventory(ownerId, "world", editorId);
    }

    @Test
    void quitFlushesSnapshotExactlyOnceEvenWhenCloseEventAlsoRuns() {
        Player player = playerIn("world");
        Inventory top = inventory(45);
        GuiManager.GuiState state = shopState(personalNpc("npc-a", "world"), "world");
        when(top.getHolder()).thenReturn(state);
        when(top.getSize()).thenReturn(45);
        InventoryView view = view(top);
        when(player.getOpenInventory()).thenReturn(view);
        doAnswer(invocation -> {
            gui.onInventoryClose(closeEvent(player, view));
            return null;
        }).when(player).closeInventory();
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);

        gui.onPlayerQuit(quit);

        verify(npcManager).setSharedShopInventory(eq(ownerId), eq("world"), any());
        verify(npcManager).unlockShopInventory(ownerId, "world", editorId);
        verify(npcManager).saveAll();
        verify(npcManager).clearShopEditLocks(editorId);
    }

    @Test
    void crossWorldTeleportClosesPluginGuiButLeavesOtherPluginGuiAlone() {
        Player player = playerIn("world-a");
        Inventory pluginTop = inventory(45);
        GuiManager.GuiState pluginState = shopState(personalNpc("npc-a", "world-a"), "world-a");
        when(pluginTop.getHolder()).thenReturn(pluginState);
        InventoryView pluginView = view(pluginTop);
        when(player.getOpenInventory()).thenReturn(pluginView);
        PlayerTeleportEvent teleport = teleportEvent(player, "world-a", "world-b");

        gui.onPlayerTeleport(teleport);

        verify(player).closeInventory();

        Player otherPluginPlayer = playerIn("world-a");
        Inventory otherPluginTop = inventory(27);
        when(otherPluginTop.getHolder()).thenReturn(null);
        InventoryView otherPluginView = view(otherPluginTop);
        when(otherPluginPlayer.getOpenInventory()).thenReturn(otherPluginView);
        gui.onPlayerTeleport(teleportEvent(otherPluginPlayer, "world-a", "world-b"));
        gui.closeNpcSession(otherPluginPlayer);

        verify(otherPluginPlayer, never()).closeInventory();
    }

    @Test
    void cancelledOrRedirectedMerchantOpenDoesNotInterceptAnExistingMerchant() {
        Player player = playerIn("world");
        PhilosNPC npc = personalNpc("npc-a", "world");
        ItemStack result = itemStack(1);
        ItemStack price = itemStack(1, Material.EMERALD);
        ShopTrade trade = new ShopTrade(result, price, null, 5);
        when(npc.getTrades()).thenReturn(List.of(trade));
        Merchant pluginMerchant = mock(Merchant.class);
        Merchant otherPluginMerchant = mock(Merchant.class);
        MerchantInventory otherPluginTop = mock(MerchantInventory.class);
        when(otherPluginTop.getMerchant()).thenReturn(otherPluginMerchant);
        when(otherPluginTop.getItem(2)).thenReturn(result);
        InventoryView redirectedView = view(otherPluginTop);
        when(player.openMerchant(pluginMerchant, true)).thenReturn(null, redirectedView);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            bukkit.when(() -> Bukkit.createMerchant(anyString())).thenReturn(pluginMerchant);
            gui.openMerchantShop(player, npc);
            gui.openMerchantShop(player, npc);
        }

        InventoryClickEvent click = merchantClick(player, otherPluginTop);
        gui.onInventoryClick(click);

        verify(click, never()).setCancelled(true);
        verify(otherPluginTop, never()).setItem(any(Integer.class), any(ItemStack.class));
        verify(player, never()).closeInventory();
        verify(player, times(2)).openMerchant(pluginMerchant, true);
    }

    @Test
    void staleCollectionBackpackCannotBeRetrievedFromAnotherWorld() {
        Player player = playerIn("world-b");
        PhilosNPC npc = personalNpc("npc-a", "world-a");
        Map<String, Object> data = new HashMap<>();
        data.put("ownerUuid", ownerId.toString());
        data.put("worldName", "world-a");
        data.put("sessionWorld", "world-a");
        GuiManager.GuiState state = new GuiManager.GuiState(
                GuiManager.GuiState.Screen.COLLECTION_BACKPACK, "npc-a", 0, data);
        Inventory top = inventory(54);
        when(top.getHolder()).thenReturn(state);
        when(top.getSize()).thenReturn(54);
        InventoryView view = view(top);
        when(player.getOpenInventory()).thenReturn(view);
        when(npcManager.getNPC("npc-a")).thenReturn(null);

        org.bukkit.event.inventory.InventoryClickEvent click = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(view);
        when(click.getRawSlot()).thenReturn(49);
        when(click.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);

        gui.onInventoryClick(click);

        verify(click).setCancelled(true);
        verify(npcManager, never()).clearCollectionBackpack(ownerId, "world-a");
        verify(player).closeInventory();
    }

    @Test
    void crossWorldShopClickWithCursorIsCancelledEvenAfterNpcWasRemoved() {
        Player player = playerIn("world-b");
        Inventory top = inventory(45);
        GuiManager.GuiState state = shopState(personalNpc("npc-a", "world-a"), "world-a");
        when(top.getHolder()).thenReturn(state);
        InventoryView view = view(top);
        when(player.getOpenInventory()).thenReturn(view);
        when(npcManager.getNPC("npc-a")).thenReturn(null);

        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(view);
        when(click.getRawSlot()).thenReturn(0);
        when(click.getClick()).thenReturn(ClickType.LEFT);
        ItemStack cursor = itemStack(1);
        when(click.getCursor()).thenReturn(cursor);

        gui.onInventoryClick(click);

        verify(click).setCancelled(true);
        verify(click, never()).setCursor(any());
        verify(top, never()).setItem(eq(0), any());
        verify(npcManager, never()).setSharedShopInventory(eq(ownerId), eq("world-a"), any());
        verify(player).closeInventory();
    }

    @Test
    void shopInventoryOpenExceptionReleasesTheLock() {
        Player player = playerIn("world");
        PhilosNPC npc = personalNpc("npc-a", "world");
        Inventory newTop = inventory(45);
        Inventory noTop = inventory(0);
        when(noTop.getHolder()).thenReturn(null);
        InventoryView noView = view(noTop);
        when(player.getOpenInventory()).thenReturn(noView);
        when(npcManager.tryLockShopInventory(ownerId, "world", editorId)).thenReturn(true);

        try (MockedStatic<ShopGui> shopGui = mockStatic(ShopGui.class)) {
            shopGui.when(() -> ShopGui.shopInventoryGui(eq(npc), any())).thenReturn(newTop);
            doThrow(new IllegalStateException("open failed")).when(player).openInventory(newTop);

            assertThrows(IllegalStateException.class, () -> gui.openShopEditGui(player, npc));
        }

        verify(npcManager).unlockShopInventory(ownerId, "world", editorId);
    }

    @Test
    void lockedCurrencyMerchantPurchaseDoesNotChargeOrDeliver() throws ReflectiveOperationException {
        Player player = playerIn("world");
        PhilosNPC npc = personalNpc("npc-a", "world");
        ItemStack result = itemStack(1);
        ShopTrade trade = new ShopTrade(result, 12.0, 5);
        MerchantInventory merchant = merchantInventory(result);
        installMerchantSession(player, npc, trade);
        when(npcManager.isShopInventoryLocked(ownerId, "world")).thenReturn(true);
        Economy economy = mock(Economy.class);
        pluginStatic.when(PhilosNPCPlugin::economy).thenReturn(economy);

        gui.onInventoryClick(merchantClick(player, merchant));

        verify(economy, never()).withdrawPlayer(player, 12.0);
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
        verify(player, never()).getInventory();
        verify(npcManager, never()).getSharedShopInventory(ownerId, "world");
        assertTrue(trade.canUse());
    }

    @Test
    void barterPurchaseIsBlockedWhileLockedAndWorksAfterUnlock() throws ReflectiveOperationException {
        Player player = playerIn("world");
        PhilosNPC npc = personalNpc("npc-a", "world");
        ItemStack result = itemStack(1);
        ItemStack price = itemStack(2, Material.EMERALD);
        ItemStack priceInSlot = price.clone();
        ShopTrade trade = new ShopTrade(result, price, null, 5);
        MerchantInventory merchant = merchantInventory(result);
        when(merchant.getItem(0)).thenReturn(priceInSlot);
        installMerchantSession(player, npc, trade);
        when(npcManager.isShopInventoryLocked(ownerId, "world")).thenReturn(true, false);
        ItemStack[] stock = new ItemStack[36];
        stock[0] = result.clone();
        when(npcManager.getSharedShopInventory(ownerId, "world")).thenReturn(stock);
        PlayerInventory playerInventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInventory);
        when(playerInventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());

        try (MockedStatic<UsageNotify> usageNotify = mockStatic(UsageNotify.class)) {
            gui.onInventoryClick(merchantClick(player, merchant));
            verify(player, never()).getInventory();
            verify(npcManager, never()).addItemToCollectionBackpack(ownerId, "world", price);

            gui.onInventoryClick(merchantClick(player, merchant));
        }

        verify(playerInventory).addItem(any(ItemStack.class));
        verify(npcManager).addItemToCollectionBackpack(eq(ownerId), eq("world"), any(ItemStack.class));
        verify(npcManager).setSharedShopInventory(eq(ownerId), eq("world"), any());
        assertTrue(trade.getUses() == 1);
    }

    @Test
    void currencyReentryGuardClearsAfterVaultThrows() throws ReflectiveOperationException {
        Player player = playerIn("world");
        PhilosNPC npc = personalNpc("npc-a", "world");
        ItemStack result = itemStack(1);
        ShopTrade trade = new ShopTrade(result, 12.0, 5);
        MerchantInventory merchant = merchantInventory(result);
        InventoryView merchantView = view(merchant);
        when(player.getOpenInventory()).thenReturn(merchantView);
        installMerchantSession(player, npc, trade);
        when(npcManager.isShopInventoryLocked(ownerId, "world")).thenReturn(false);
        ItemStack[] stock = new ItemStack[36];
        stock[0] = result.clone();
        when(npcManager.getSharedShopInventory(ownerId, "world")).thenReturn(stock);
        Economy economy = mock(Economy.class);
        pluginStatic.when(PhilosNPCPlugin::economy).thenReturn(economy);
        doThrow(new IllegalStateException("Vault provider failed"))
                .when(economy).withdrawPlayer(player, 12.0);

        assertThrows(IllegalStateException.class,
                () -> gui.onInventoryClick(merchantClick(player, merchant)));

        Inventory editor = inventory(45);
        InventoryView editorView = view(editor);
        when(npcManager.tryLockShopInventory(ownerId, "world", editorId)).thenReturn(true);
        when(player.openInventory(editor)).thenReturn(editorView);
        try (MockedStatic<ShopGui> shopGui = mockStatic(ShopGui.class)) {
            shopGui.when(() -> ShopGui.shopInventoryGui(eq(npc), any())).thenReturn(editor);
            gui.openShopEditGui(player, npc);
        }

        verify(npcManager).tryLockShopInventory(ownerId, "world", editorId);
        verify(player).openInventory(editor);
    }

    private Player playerIn(String worldName) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(editorId);
        return player;
    }

    private MerchantInventory merchantInventory(ItemStack result) {
        MerchantInventory merchant = mock(MerchantInventory.class);
        when(merchant.getItem(2)).thenReturn(result);
        when(merchant.getSelectedRecipeIndex()).thenReturn(0);
        return merchant;
    }

    private ItemStack itemStack(int amount) {
        return itemStack(amount, Material.DIAMOND);
    }

    private ItemStack itemStack(int amount, Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);
        when(item.getAmount()).thenReturn(amount);
        when(item.getType()).thenReturn(material);
        when(item.isSimilar(any(ItemStack.class))).thenReturn(true);
        return item;
    }

    @SuppressWarnings("unchecked")
    private void installMerchantSession(Player player, PhilosNPC npc, ShopTrade trade)
            throws ReflectiveOperationException {
        Field sessionsField = GuiManager.class.getDeclaredField("merchantSessions");
        sessionsField.setAccessible(true);
        Map<UUID, Object> sessions = (Map<UUID, Object>) sessionsField.get(gui);

        Class<?> sessionClass = Class.forName(GuiManager.class.getName() + "$MerchantSession");
        Constructor<?> constructor = sessionClass.getDeclaredConstructor(
                PhilosNPC.class, List.class, String.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(npc, List.of(trade), npc.getWorldName());
        sessions.put(player.getUniqueId(), session);
    }

    private InventoryClickEvent merchantClick(Player player, MerchantInventory top) {
        InventoryView view = view(top);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        when(click.getView()).thenReturn(view);
        when(click.getRawSlot()).thenReturn(2);
        when(click.getClick()).thenReturn(ClickType.LEFT);
        return click;
    }

    private PhilosNPC personalNpc(String id, String worldName) {
        PhilosNPC npc = mock(PhilosNPC.class);
        when(npc.getId()).thenReturn(id);
        when(npc.getOwnerUuid()).thenReturn(ownerId);
        when(npc.getWorldName()).thenReturn(worldName);
        when(npc.isSystem()).thenReturn(false);
        return npc;
    }

    private GuiManager.GuiState shopState(PhilosNPC npc, String worldName) {
        Map<String, Object> data = new HashMap<>();
        data.put("ownerUuid", ownerId.toString());
        data.put("sessionWorld", worldName);
        return new GuiManager.GuiState(GuiManager.GuiState.Screen.SHOP_EDIT, npc.getId(), 0, data);
    }

    private Inventory inventory(int size) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(size);
        return inventory;
    }

    private InventoryView view(Inventory top) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        return view;
    }

    private InventoryCloseEvent closeEvent(Player player, InventoryView view) {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        return event;
    }

    private PlayerTeleportEvent teleportEvent(Player player, String fromName, String toName) {
        World fromWorld = mock(World.class);
        World toWorld = mock(World.class);
        when(fromWorld.getUID()).thenReturn(UUID.nameUUIDFromBytes(fromName.getBytes()));
        when(toWorld.getUID()).thenReturn(UUID.nameUUIDFromBytes(toName.getBytes()));
        PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getFrom()).thenReturn(new org.bukkit.Location(fromWorld, 0, 0, 0));
        when(event.getTo()).thenReturn(new org.bukkit.Location(toWorld, 0, 0, 0));
        return event;
    }
}
