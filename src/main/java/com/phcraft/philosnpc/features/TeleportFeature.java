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

        return teleport(player, target, cost);
    }

    public static boolean teleport(Player player, Location target, double cost) {
        if (target == null || target.getWorld() == null || !Double.isFinite(cost) || cost < 0) {
            player.sendMessage(PhilosNPCPlugin.cc("&c传送目标或费用无效"));
            return false;
        }
        var economy = PhilosNPCPlugin.economy();
        Payments.Result payment = Payments.transfer(economy, player, null, cost);
        if (payment != Payments.Result.SUCCESS) {
            player.sendMessage(PhilosNPCPlugin.cc("&c传送付款未完成；若余额异常，请联系管理员核对。"));
            if (payment == Payments.Result.UNCERTAIN) {
                PhilosNPCPlugin.instance().getLogger().severe("传送付款结果未知：player="
                        + player.getUniqueId() + ", cost=" + cost);
            }
            return false;
        }
        boolean teleported;
        try {
            teleported = player.teleport(target);
        } catch (RuntimeException ex) {
            teleported = false;
        }
        if (!teleported) {
            boolean refunded = cost == 0;
            if (cost > 0) {
                try {
                    EconomyResponse refund = economy.depositPlayer(player, cost);
                    refunded = refund != null && refund.transactionSuccess();
                } catch (RuntimeException ex) {
                    // 禁止重试结果未知的退款。
                }
            }
            player.sendMessage(PhilosNPCPlugin.cc(refunded ? "&c传送未成功，费用已退回。" : "&c传送未成功，退款异常，请联系管理员。"));
            if (!refunded) PhilosNPCPlugin.instance().getLogger().severe("传送退款需核对：player="
                    + player.getUniqueId() + ", cost=" + cost);
            return false;
        }
        player.sendMessage(PhilosNPCPlugin.cc("&a已传送，花费 " + cost + " 金币"));
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
