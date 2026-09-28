package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 跨世界转移仓库界面（54格，0-44物品展示，45个/页）
 * 仓库按玩家全局共享（跨世界），仅存白名单物品（PDC rpgforge:item-id）
 */
public class TransferGui {

    public static final int ITEMS_PER_PAGE = 45;

    /**
     * 转移仓库界面：0-44展示仓库物品，
     * 45上一页 / 47使用说明 / 49一键取回全部 / 51下一页 / 53返回
     */
    public static Inventory transferVaultGui(UUID ownerUuid, int page, InventoryHolder holder) {
        List<ItemStack> vault = PhilosNPCPlugin.instance().npcManager().getTransferVault(ownerUuid);

        int totalPages = Math.max(1, (vault.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory inv = Bukkit.createInventory(holder, 54,
                PhilosNPCPlugin.cc("&5&l跨世界转移仓库 - 第" + (page + 1) + "/" + totalPages + "页"));

        int start = page * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int index = start + i;
            if (index < vault.size()) {
                inv.setItem(i, vault.get(index).clone());
            }
        }

        boolean hasPrev = page > 0;
        inv.setItem(45, createItem(
                hasPrev ? Material.ARROW : Material.LEVER,
                hasPrev ? "&a上一页" : "&7已是第一页",
                hasPrev ? "&7左键点击上一页" : "&c没有上一页了"
        ));

        // slot 47: 使用说明
        inv.setItem(47, createItem(
                Material.HOPPER,
                "&d存入 / 取出",
                "&7存入：手持白名单物品点击仓库区",
                "&7     或对背包物品 shift 点击",
                "&7取出：空手点击仓库中的物品",
                "&7仓库跨世界共享，任何世界的",
                "&7转移NPC都能取回这里的东西"
        ));

        if (vault.isEmpty()) {
            inv.setItem(49, createItem(
                    Material.ENDER_CHEST,
                    "&7仓库是空的",
                    "&7RPG世界获得的代币等",
                    "&7白名单物品可存入这里"
            ));
        } else {
            int total = 0;
            for (ItemStack item : vault) {
                if (item != null) total += item.getAmount();
            }
            inv.setItem(49, createItem(
                    Material.ENDER_CHEST,
                    "&d&l一键取回全部",
                    "&7共有 &f" + vault.size() + " &7组 / &f" + total + " &7个物品",
                    "&7超出背包容量的物品",
                    "&7将掉落在你脚下",
                    "&e左键点击取回"
            ));
        }

        boolean hasNext = page < totalPages - 1;
        inv.setItem(51, createItem(
                hasNext ? Material.ARROW : Material.LEVER,
                hasNext ? "&a下一页" : "&7已是最后一页",
                hasNext ? "&7左键点击下一页" : "&c没有下一页了"
        ));

        inv.setItem(53, createItem(
                Material.BARRIER,
                "&c返回",
                "&7左键点击返回功能界面"
        ));

        for (int slot : new int[]{46, 48, 50, 52}) {
            inv.setItem(slot, createItem(Material.GRAY_STAINED_GLASS_PANE, "&r "));
        }

        return inv;
    }

    private static ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(PhilosNPCPlugin.cc(name));
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(PhilosNPCPlugin.cc(line));
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
