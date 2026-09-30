package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 礼包发放功能：构建礼包容器物品、领取判定与发放
 */
public class GiftPackFeature {

    /**
     * 领取礼包：普通玩家每种只能领一次，管理员不限次
     * @return 是否领取成功
     */
    public static boolean claim(Player player, GiftPack pack) {
        boolean admin = player.hasPermission("philosnpc.admin");
        if (!admin && pack.isClaimedBy(player.getUniqueId())) {
            return false;
        }

        ItemStack item = buildContainerItem(pack);
        var leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }

        if (!admin) {
            pack.markClaimed(player.getUniqueId());
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        player.sendMessage(PhilosNPCPlugin.cc("&a已领取礼包: &d&l" + pack.getName()));
        return true;
    }

    /**
     * 构建礼包容器物品：潜影盒（真实BlockState填充）或收纳袋（BundleMeta填充），
     * 附礼包名与内容物预览lore
     */
    public static ItemStack buildContainerItem(GiftPack pack) {
        ItemStack item = new ItemStack(pack.containerMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<ItemStack> contents = pack.getContents();

            if (pack.isShulker() && meta instanceof BlockStateMeta bsm
                    && bsm.getBlockState() instanceof ShulkerBox box) {
                for (int i = 0; i < contents.size() && i < 27; i++) {
                    box.getInventory().setItem(i, contents.get(i).clone());
                }
                bsm.setBlockState(box);
            } else if (meta instanceof BundleMeta bm) {
                // 1.21.2+ 收纳袋上限16组，超出部分丢弃避免生成非法物品
                int added = 0;
                for (ItemStack content : contents) {
                    if (added >= 16) break;
                    bm.addItem(content.clone());
                    added++;
                }
            }

            meta.setDisplayName(PhilosNPCPlugin.cc("&d&l" + pack.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(PhilosNPCPlugin.cc("&7内含 &f" + contents.size() + " &7组物品:"));
            int shown = Math.min(5, contents.size());
            for (int i = 0; i < shown; i++) {
                ItemStack content = contents.get(i);
                String name = displayNameOf(content);
                lore.add(PhilosNPCPlugin.cc("&8- &f" + content.getAmount() + "x " + name));
            }
            if (contents.size() > 5) {
                lore.add(PhilosNPCPlugin.cc("&8...等共 " + contents.size() + " 组"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 物品展示名：自定义名或材质名
     */
    private static String displayNameOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return item.getType().name();
    }
}
