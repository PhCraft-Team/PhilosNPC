package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.PhilosNPC;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 传送功能
 * 玩家可以花费固定金额传送到NPC设定的目标点
 */
public class TeleportFeature {

    private static final double TELEPORT_COST = 5.0;

    /**
     * 设置传送目标点为editor当前位置
     * @param editor 编辑者玩家
     * @param npc NPC对象
     */
    public static void setupTeleport(Player editor, PhilosNPC npc) {
        if (editor == null || npc == null) return;
        npc.setTeleportTarget(editor.getLocation().clone());
        editor.sendMessage(PhilosNPCPlugin.cc("&a传送目标点已设置为当前位置"));
    }

    /**
     * 执行传送
     * 扣除费用（给系统），传送到目标点
     * @param player 玩家
     * @param npc NPC对象
     * @return 是否传送成功
     */
    public static boolean executeTeleport(Player player, PhilosNPC npc) {
        if (player == null || npc == null) return false;

        Location target = npc.getTeleportTarget();
        if (target == null) {
            player.sendMessage(PhilosNPCPlugin.cc("&c未设置传送目标点"));
            return false;
        }

        double cost = npc.getEffectiveTeleportCost();

        // 扣除费用
        if (PhilosNPCPlugin.economy() != null && cost > 0) {
            EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, cost);
            if (!resp.transactionSuccess()) {
                player.sendMessage(PhilosNPCPlugin.cc(
                        "&c金币不足，传送需要 " + cost + " 金币"));
                return false;
            }
        }

        // 执行传送
        player.teleport(target);
        player.sendMessage(PhilosNPCPlugin.cc(
                "&a已传送，花费 " + cost + " 金币"));

        return true;
    }

    /**
     * 获取传送费用
     * @return 传送费用（固定5.0）
     */
    public static double getTeleportCost() {
        return TELEPORT_COST;
    }
}
