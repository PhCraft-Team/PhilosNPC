package com.phcraft.philosnpc.features;

import com.phcraft.philosnpc.PhilosNPCPlugin;
import com.phcraft.philosnpc.npc.PhilosNPC;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * 点歌功能
 * 玩家可以选择NPC唱片栏中的唱片进行播放
 */
public class JukeboxFeature {

    private static final Map<Material, Sound> DISC_SOUND_MAP = new HashMap<>();

    static {
        // 唱片材质 -> 对应音效 的映射
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_13, Sound.MUSIC_DISC_13);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_CAT, Sound.MUSIC_DISC_CAT);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_BLOCKS, Sound.MUSIC_DISC_BLOCKS);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_CHIRP, Sound.MUSIC_DISC_CHIRP);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_FAR, Sound.MUSIC_DISC_FAR);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_MALL, Sound.MUSIC_DISC_MALL);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_MELLOHI, Sound.MUSIC_DISC_MELLOHI);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_STAL, Sound.MUSIC_DISC_STAL);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_STRAD, Sound.MUSIC_DISC_STRAD);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_WARD, Sound.MUSIC_DISC_WARD);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_11, Sound.MUSIC_DISC_11);
        DISC_SOUND_MAP.put(Material.MUSIC_DISC_WAIT, Sound.MUSIC_DISC_WAIT);

        // 尝试添加较新版本的唱片（兼容旧版本）
        try {
            DISC_SOUND_MAP.put(Material.MUSIC_DISC_PIGSTEP, Sound.MUSIC_DISC_PIGSTEP);
        } catch (NoSuchFieldError ignored) {}
        try {
            DISC_SOUND_MAP.put(Material.MUSIC_DISC_OTHERSIDE, Sound.MUSIC_DISC_OTHERSIDE);
        } catch (NoSuchFieldError ignored) {}
        try {
            DISC_SOUND_MAP.put(Material.MUSIC_DISC_5, Sound.MUSIC_DISC_5);
        } catch (NoSuchFieldError ignored) {}
        try {
            DISC_SOUND_MAP.put(Material.MUSIC_DISC_RELIC, Sound.MUSIC_DISC_RELIC);
        } catch (NoSuchFieldError ignored) {}
    }

    /**
     * 播放指定槽位的唱片
     * @param player 玩家
     * @param npc NPC对象
     * @param slotIndex 槽位索引（0-8）
     * @return 是否播放成功
     */
    public static boolean playDisc(Player player, PhilosNPC npc, int slotIndex) {
        if (player == null || npc == null) return false;

        ItemStack[] discs = npc.getJukeboxDiscs();
        if (slotIndex < 0 || slotIndex >= discs.length) return false;

        ItemStack disc = discs[slotIndex];
        if (disc == null || disc.getType() == Material.AIR) {
            player.sendMessage(PhilosNPCPlugin.cc("&c该槽位没有唱片"));
            return false;
        }

        Sound sound = getDiscSound(disc.getType());
        if (sound == null) {
            player.sendMessage(PhilosNPCPlugin.cc("&c无法播放该唱片"));
            return false;
        }

        // 在NPC位置播放唱片音效
        Location npcLoc = npc.getLocation();
        player.playSound(npcLoc, sound, 1.0f, 1.0f);

        String discName = getDiscName(disc);
        player.sendMessage(PhilosNPCPlugin.cc("&a正在播放: &f" + discName));

        return true;
    }

    /**
     * 停止播放唱片
     * 由于Bukkit API限制，无法精确停止单个唱片音效，
     * 这里通过停止所有唱片音效来实现
     * @param player 玩家
     * @param npc NPC对象
     */
    public static void stopDisc(Player player, PhilosNPC npc) {
        if (player == null) return;

        // 停止所有唱片音效
        for (Sound sound : DISC_SOUND_MAP.values()) {
            try {
                player.stopSound(sound);
            } catch (Exception ignored) {}
        }

        player.sendMessage(PhilosNPCPlugin.cc("&c已停止播放唱片"));
    }

    /**
     * 获取唱片名称
     * @param disc 唱片物品
     * @return 唱片名称
     */
    public static String getDiscName(ItemStack disc) {
        if (disc == null) return "未知唱片";

        ItemMeta meta = disc.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        // 根据材质类型返回友好名称
        Material type = disc.getType();
        switch (type) {
            case MUSIC_DISC_13: return "13号唱片";
            case MUSIC_DISC_CAT: return "Cat唱片";
            case MUSIC_DISC_BLOCKS: return "Blocks唱片";
            case MUSIC_DISC_CHIRP: return "Chirp唱片";
            case MUSIC_DISC_FAR: return "Far唱片";
            case MUSIC_DISC_MALL: return "Mall唱片";
            case MUSIC_DISC_MELLOHI: return "Mellohi唱片";
            case MUSIC_DISC_STAL: return "Stal唱片";
            case MUSIC_DISC_STRAD: return "Strad唱片";
            case MUSIC_DISC_WARD: return "Ward唱片";
            case MUSIC_DISC_11: return "11号唱片";
            case MUSIC_DISC_WAIT: return "Wait唱片";
            default:
                try {
                    if (type == Material.MUSIC_DISC_PIGSTEP) return "Pigstep唱片";
                    if (type == Material.MUSIC_DISC_OTHERSIDE) return "Otherside唱片";
                    if (type == Material.MUSIC_DISC_5) return "5号唱片";
                    if (type == Material.MUSIC_DISC_RELIC) return "Relic唱片";
                } catch (NoSuchFieldError ignored) {}
                return type.name().replace("MUSIC_DISC_", "") + "唱片";
        }
    }

    /**
     * 根据唱片材质获取对应的Sound
     * @param discType 唱片材质
     * @return 对应的Sound，找不到返回null
     */
    private static Sound getDiscSound(Material discType) {
        return DISC_SOUND_MAP.get(discType);
    }

    /**
     * 判断物品是否为唱片
     * @param item 物品
     * @return 是否为唱片
     */
    public static boolean isMusicDisc(ItemStack item) {
        if (item == null) return false;
        return DISC_SOUND_MAP.containsKey(item.getType());
    }
}
