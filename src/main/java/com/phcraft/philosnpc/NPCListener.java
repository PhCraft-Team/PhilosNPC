package com.phcraft.philosnpc;

import com.phcraft.philosnpc.features.FeatureGuiFactory;
import com.phcraft.philosnpc.gui.GuiManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class NPCListener implements Listener {

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        var pdc = stand.getPersistentDataContainer();
        if (!pdc.has(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        String npcId = pdc.get(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING);
        PhilosNPC npc = PhilosNPCPlugin.instance().npcManager().getNPC(npcId);
        if (npc == null) return;

        GuiManager gui = PhilosNPCPlugin.instance().guiManager();

        if (npc.getOwnerUuid().equals(player.getUniqueId()) && player.isSneaking()) {
            gui.openMainGui(player, npc);
        } else {
            if (npc.getFeatures().isEmpty()) {
                player.sendMessage(PhilosNPCPlugin.cc("&7这个NPC还没有启用任何功能"));
                return;
            }
            gui.openCustomerGui(player, npc);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        var pdc = event.getEntity().getPersistentDataContainer();
        if (pdc.has(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}
