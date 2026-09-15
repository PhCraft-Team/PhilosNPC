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
                    player.sendMessage(PhilosNPCPlugin.cc("&cNPC不存在"));
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
                player.sendMessage(PhilosNPCPlugin.cc("&a已重载"));
            }
            default -> {
                // 未知子命令：打开NPC列表
                gui.openNPCListGui(player, 0);
            }
        }
        return true;
    }

    private PhilosNPC findByShortId(NPCManager mgr, String shortId) {
        for (PhilosNPC npc : mgr.getAllNPCs()) {
            if (npc.getId().startsWith(shortId)) return npc;
        }
        return null;
    }
}
