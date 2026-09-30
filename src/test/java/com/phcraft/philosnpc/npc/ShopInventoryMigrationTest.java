package com.phcraft.philosnpc.npc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopInventoryMigrationTest {

    private static final UUID OWNER = UUID.fromString("bf39c55e-7f0d-4e41-9af3-60ccf3c44a15");

    @Test
    void equalInventoriesInDifferentWorldsAreAmbiguous() {
        Map<UUID, Set<String>> result = ShopInventoryMigration.findAmbiguousOwners(List.of(
                record("world", List.of("diamond", "bread")),
                record("world_nether", List.of("diamond", "bread"))),
                List::equals);

        assertEquals(Set.of("world", "world_nether"), result.get(OWNER));
    }

    @Test
    void differentInventoriesInDifferentWorldsAreAmbiguous() {
        Map<UUID, Set<String>> result = ShopInventoryMigration.findAmbiguousOwners(List.of(
                record("world", List.of("diamond")),
                record("world_nether", List.of("emerald"))),
                List::equals);

        assertEquals(Set.of("world", "world_nether"), result.get(OWNER));
    }

    @Test
    void inconsistentNpcCopiesInOneWorldAreAmbiguous() {
        Map<UUID, Set<String>> result = ShopInventoryMigration.findAmbiguousOwners(List.of(
                record("world", List.of("diamond")),
                record("world", List.of("emerald"))),
                List::equals);

        assertEquals(Set.of("world"), result.get(OWNER));
    }

    @Test
    void equalNpcCopiesInOneWorldAndSingleNpcRemainUsable() {
        Map<UUID, Set<String>> result = ShopInventoryMigration.findAmbiguousOwners(List.of(
                record("world", List.of("diamond")),
                record("world", List.of("diamond")),
                new ShopInventoryMigration.InventoryRecord<>(UUID.randomUUID(), "world", false, List.of("bread"))),
                List::equals);

        assertFalse(result.containsKey(OWNER));
        assertTrue(result.isEmpty());
    }

    @Test
    void systemNpcsDoNotCreateOwnerAmbiguity() {
        Map<UUID, Set<String>> result = ShopInventoryMigration.findAmbiguousOwners(List.of(
                new ShopInventoryMigration.InventoryRecord<>(OWNER, "world", true, List.of("diamond")),
                new ShopInventoryMigration.InventoryRecord<>(OWNER, "world_nether", true, List.of("emerald"))),
                List::equals);

        assertTrue(result.isEmpty());
    }

    private static ShopInventoryMigration.InventoryRecord<List<String>> record(String world, List<String> contents) {
        return new ShopInventoryMigration.InventoryRecord<>(OWNER, world, false, contents);
    }
}
