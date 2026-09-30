package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 玩家NPC功能使用成功通知
 * 仅在功能成功使用并结算后，向NPC主人反馈使用信息
 */
public final class UsageNotify {

    private UsageNotify() {}

    /**
     * 通知NPC主人。仅个人NPC、非本人使用、主人在线时发送。
     */
    public static void notify(PhilosNPC npc, Player user, String message) {
        if (npc == null || user == null || npc.isSystem()) return;
        if (npc.getOwnerUuid() == null || npc.getOwnerUuid().equals(user.getUniqueId())) return;
        Player owner = Bukkit.getPlayer(npc.getOwnerUuid());
        if (owner != null) {
            owner.sendMessage(PhilosNPCPlugin.cc(message));
        }
    }

    /**
     * 物品简述：如 "64x DIAMOND"
     */
    public static String fmtItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return "无";
        return item.getAmount() + "x" + item.getType().name();
    }
}
