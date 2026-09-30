package com.phcraft.philosnpc;

import com.phcraft.philosnpc.gui.GuiManager;
import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;

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
                    if (!PhilosNPCPlugin.economy().has(player, PluginSettings.createCost())) {
                        player.sendMessage(PhilosNPCPlugin.cc(
                                "&c金币不足！创建NPC需要 " + PluginSettings.createCost() + " 金币"));
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
                if (!npc.isSystem() && mgr.isShopInventoryMigrationBlocked(npc.getOwnerUuid())) {
                    Set<String> worlds = mgr.getShopInventoryMigrationReviewWorlds(npc.getOwnerUuid());
                    player.sendMessage(PhilosNPCPlugin.cc("&c该店主在世界 &f" + String.join(", ", worlds)
                            + " &c的商店库存待管理员核账，已暂停移动。请管理员停服后按库存恢复文档逐世界核对"));
                    return true;
                }
                mgr.despawnNPC(npc);
                org.bukkit.Location target = player.getLocation();
                boolean crossWorld = !target.getWorld().getName().equals(npc.getWorldName());
                npc.setLocation(target);
                if (crossWorld && !npc.isSystem()) {
                    // NPC跨世界移动：库存数据归属立即切换到新世界侧，防止继续持有旧世界的共享数组引用
                    npc.setShopInventory(mgr.getSharedShopInventory(npc.getOwnerUuid(), target.getWorld().getName()));
                }
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
                if (!npc.isSystem() && mgr.isShopInventoryMigrationBlocked(npc.getOwnerUuid())) {
                    Set<String> worlds = mgr.getShopInventoryMigrationReviewWorlds(npc.getOwnerUuid());
                    player.sendMessage(PhilosNPCPlugin.cc("&c该店主在世界 &f" + String.join(", ", worlds)
                            + " &c的商店库存待管理员核账，已暂停删除。请管理员停服后按库存恢复文档逐世界核对"));
                    return true;
                }
                if (mgr.deleteNPC(npc.getId())) {
                    player.sendMessage(PhilosNPCPlugin.cc("&cNPC已删除"));
                }
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
                // 安全付费传送：失败自动退款，付款结果未知时挂锁定待管理员核对
                com.phcraft.philosnpc.features.TeleportFeature.teleport(player, npc.getLocation(), PluginSettings.tpToNpcCost());
            }
            case "reconcileteleport" -> {
                if (!player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c你没有权限"));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c核对账本后使用: /" + label + " reconcileteleport <在线玩家UUID>"));
                    return true;
                }
                Player target;
                try {
                    target = plugin.getServer().getPlayer(java.util.UUID.fromString(args[1]));
                } catch (IllegalArgumentException ex) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c无效的玩家UUID"));
                    return true;
                }
                if (target == null) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c请让待核对玩家上线后操作"));
                    return true;
                }
                boolean cleared = com.phcraft.philosnpc.features.TeleportFeature.reconcilePayment(target);
                gui.clearUncertainPayment(target.getUniqueId());
                if (cleared) plugin.getLogger().warning("管理员 " + player.getUniqueId()
                        + " 确认已核对传送付款，解除玩家 " + target.getUniqueId() + " 的付款锁定");
                player.sendMessage(PhilosNPCPlugin.cc(cleared
                        ? "&a已解除传送付款锁定与商店消费暂停；未自动扣款或退款。"
                        : "&a已解除商店消费暂停；该玩家没有待核对的传送付款。"));
            }
            case "reload" -> {
                if (!player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c你没有权限"));
                    return true;
                }
                // 先安全结束商店背包编辑会话（写回并解锁），避免重载后旧界面把旧内容写回新数据
                gui.closeAllShopEditSessions();
                plugin.npcManager().saveAll();
                plugin.npcManager().despawnAll();
                plugin.reloadConfig();
                PluginSettings.load(plugin.getConfig());
                plugin.npcManager().loadAll();
                if (!plugin.npcManager().isStorageReady()) {
                    player.sendMessage(PhilosNPCPlugin.cc("&c存储加载或库存迁移失败，插件将禁用以保护数据"));
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                    return true;
                }
                player.sendMessage(PhilosNPCPlugin.cc("&a已重载（配置与费用已生效）"));
            }
            case "help" -> {
                player.sendMessage(PhilosNPCPlugin.cc("&b&l===== PhilosNPC 命令 ====="));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " create &7在脚下创建NPC，花 " + PluginSettings.createCost() + " 金币"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " list &7打开我的NPC列表"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " edit <id> &7打开NPC编辑界面"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " move <id> &7把NPC移到你脚下"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " delete <id> &7删除NPC，退回 &6" + PluginSettings.deleteRefund() + " 金币"));
                player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " tp <id> &7传送到NPC，花 " + PluginSettings.tpToNpcCost() + " 金币"));
                player.sendMessage(PhilosNPCPlugin.cc("&7ID在列表和编辑界面可查，输入前几位即可匹配"));
                if (player.hasPermission("philosnpc.admin")) {
                    player.sendMessage(PhilosNPCPlugin.cc("&b&l----- 管理员 -----"));
                    player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " syscreate <类型> &7创建系统NPC，如 ZOMBIE、PLAYER:Notch"));
                    player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " syslist &7查看系统NPC列表"));
                    player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " reload &7重载插件"));
                    player.sendMessage(PhilosNPCPlugin.cc("&e/" + label + " reconcileteleport <UUID> &7核对账本后解除在线玩家传送付款锁定"));
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
