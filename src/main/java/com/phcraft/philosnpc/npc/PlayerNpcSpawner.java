package com.phcraft.philosnpc.npc;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.phcraft.philosnpc.PhilosNPCPlugin;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * 玩家形态NPC：通过PacketEvents发包渲染虚拟玩家实体。
 * 完整玩家模型（皮肤/袖子/手部正确渲染），服务端无真实体。
 * 头顶名牌：原生名牌（来自profile名，仅支持英文数字）用记分板队伍隐藏，
 * 改用真实TextDisplay实体显示完整名字（支持中文/彩色）。
 */
public class PlayerNpcSpawner {

    private static final Random RANDOM = new Random();
    // 隐藏原生名牌的记分板队伍名
    private static final String HIDE_TEAM_NAME = "philosnpc_hide";

    /**
     * 虚拟NPC运行时状态
     */
    public static class VirtualNpc {
        public final PhilosNPC data;
        public final int entityId;
        // 坐姿载具（隐形TextDisplay）实体ID：玩家模型仅作为乘客时才渲染坐姿
        public final int vehicleEntityId;
        public final UUID fakeUuid;
        public final String profileName;
        // 头顶名牌（真实TextDisplay实体，显示完整名字）
        TextDisplay nameTag;
        // 村民式转头状态
        double bodyYaw;
        double headYawOffset = 0;      // 头相对身体的偏航
        double targetHeadYaw = 0;
        double targetPitch = 0;
        double curPitch = 0;
        long nextSense = 0;
        int idleCooldown = 0;

        VirtualNpc(PhilosNPC data, int entityId, int vehicleEntityId, UUID fakeUuid, String profileName, double bodyYaw) {
            this.data = data;
            this.entityId = entityId;
            this.vehicleEntityId = vehicleEntityId;
            this.fakeUuid = fakeUuid;
            this.profileName = profileName;
            this.bodyYaw = bodyYaw;
        }
    }

    // npcId -> 虚拟NPC
    private final Map<String, VirtualNpc> npcs = new HashMap<>();
    // 虚拟实体ID -> npcId（交互反查）
    private final Map<Integer, String> entityIdToNpcId = new HashMap<>();
    private int nextEntityId = -10000;

    // ===== 生命周期 =====

    /**
     * 生成玩家形态NPC：对当前所有在线玩家广播
     */
    public void spawn(PhilosNPC npc) {
        if (npcs.containsKey(npc.getId())) return;

        // 确定性UUID：重载后保持一致
        UUID fakeUuid = UUID.nameUUIDFromBytes(("PhilosNPC:" + npc.getId()).getBytes());
        // profile名须为合法用户名格式（仅英数下划线，<=16字符）
        // 该名字仅作内部标识，原生名牌由队伍隐藏，可见名字在TextDisplay名牌上
        String profileName = "NPC_" + Integer.toUnsignedString(fakeUuid.hashCode(), 36);
        if (profileName.length() > 16) profileName = profileName.substring(0, 16);

        Location loc = new Location(
                npc.getLocation().getX(),
                npc.getLocation().getY(),
                npc.getLocation().getZ(),
                npc.getLocation().getYaw(),
                npc.getLocation().getPitch());

        VirtualNpc v = new VirtualNpc(npc, nextEntityId--, nextEntityId--, fakeUuid, profileName, loc.getYaw());
        npcs.put(npc.getId(), v);
        entityIdToNpcId.put(v.entityId, npc.getId());

        hideNameplate(profileName);
        spawnNameTag(v);

        for (Player p : Bukkit.getOnlinePlayers()) {
            sendNpcTo(p, v);
        }
    }

    /**
     * 玩家上线时补发所有虚拟NPC
     */
    public void spawnAllTo(Player player) {
        for (VirtualNpc v : npcs.values()) {
            sendNpcTo(player, v);
        }
    }

    /**
     * 销毁NPC：对所有看到它的玩家广播移除
     */
    public void despawn(PhilosNPC npc) {
        VirtualNpc v = npcs.remove(npc.getId());
        if (v == null) return;
        entityIdToNpcId.remove(v.entityId);

        destroyVirtual(v);
    }

    public void despawnAll() {
        for (VirtualNpc v : npcs.values()) {
            destroyVirtual(v);
        }
        npcs.clear();
        entityIdToNpcId.clear();
        // 清理名牌隐藏队伍
        try {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(HIDE_TEAM_NAME);
            if (team != null) team.unregister();
        } catch (IllegalStateException ignored) {
        }
    }

