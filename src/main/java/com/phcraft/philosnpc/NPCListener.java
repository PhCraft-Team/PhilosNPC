package com.phcraft.philosnpc;

import com.phcraft.philosnpc.gui.GuiManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class NPCListener implements Listener {

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        var pdc = event.getRightClicked().getPersistentDataContainer();
        if (!pdc.has(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        String npcId = pdc.get(PhilosNPCPlugin.npcIdKey(), PersistentDataType.STRING);
        PhilosNPC npc = PhilosNPCPlugin.instance().npcManager().getNPC(npcId);
        if (npc == null) return;

        GuiManager gui = PhilosNPCPlugin.instance().guiManager();

        boolean isOwner = npc.getOwnerUuid().equals(player.getUniqueId());
        boolean isAdmin = player.hasPermission("philosnpc.admin");

        if ((isOwner || isAdmin) && player.isSneaking()) {
            gui.openMainGui(player, npc);
        } else {
            if (npc.getFeatures().isEmpty()) {
                player.sendMessage(PhilosNPCPlugin.cc("&7此NPC未启用任何功能"));
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
