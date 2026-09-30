package com.phcraft.philosnpc.npc;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.gui.GuiManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NPCManagerRemovalGuardTest {
    private final UUID ownerId = UUID.randomUUID();
    private final List<World> worlds = new ArrayList<>();

    @Test
    void deletingLastNpcChecksInventoryAfterOpenEditorWasFlushed(@TempDir Path dataDir)
            throws ReflectiveOperationException, IOException {
        PhilosNPCPlugin plugin = mock(PhilosNPCPlugin.class);
        try (MockedStatic<PhilosNPCPlugin> pluginStatic =
                     mockStatic(PhilosNPCPlugin.class, CALLS_REAL_METHODS);
             MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
            pluginStatic.when(PhilosNPCPlugin::instance).thenReturn(plugin);
            when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
            when(plugin.getLogger()).thenReturn(Logger.getLogger("npc-removal-guard-tests"));
            Files.writeString(dataDir.resolve("npcs.yml"), "inventory-format: 2\nnpcs: []\n");

            NPCManager manager = new NPCManager();
            manager.loadAll();
            when(plugin.npcManager()).thenReturn(manager);
            GuiManager gui = new GuiManager();
            when(plugin.guiManager()).thenReturn(gui);

            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            worlds.add(world);
            PhilosNPC npc = new PhilosNPC("Owner", ownerId, new Location(world, 1, 64, 1));
            npc.setId("npc-last");
            addNpc(manager, npc);
            manager.setSharedShopInventory(ownerId, "world", new ItemStack[36]);

            Player owner = mock(Player.class);
            when(owner.getUniqueId()).thenReturn(ownerId);
            when(owner.getWorld()).thenReturn(world);
            Inventory top = mock(Inventory.class);
            when(top.getSize()).thenReturn(45);
            GuiManager.GuiState state = shopState(npc);
            when(top.getHolder()).thenReturn(state);
            ItemStack storedStock = mock(ItemStack.class);
            Material itemType = mock(Material.class);
            when(itemType.isAir()).thenReturn(false);
            when(storedStock.clone()).thenReturn(storedStock);
            when(storedStock.getType()).thenReturn(itemType);
            when(top.getItem(0)).thenReturn(storedStock);
            InventoryView view = mock(InventoryView.class);
            when(view.getTopInventory()).thenReturn(top);
            when(owner.getOpenInventory()).thenReturn(view);

            InventoryCloseEvent closeEvent = mock(InventoryCloseEvent.class);
            when(closeEvent.getPlayer()).thenReturn(owner);
            when(closeEvent.getView()).thenReturn(view);
            doAnswer(invocation -> {
                gui.onInventoryClose(closeEvent);
                return null;
            }).when(owner).closeInventory();

            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(owner));
            bukkit.when(() -> Bukkit.getPlayer(ownerId)).thenReturn(null);

            assertFalse(manager.deleteNPC("npc-last"));
            assertTrue(manager.getNPC("npc-last") == npc);
            assertSame(storedStock, manager.getSharedShopInventory(ownerId, "world")[0]);
        }
    }

    @Test
    void anotherPersonalNpcInTheSameScopeKeepsInventoryFromBeingOrphaned() {
        PhilosNPC candidate = npc("npc-a", "world");
        PhilosNPC sibling = npc("npc-b", "world");
        ItemStack[] stock = new ItemStack[36];
        ItemStack stockItem = mock(ItemStack.class);
        Material itemType = mock(Material.class);
        when(itemType.isAir()).thenReturn(false);
        when(stockItem.getType()).thenReturn(itemType);
        stock[0] = stockItem;

        assertFalse(NPCManager.wouldLoseShopInventoryOnRemoval(
                candidate, List.of(candidate, sibling), stock));
    }

    private PhilosNPC npc(String id, String worldName) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        worlds.add(world);
        PhilosNPC npc = new PhilosNPC("Owner", ownerId, new Location(world, 0, 64, 0));
        npc.setId(id);
        return npc;
    }

    private GuiManager.GuiState shopState(PhilosNPC npc) {
        Map<String, Object> data = new HashMap<>();
        data.put("ownerUuid", ownerId.toString());
        data.put("sessionWorld", npc.getWorldName());
        return new GuiManager.GuiState(GuiManager.GuiState.Screen.SHOP_EDIT, npc.getId(), 0, data);
    }

    @SuppressWarnings("unchecked")
    private void addNpc(NPCManager manager, PhilosNPC npc) throws ReflectiveOperationException {
        Field field = NPCManager.class.getDeclaredField("npcs");
        field.setAccessible(true);
        ((Map<String, PhilosNPC>) field.get(manager)).put(npc.getId(), npc);
    }
}