    private void destroyVirtual(VirtualNpc v) {
        // 移除头顶名牌与原生名牌的隐藏
        if (v.nameTag != null) {
            v.nameTag.remove();
            v.nameTag = null;
        }
        unhideNameplate(v);

        // 一并销毁坐姿载具（客户端未生成的ID会被安全忽略）
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(
                v.entityId, v.vehicleEntityId);
        WrapperPlayServerPlayerInfoRemove removeInfo = new WrapperPlayServerPlayerInfoRemove(
                Collections.singletonList(v.fakeUuid));
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p, destroy);
            send(p, removeInfo);
        }
    }

    public PhilosNPC getByEntityId(int entityId) {
        String npcId = entityIdToNpcId.get(entityId);
        return npcId != null ? PhilosNPCPlugin.instance().npcManager().getNPC(npcId) : null;
    }

    // ===== 发包 =====

    private void sendNpcTo(Player viewer, VirtualNpc v) {
        PhilosNPC data = v.data;
        UserProfile profile = new UserProfile(v.fakeUuid, v.profileName);
        if (data.getSkinValue() != null) {
            profile.setTextureProperties(Collections.singletonList(
                    new TextureProperty("textures", data.getSkinValue(), data.getSkinSignature())));
        }

        // 1. 玩家信息（listed=false：不进Tab列表）
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        profile, false, 0, GameMode.SURVIVAL, null, null, 0);
        WrapperPlayServerPlayerInfoUpdate infoUpdate = new WrapperPlayServerPlayerInfoUpdate(
                WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, Collections.singletonList(info));
        send(viewer, infoUpdate);

        // 2. 生成实体（玩家类型）
        WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(
                v.entityId, Optional.of(v.fakeUuid), EntityTypes.PLAYER,
                new Vector3d(data.getLocation().getX(), data.getLocation().getY(), data.getLocation().getZ()),
                0f, (float) v.bodyYaw, 0f, 0, Optional.empty());
        send(viewer, spawn);

        // 3. 元数据：皮肤外层
        sendMetadata(viewer, v);

        // 4. 装备
        sendEquipment(viewer, v);

        // 5. 缩放
        sendScale(viewer, v);

        // 6. 坐姿：隐形TextDisplay载具 + 骑乘（玩家模型仅作为乘客时渲染坐姿）
        if (data.getPose() == NPCPose.SITTING) {
            sendSittingVehicle(viewer, v);
        }
    }

    /**
     * 生成坐姿载具：隐形TextDisplay置于NPC位置，玩家NPC作为其乘客
     */
    private void sendSittingVehicle(Player viewer, VirtualNpc v) {
        WrapperPlayServerSpawnEntity vehicleSpawn = new WrapperPlayServerSpawnEntity(
                v.vehicleEntityId, Optional.of(UUID.randomUUID()), EntityTypes.TEXT_DISPLAY,
                new Vector3d(v.data.getLocation().getX(), v.data.getLocation().getY(), v.data.getLocation().getZ()),
                0f, 0f, 0f, 0, Optional.empty());
        send(viewer, vehicleSpawn);

        WrapperPlayServerSetPassengers passengers = new WrapperPlayServerSetPassengers(
                v.vehicleEntityId, new int[]{v.entityId});
        send(viewer, passengers);
    }

    private void sendMetadata(Player viewer, VirtualNpc v) {
        List<EntityData<?>> dataList = new ArrayList<>();
        // 皮肤外层（粗模+帽/外套层），否则显示为细手臂
        // 26.2 布局：Avatar 类拆出后皮肤层从 17 移到 16，17 变为额外血量(Float)
        dataList.add(new EntityData(16, EntityDataTypes.BYTE, (byte) 127));
        // 头顶名字由TextDisplay名牌显示（玩家实体不渲染custom name），原生名牌已被队伍隐藏

        send(viewer, new WrapperPlayServerEntityMetadata(v.entityId, dataList));
    }

    private void sendEquipment(Player viewer, VirtualNpc v) {
        org.bukkit.inventory.ItemStack[] eq = v.data.getEquipment();
        List<Equipment> list = new ArrayList<>();
        if (eq[0] != null && !eq[0].getType().isAir()) {
            list.add(new Equipment(EquipmentSlot.HELMET, SpigotConversionUtil.fromBukkitItemStack(eq[0])));
        }
        if (eq[1] != null && !eq[1].getType().isAir()) {
            list.add(new Equipment(EquipmentSlot.CHEST_PLATE, SpigotConversionUtil.fromBukkitItemStack(eq[1])));
        }
        if (eq[2] != null && !eq[2].getType().isAir()) {
            list.add(new Equipment(EquipmentSlot.LEGGINGS, SpigotConversionUtil.fromBukkitItemStack(eq[2])));
        }
        if (eq[3] != null && !eq[3].getType().isAir()) {
            list.add(new Equipment(EquipmentSlot.BOOTS, SpigotConversionUtil.fromBukkitItemStack(eq[3])));
        }
        if (eq[4] != null && !eq[4].getType().isAir()) {
            list.add(new Equipment(EquipmentSlot.MAIN_HAND, SpigotConversionUtil.fromBukkitItemStack(eq[4])));
        }
        if (!list.isEmpty()) {
            send(viewer, new WrapperPlayServerEntityEquipment(v.entityId, list));
        }
    }

    private void sendScale(Player viewer, VirtualNpc v) {
        WrapperPlayServerUpdateAttributes attrs = new WrapperPlayServerUpdateAttributes(
                v.entityId,
                Collections.singletonList(new WrapperPlayServerUpdateAttributes.Property(
                        "minecraft:scale", v.data.getScale(), Collections.emptyList())));
        send(viewer, attrs);
    }

    /**
     * 同步外观（装备/缩放/名字/姿势变化后广播刷新）
     */
    public void refresh(PhilosNPC npc) {
        VirtualNpc v = npcs.get(npc.getId());
        if (v == null) return;
        updateNameTagPosition(v);
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendMetadata(p, v);
            sendEquipment(p, v);
            sendScale(p, v);
        }
    }

    /**
     * 皮肤数据就绪后重发外观：玩家信息须携带纹理重建（profile不可原地更新），
     * 先移除旧玩家信息与实体，再按新皮肤重新生成
     */
    public void updateSkin(PhilosNPC npc) {
        VirtualNpc v = npcs.get(npc.getId());
        if (v == null) return;
        WrapperPlayServerPlayerInfoRemove removeInfo = new WrapperPlayServerPlayerInfoRemove(
                Collections.singletonList(v.fakeUuid));
        WrapperPlayServerDestroyEntities destroy = new WrapperPlayServerDestroyEntities(v.entityId);
        for (Player p : Bukkit.getOnlinePlayers()) {
            send(p, removeInfo);
            send(p, destroy);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendNpcTo(p, v);
        }
    }

    // ===== 头顶名牌 =====

    /**
     * 名牌高度：站立2.35格、坐姿1.8格，随NPC缩放
     */
    private static double nameTagHeight(VirtualNpc v) {
        double scale = Math.max(0.0625, v.data.getScale());
        return (v.data.getPose() == NPCPose.SITTING ? 1.8 : 2.35) * scale;
    }

    /**
     * 生成头顶名牌（真实TextDisplay实体，Bukkit API负责版本兼容的元数据）
     */
    private void spawnNameTag(VirtualNpc v) {
        org.bukkit.Location base = v.data.getLocation();
        if (base == null || base.getWorld() == null) return;
        org.bukkit.Location tagLoc = base.clone().add(0, nameTagHeight(v), 0);
        Component name = PhilosNPCPlugin.instance().miniMessage().deserialize(
                "<!italic>" + v.data.getDisplayName().replace('§', '&'));
        v.nameTag = base.getWorld().spawn(tagLoc, TextDisplay.class, tag -> {
            tag.text(name);
            tag.setBillboard(Display.Billboard.CENTER);
            tag.setShadowed(true);
            tag.setSeeThrough(true);
            tag.setPersistent(false);
        });
    }

    /**
     * 姿势/缩放变化后同步名牌位置
     */
    private void updateNameTagPosition(VirtualNpc v) {
        if (v.nameTag == null || !v.nameTag.isValid()) return;
        org.bukkit.Location base = v.data.getLocation();
        if (base == null) return;
        v.nameTag.teleport(base.clone().add(0, nameTagHeight(v), 0));
    }

    /**
     * 名牌被意外清除时重建（第三方清理插件等）；区块未加载时跳过
     */
    private void repairNameTag(VirtualNpc v) {
        if (v.nameTag != null && v.nameTag.isValid()) return;
        org.bukkit.Location base = v.data.getLocation();
        if (base == null || base.getWorld() == null) return;
        if (!base.getWorld().isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) return;
        spawnNameTag(v);
    }

    /**
     * 用记分板队伍隐藏原生名牌（玩家实体名牌来自profile名，仅支持英文数字）
     */
    private static void hideNameplate(String profileName) {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam(HIDE_TEAM_NAME);
            if (team == null) {
                team = board.registerNewTeam(HIDE_TEAM_NAME);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
            if (!team.hasEntry(profileName)) {
                team.addEntry(profileName);
            }
        } catch (IllegalStateException ignored) {
            // 队伍被其他插件占用时忽略，最多显示内部名
        }
    }

    /**
     * 取消原生名牌隐藏（仍有其他NPC共用同一profile名时保留）
     */
    private void unhideNameplate(VirtualNpc removed) {
        for (VirtualNpc other : npcs.values()) {
            if (other != removed && other.profileName.equals(removed.profileName)) {
                return;
            }
        }
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam(HIDE_TEAM_NAME);
            if (team != null) {
                team.removeEntry(removed.profileName);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    // ===== 村民式转头动画 =====

    public void startHeadAnimation() {
        Bukkit.getScheduler().runTaskTimer(PhilosNPCPlugin.instance(), () -> {
            long tick = Bukkit.getCurrentTick();
            for (VirtualNpc v : npcs.values()) {
                if (tick >= v.nextSense) {
                    v.nextSense = tick + 5;
                    updateLookTarget(v);
                }
                animate(v);
                repairNameTag(v);
            }
        }, 20L, 1L);
    }

    private void updateLookTarget(VirtualNpc v) {
        org.bukkit.Location base = v.data.getLocation();
        Player nearest = null;
        double best = 25.0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(base.getWorld())) {
                double dist = p.getLocation().distanceSquared(base);
                if (dist < best) {
                    best = dist;
                    nearest = p;
                }
            }
        }

        if (nearest != null) {
            double dx = nearest.getX() - base.getX();
            double dy = nearest.getEyeLocation().getY() - (base.getY() + 1.62 * v.data.getScale());
            double dz = nearest.getZ() - base.getZ();
            double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double targetPitch = -Math.toDegrees(Math.atan2(dy, horizontal));
            v.targetHeadYaw = targetYaw;
            v.targetPitch = Math.max(-45, Math.min(45, targetPitch));
        } else if (v.idleCooldown <= 0) {
            v.idleCooldown = 60 + RANDOM.nextInt(140);
            v.targetHeadYaw = v.bodyYaw + (RANDOM.nextDouble() - 0.5) * 60;
            v.targetPitch = (RANDOM.nextDouble() - 0.5) * 16;
        }
    }

    private void animate(VirtualNpc v) {
        if (v.idleCooldown > 0) v.idleCooldown--;

        // 头部目标偏航（相对身体）
        double yawDiff = wrapDegrees(v.targetHeadYaw - v.bodyYaw);
        // 脖子最多偏75度
        double clamped = Math.max(-75, Math.min(75, yawDiff));
        // 平滑插值：每tick最多5度
        v.headYawOffset = approach(v.headYawOffset, clamped, 5.0);
        v.curPitch = approach(v.curPitch, v.targetPitch, 4.0);

        // 坐姿时身体不转，只转头
        if (v.data.getPose() != NPCPose.SITTING && Math.abs(v.headYawOffset) > 55) {
            // 头转不过去了，身体慢慢转
            double turn = Math.signum(v.headYawOffset) * 2.5;
            v.bodyYaw = wrapDegrees(v.bodyYaw + turn);
            v.headYawOffset -= turn;
        }

        broadcastHead(v);
    }

    private void broadcastHead(VirtualNpc v) {
        double headYaw = wrapDegrees(v.bodyYaw + v.headYawOffset);
        WrapperPlayServerEntityHeadLook head =
                new WrapperPlayServerEntityHeadLook(v.entityId, (float) headYaw);
        WrapperPlayServerEntityTeleport teleport = new WrapperPlayServerEntityTeleport(
                v.entityId,
                new Vector3d(v.data.getLocation().getX(), v.data.getLocation().getY(), v.data.getLocation().getZ()),
                (float) v.bodyYaw, (float) v.curPitch, true);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(v.data.getLocation().getWorld())) continue;
            send(p, head);
            send(p, teleport);
        }
    }

    // ===== 交互监听（INTERACT_ENTITY包） =====

    public static class InteractListener extends PacketListenerAbstract {

        public InteractListener() {
            super(PacketListenerPriority.NORMAL);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
            if (!(event.getPlayer() instanceof Player player)) return;

            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            int entityId = wrapper.getEntityId();

            PlayerNpcSpawner spawner = PhilosNPCPlugin.instance().npcManager().playerNpcSpawner();
            PhilosNPC npc = spawner.getByEntityId(entityId);
            if (npc == null) return;

            // 仅处理右键交互（ATTACK忽略）
            if (wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT
                    && wrapper.getAction() != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
                return;
            }

            event.setCancelled(true);
            Bukkit.getScheduler().runTask(PhilosNPCPlugin.instance(), () ->
                    com.phcraft.philosnpc.NPCListener.handleNpcInteract(player, npc.getId()));
        }
    }

    // ===== 工具 =====

    private static void send(Player player, com.github.retrooper.packetevents.wrapper.PacketWrapper<?> wrapper) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
    }

    private static double approach(double current, double target, double maxStep) {
        double diff = target - current;
        if (Math.abs(diff) <= maxStep) return target;
        return current + Math.signum(diff) * maxStep;
    }

    private static double wrapDegrees(double angle) {
        angle %= 360.0;
        if (angle > 180.0) angle -= 360.0;
        if (angle < -180.0) angle += 360.0;
        return angle;
    }
}
