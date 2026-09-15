package com.phcraft.philosnpc;

import com.phcraft.philosnpc.gui.GuiManager;
import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PhilosCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令只能由玩家执行");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player, label);
            return true;
        }

        var plugin = PhilosNPCPlugin.instance();
        NPCManager mgr = plugin.npcManager();
        GuiManager gui = plugin.guiManager();

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!player.hasPermission("philosnpc.create")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c你没有权限创建NPC"));
                    return true;
                }
                if (PhilosNPCPlugin.economy() != null) {
                    if (!PhilosNPCPlugin.economy().has(player, PhilosNPCPlugin.CREATE_COST)) {
                        player.sendMessage(PhilosNPCPlugin.cc(
                                "&c金币不足！创建NPC需要 " + PhilosNPCPlugin.CREATE_COST + " 金币"));
                        return true;
                    }
                }
                PhilosNPC npc = mgr.createNPC(player);
                if (npc != null) {
                    gui.openMainGui(player, npc);
                }
            }
            case "list" -> {
                List<PhilosNPC> npcs = mgr.getNPCsByOwner(player.getUniqueId());
                if (npcs.isEmpty()) {
                    player.sendMessage(PhilosNPCPlugin.cc("&7你还没有创建任何NPC"));
                    return true;
                }
                player.sendMessage(PhilosNPCPlugin.cc("&b&l=== 你的NPC列表 ==="));
                for (PhilosNPC npc : npcs) {
                    String shortId = npc.getId().substring(0, 8);
                    String world = npc.getLocation() != null && npc.getLocation().getWorld() != null
                            ? npc.getLocation().getWorld().getName() : "unknown";
                    int x = npc.getLocation() != null ? npc.getLocation().getBlockX() : 0;
                    int y = npc.getLocation() != null ? npc.getLocation().getBlockY() : 0;
                    int z = npc.getLocation() != null ? npc.getLocation().getBlockZ() : 0;
                    player.sendMessage(PhilosNPCPlugin.cc(String.format(
                            "&b%s &7| &f%s &7| &7%s @ %d,%d,%d &7| 功能: %d/4",
                            shortId, npc.getDisplayName(), world, x, y, z, npc.getFeatures().size())));
                }
                player.sendMessage(PhilosNPCPlugin.cc("&7使用 &f/" + label + " edit <id> &7编辑NPC"));
            }
            case "edit" -> {
                if (args.length < 2) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c用法: /" + label + " edit <id>"));
                    return true;
                }
                PhilosNPC npc = mgr.getNPC(args[1]);
                if (npc == null) {
                    npc = findByShortId(mgr, args[1]);
                }
                if (npc == null) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c找不到ID为 " + args[1] + " 的NPC"));
                    return true;
                }
                if (!npc.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c这不是你的NPC，无法编辑"));
                    return true;
                }
                gui.openMainGui(player, npc);
            }
            case "move" -> {
                if (args.length < 2) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c用法: /" + label + " move <id>"));
                    return true;
                }
                PhilosNPC npc = mgr.getNPC(args[1]);
                if (npc == null) npc = findByShortId(mgr, args[1]);
                if (npc == null) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c找不到该NPC"));
                    return true;
                }
                if (!npc.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c这不是你的NPC"));
                    return true;
                }
                mgr.despawnNPC(npc);
                npc.setLocation(player.getLocation());
                mgr.spawnNPC(npc);
                mgr.saveAll();
                player.sendMessage(PhilosNPCPlugin.cc("&aNPC已移动到当前位置"));
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c用法: /" + label + " delete <id>"));
                    return true;
                }
                PhilosNPC npc = mgr.getNPC(args[1]);
                if (npc == null) npc = findByShortId(mgr, args[1]);
                if (npc == null) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c找不到该NPC"));
                    return true;
                }
                if (!npc.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c这不是你的NPC"));
                    return true;
                }
                mgr.deleteNPC(npc.getId());
                player.sendMessage(PhilosNPCPlugin.cc("&cNPC已删除"));
            }
            case "tp" -> {
                if (args.length < 2) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c用法: /" + label + " tp <id>"));
                    return true;
                }
                PhilosNPC npc = mgr.getNPC(args[1]);
                if (npc == null) npc = findByShortId(mgr, args[1]);
                if (npc == null) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c找不到该NPC"));
                    return true;
                }
                if (!npc.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c这不是你的NPC"));
                    return true;
                }
                if (PhilosNPCPlugin.economy() != null) {
                    EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, PhilosNPCPlugin.TP_TO_NPC_COST);
                    if (!resp.transactionSuccess()) {
                        player.sendMessage(PhilosNPCPlugin.cc("&c金币不足！传送需要 " + PhilosNPCPlugin.TP_TO_NPC_COST + " 金币"));
                        return true;
                    }
                }
                player.teleport(npc.getLocation());
                player.sendMessage(PhilosNPCPlugin.cc("&a已传送到NPC，花费 " + PhilosNPCPlugin.TP_TO_NPC_COST + " 金币"));
            }
            case "reload" -> {
                if (!player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c你没有权限"));
                    return true;
                }
                plugin.npcManager().saveAll();
                plugin.npcManager().despawnAll();
                plugin.reloadConfig();
                plugin.npcManager().loadAll();
                player.sendMessage(PhilosNPCPlugin.cc("&a配置已重载"));
            }
            default -> sendHelp(player, label);
        }
        return true;
    }

    private PhilosNPC findByShortId(NPCManager mgr, String shortId) {
        for (PhilosNPC npc : mgr.getAllNPCs()) {
            if (npc.getId().startsWith(shortId)) return npc;
        }
        return null;
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(PhilosNPCPlugin.cc("&b&l===== PhilosNPC 帮助 ====="));
        player.sendMessage(PhilosNPCPlugin.cc("&b/" + label + " create &7- 在当前位置创建NPC (花费" + PhilosNPCPlugin.CREATE_COST + "金币)"));
        player.sendMessage(PhilosNPCPlugin.cc("&b/" + label + " list &7- 查看你的NPC列表"));
        player.sendMessage(PhilosNPCPlugin.cc("&b/" + label + " edit <id> &7- 打开NPC编辑界面"));
        player.sendMessage(PhilosNPCPlugin.cc("&b/" + label + " move <id> &7- 将NPC移动到当前位置"));
        player.sendMessage(PhilosNPCPlugin.cc("&b/" + label + " delete <id> &7- 删除NPC"));
        player.sendMessage(PhilosNPCPlugin.cc("&b/" + label + " tp <id> &7- 传送到NPC (花费" + PhilosNPCPlugin.TP_TO_NPC_COST + "金币)"));
        if (player.hasPermission("philosnpc.admin")) {
            player.sendMessage(PhilosNPCPlugin.cc("&b/" + label + " reload &7- 重载配置 (管理员)"));
        }
    }
}
