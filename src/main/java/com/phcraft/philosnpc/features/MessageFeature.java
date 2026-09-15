package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.entity.Player;

/**
 * 留言功能
 * NPC主人可以设置留言，玩家右键NPC可以查看留言
 */
public class MessageFeature {

    /**
     * 设置留言
     * @param npc NPC对象
     * @param message 留言内容
     */
    public static void setMessage(PhilosNPC npc, String message) {
        if (npc == null) return;
        npc.setMessage(message != null ? message : "");
    }

    /**
     * 获取留言
     * @param npc NPC对象
     * @return 留言内容
     */
    public static String getMessage(PhilosNPC npc) {
        if (npc == null) return "";
        String msg = npc.getMessage();
        return msg != null ? msg : "";
    }

    /**
     * 向玩家显示留言（发消息形式）
     * @param player 玩家
     * @param npc NPC对象
     */
    public static void showMessage(Player player, PhilosNPC npc) {
        if (player == null || npc == null) return;

        String message = getMessage(npc);
        if (message.isEmpty()) {
            player.sendMessage(PhilosNPCPlugin.cc(
                    "&7[&b" + npc.getDisplayName() + "&7] &f（该NPC暂无留言）"));
            return;
        }

        // 支持换行符，逐行发送
        String[] lines = message.split("\\\\n|\\n");
        player.sendMessage(PhilosNPCPlugin.cc(
                "&7&m-----[ &b" + npc.getDisplayName() + " 的留言 &7&m]-----"));
        for (String line : lines) {
            player.sendMessage(PhilosNPCPlugin.cc("&f" + line));
        }
        player.sendMessage(PhilosNPCPlugin.cc(
                "&7&m---------------------------------"));
    }
}
