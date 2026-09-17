package com.phcraft.philosnpc;

import com.phcraft.philosnpc.gui.GuiManager;
import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PhilosCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("仅玩家可执行");
            return true;
        }

        var plugin = PhilosNPCPlugin.instance();
        NPCManager mgr = plugin.npcManager();
        GuiManager gui = plugin.guiManager();

        // 无参数：打开NPC列表
        if (args.length == 0) {
            gui.openNPCListGui(player, 0);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (!player.hasPermission("philosnpc.create")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c无权限"));
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
            case "syscreate" -> {
                if (!player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c只有管理员可以创建系统NPC"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c用法: /" + label + " syscreate <类型>"));
                    player.sendMessage(PhilosNPCPlugin.cc("&7类型示例: &fZOMBIE, SKELETON, CREEPER, PLAYER:Notch"));
                    return true;
                }
                String entityType = args[1].toUpperCase();
                if (!entityType.startsWith("PLAYER:")) {
                    try {
                        org.bukkit.entity.EntityType.valueOf(entityType);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(PhilosNPCPlugin.cc("&c未知实体类型: " + entityType));
                        return true;
                    }
                }
                PhilosNPC sysNpc = mgr.createSystemNPC(player, entityType);
                if (sysNpc != null) {
                    gui.openMainGui(player, sysNpc);
                }
            }
            case "list" -> {
                // 打开NPC列表GUI
                gui.openNPCListGui(player, 0);
            }
            case "syslist" -> {
                if (!player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c只有管理员可以查看系统NPC列表"));
                    return true;
                }
                gui.openNPCListGui(player, 0, true);
            }
            case "edit" -> {
                if (args.length < 2) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c用法: /" + label + " edit <id>"));
                    return true;
                }
                PhilosNPC npc = mgr.findNPC(args[1]);
                if (npc == null) {
                    player.sendMessage(PhilosNPCPlugin.cc(notFoundMsg(label, mgr, args[1])));
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
                PhilosNPC npc = mgr.findNPC(args[1]);
                if (npc == null) {
                    player.sendMessage(PhilosNPCPlugin.cc(notFoundMsg(label, mgr, args[1])));
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
                player.sendMessage(PhilosNPCPlugin.cc("&aNPC已移到你脚下"));
            }
            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c用法: /" + label + " delete <id>"));
                    return true;
                }
                PhilosNPC npc = mgr.findNPC(args[1]);
                if (npc == null) {
                    player.sendMessage(PhilosNPCPlugin.cc(notFoundMsg(label, mgr, args[1])));
                    return true;
                }
                if (!npc.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c不是你的NPC"));
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
                PhilosNPC npc = mgr.findNPC(args[1]);
                if (npc == null) {
                    player.sendMessage(PhilosNPCPlugin.cc(notFoundMsg(label, mgr, args[1])));
                    return true;
                }
                if (!npc.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c这不是你的NPC"));
                    return true;
                }
                if (PhilosNPCPlugin.economy() != null) {
                    EconomyResponse resp = PhilosNPCPlugin.economy().withdrawPlayer(player, PhilosNPCPlugin.TP_TO_NPC_COST);
                    if (!resp.transactionSuccess()) {
                        player.sendMessage(PhilosNPCPlugin.cc("&c金币不够，传送需要 " + PhilosNPCPlugin.TP_TO_NPC_COST + " 金币"));
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
                player.sendMessage(PhilosNPCPlugin.cc("&a已重载"));
            }
            case "help" -> {
                player.sendMessage(PhilosNPCPlugin.cc("&b&l===== PhilosNPC 命令 ====="));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " create &7在脚下创建NPC，花 " + PhilosNPCPlugin.CREATE_COST + " 金币"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " list &7打开我的NPC列表"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " edit <id> &7打开NPC编辑界面"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " move <id> &7把NPC移到你脚下"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " delete <id> &7删除NPC，不可恢复"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " tp <id> &7传送到NPC，花 " + PhilosNPCPlugin.TP_TO_NPC_COST + " 金币"));
                player.sendMessage(PhilosNPCPlugin.cc("&7ID在列表和编辑界面可查，输入前几位即可匹配"));
                if (player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&b&l----- 管理员 -----"));
                    player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " syscreate <类型> &7创建系统NPC，如 ZOMBIE、PLAYER:Notch"));
                    player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " syslist &7查看系统NPC列表"));
                    player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " reload &7重载插件"));
                }
            }
            default -> {
                player.sendMessage(PhilosNPCPlugin.cc("&c未知子命令，输入 /" + label + " help 查看帮助"));
            }
        }
        return true;
    }

    /**
     * ID找不到时的提示：多匹配提示歧义，无匹配引导查列表
     */
    private String notFoundMsg(String label, NPCManager mgr, String input) {
        int matches = mgr.countNPCMatches(input);
        if (matches > 1) {
            return "&cID不唯一，匹配到 " + matches + " 个NPC，请输入完整ID";
        }
        return "&c找不到NPC: &f" + input + "&c。输入 /" + label + " list 查看你的NPC";
    }
}
