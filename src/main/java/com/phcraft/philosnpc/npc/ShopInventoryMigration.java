package com.phcraft.philosnpc.npc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Identifies legacy shop inventories that cannot be assigned safely to a world.
 * This class deliberately does not choose a surviving copy or mutate any inventory.
 */
final class ShopInventoryMigration {

    private ShopInventoryMigration() {
    }

    static <T> Map<UUID, Set<String>> findAmbiguousOwners(
            Collection<InventoryRecord<T>> records,
            BiPredicate<T, T> inventoriesEqual) {
        Map<UUID, Map<String, List<T>>> byOwnerAndWorld = new LinkedHashMap<>();
        for (InventoryRecord<T> record : records) {
            if (record.system()) continue;
            byOwnerAndWorld
                    .computeIfAbsent(record.ownerUuid(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(record.worldName(), ignored -> new ArrayList<>())
                    .add(record.inventory());
        }

        Map<UUID, Set<String>> ambiguousOwners = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, List<T>>> ownerEntry : byOwnerAndWorld.entrySet()) {
            Map<String, List<T>> worlds = ownerEntry.getValue();
            boolean ambiguous = worlds.size() > 1;
            for (List<T> inventories : worlds.values()) {
                T reference = inventories.get(0);
                for (int i = 1; i < inventories.size(); i++) {
                    if (!inventoriesEqual.test(reference, inventories.get(i))) {
                        ambiguous = true;
                        break;
                    }
                }
                if (ambiguous) break;
            }

            if (ambiguous) {
                ambiguousOwners.put(ownerEntry.getKey(), new LinkedHashSet<>(worlds.keySet()));
            }
        }
        return ambiguousOwners;
    }

    record InventoryRecord<T>(UUID ownerUuid, String worldName, boolean system, T inventory) {
        InventoryRecord {
            if (ownerUuid == null) throw new IllegalArgumentException("ownerUuid is required");
            if (worldName == null) worldName = "world";
        }
    }
}
