package com.phcraft.philosnpc.npc;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;

class NPCManagerMigrationIntegrationTest {

    private static final UUID OWNER = UUID.fromString("bf39c55e-7f0d-4e41-9af3-60ccf3c44a15");

    @TempDir
    Path directory;

    @Test
    void ambiguousLegacyOwnerKeepsPerNpcStockAcrossSaveAndReload() throws IOException {
        try (Fixture fixture = new Fixture(directory)) {
            Path source = directory.resolve("npcs.yml");
            writeNpcFile(source, false);
            String original = Files.readString(source);
            fixture.unloadWorlds();

            NPCManager manager = new NPCManager();
            manager.loadAll();

            assertTrue(manager.isStorageReady());
            assertTrue(manager.isShopInventoryMigrationBlocked(OWNER));
            assertEquals(Set.of("world", "world_nether"), manager.getShopInventoryMigrationReviewWorlds(OWNER));
            assertNull(manager.getNPC("owner-world").getShopInventory()[0]);
            assertEquals(Material.DIAMOND, manager.getNPC("owner-nether").getShopInventory()[0].getType());
            assertFalse(manager.deleteNPC("owner-nether"));
            assertTrue(manager.getNPC("owner-nether") != null);
            assertTrue(Files.exists(directory.resolve("npcs.yml.bak-v1-inventory")));
            assertFalse(original.contains("inventory-migration-review"));

            manager.saveAll();
            Map<?, ?> savedNetherNpc = YamlConfiguration.loadConfiguration(source.toFile()).getMapList("npcs").stream()
                    .filter(row -> "owner-nether".equals(row.get("id")))
                    .findFirst().orElseThrow();
            assertEquals("world_nether", ((Map<?, ?>) savedNetherNpc.get("location")).get("world"));
            assertEquals("world_nether", ((Map<?, ?>) savedNetherNpc.get("teleportTarget")).get("world"));
            assertEquals("DIAMOND", ((Map<?, ?>) ((List<?>) savedNetherNpc.get("shopInventory")).get(0)).get("type"));

            NPCManager reloaded = new NPCManager();
            reloaded.loadAll();
            assertTrue(reloaded.isStorageReady());
            assertTrue(reloaded.isShopInventoryMigrationBlocked(OWNER));
            assertEquals("world", reloaded.getNPC("owner-world").getWorldName());
            assertEquals("world_nether", reloaded.getNPC("owner-nether").getWorldName());
            assertNull(reloaded.getNPC("owner-world").getShopInventory()[0]);
            assertEquals(Material.DIAMOND, reloaded.getNPC("owner-nether").getShopInventory()[0].getType());
            assertFalse(reloaded.isShopInventoryAvailable(OWNER));
        }
    }

    @Test
    void backupFailureFollowedBySaveDoesNotChangeLegacySource() throws IOException {
        try (Fixture fixture = new Fixture(directory)) {
            Path source = directory.resolve("npcs.yml");
            writeNpcFile(source, false);
            String original = Files.readString(source);
            Files.createDirectory(directory.resolve("npcs.yml.bak-v1-inventory"));

            NPCManager manager = new NPCManager();
            manager.loadAll();
            manager.saveAll();

            assertFalse(manager.isStorageReady());
            assertTrue(manager.isShopInventoryMigrationBlocked(OWNER));
            assertEquals(original, Files.readString(source));
        }
    }

    @Test
    void invalidYamlFollowedBySaveDoesNotReplaceSource() throws IOException {
        try (Fixture fixture = new Fixture(directory)) {
            Path source = directory.resolve("npcs.yml");
            String original = "npcs: [\n  - id: broken\n    ownerUuid: [oops\n";
            Files.writeString(source, original);

            NPCManager manager = new NPCManager();
            manager.loadAll();
            manager.saveAll();

            assertFalse(manager.isStorageReady());
            assertEquals(original, Files.readString(source));
        }
    }

    private static void writeNpcFile(Path source, boolean versioned) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        if (versioned) config.set("inventory-format", 2);
        config.set("npcs", List.of(
                npcRow("owner-world", "world", false),
                npcRow("owner-nether", "world_nether", true)));
        config.save(source.toFile());
    }

    private static Map<String, Object> npcRow(String id, String world, boolean withDiamond) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("world", world);
        location.put("x", 0.0);
        location.put("y", 64.0);
        location.put("z", 0.0);
        location.put("yaw", 0.0);
        location.put("pitch", 0.0);

        Map<String, Object> teleportTarget = new LinkedHashMap<>();
        teleportTarget.put("world", world);
        teleportTarget.put("x", 10.0);
        teleportTarget.put("y", 64.0);
        teleportTarget.put("z", 10.0);
        teleportTarget.put("yaw", 0.0);
        teleportTarget.put("pitch", 0.0);

        Map<String, Object> npc = new LinkedHashMap<>();
        npc.put("id", id);
        npc.put("ownerName", "owner");
        npc.put("ownerUuid", OWNER.toString());
        npc.put("location", location);
        npc.put("teleportTarget", teleportTarget);
        npc.put("pose", "STANDING");
        npc.put("scale", 1.0);
        npc.put("features", List.of());
        npc.put("npcType", "PERSONAL");
        if (withDiamond) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "DIAMOND");
            item.put("amount", 1);
            npc.put("shopInventory", List.of(item));
        }
        return npc;
    }

    private static final class Fixture implements AutoCloseable {
        private final MockedStatic<PhilosNPCPlugin> pluginStatic;
        private final MockedStatic<Bukkit> bukkitStatic;
        private final MockedStatic<ItemStack> itemStackStatic;

        private Fixture(Path dataFolder) {
            PhilosNPCPlugin plugin = mock(PhilosNPCPlugin.class);
            when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
            when(plugin.getLogger()).thenReturn(Logger.getLogger("npc-migration-tests"));
            pluginStatic = mockStatic(PhilosNPCPlugin.class);
            pluginStatic.when(PhilosNPCPlugin::instance).thenReturn(plugin);

            World mainWorld = mock(World.class);
            World nether = mock(World.class);
            when(mainWorld.getName()).thenReturn("world");
            when(nether.getName()).thenReturn("world_nether");
            bukkitStatic = mockStatic(Bukkit.class);
            bukkitStatic.when(() -> Bukkit.getWorld("world")).thenReturn(mainWorld);
            bukkitStatic.when(() -> Bukkit.getWorld("world_nether")).thenReturn(nether);

            ItemStack diamond = mock(ItemStack.class);
            when(diamond.getType()).thenReturn(Material.DIAMOND);
            when(diamond.getAmount()).thenReturn(1);
            when(diamond.serialize()).thenReturn(Map.of("type", "DIAMOND", "amount", 1));
            itemStackStatic = mockStatic(ItemStack.class);
            itemStackStatic.when(() -> ItemStack.deserialize(anyMap())).thenReturn(diamond);
        }

        private void unloadWorlds() {
            bukkitStatic.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            bukkitStatic.when(() -> Bukkit.getWorld("world_nether")).thenReturn(null);
        }

        @Override
        public void close() {
            itemStackStatic.close();
            bukkitStatic.close();
            pluginStatic.close();
        }
    }
}
