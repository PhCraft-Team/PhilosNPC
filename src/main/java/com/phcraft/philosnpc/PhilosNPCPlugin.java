package com.phcraft.philosnpc;

import com.phcraft.philosnpc.npc.NPCManager;
import com.phcraft.philosnpc.gui.GuiManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class PhilosNPCPlugin extends JavaPlugin {

    private static PhilosNPCPlugin instance;
    private static Economy economy;
    private static NamespacedKey npcIdKey;

    private NPCManager npcManager;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        instance = this;
        npcIdKey = new NamespacedKey(this, "npc_id");

        // PacketEvents 初始化（发包渲染玩家形态NPC）
        com.github.retrooper.packetevents.PacketEvents.setAPI(
                io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder.build(this));
        com.github.retrooper.packetevents.PacketEvents.getAPI().getSettings()
                .checkForUpdates(false)
                .debug(false);
        com.github.retrooper.packetevents.PacketEvents.getAPI().load();

        saveDefaultConfig();
        PluginSettings.load(getConfig());

        // 初始化 Vault
        if (!setupEconomy()) {
            getLogger().warning("Vault 未找到，经济功能将不可用");
        }

        npcManager = new NPCManager();
        npcManager.loadAll();

        guiManager = new GuiManager();

        // 注册命令
        var cmd = getCommand("philosnpc");
        if (cmd != null) {
            cmd.setExecutor(new PhilosCommand());
        }

        // 注册事件
        Bukkit.getPluginManager().registerEvents(new NPCListener(), this);
        Bukkit.getPluginManager().registerEvents(guiManager, this);

        // 玩家上线补发虚拟NPC
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                npcManager.playerNpcSpawner().spawnAllTo(event.getPlayer());
            }
        }, this);

        // 启动头部动画任务（玩家形态虚拟NPC + 生物型）
        npcManager.playerNpcSpawner().startHeadAnimation();
        npcManager.startHeadAnimation();

        // 区块加载时重生NPC（生物型）
        Bukkit.getPluginManager().registerEvents(new ChunkListener(npcManager), this);

        // PacketEvents 监听虚拟NPC交互
        com.github.retrooper.packetevents.PacketEvents.getAPI().init();
        com.github.retrooper.packetevents.PacketEvents.getAPI().getEventManager()
                .registerListener(new com.phcraft.philosnpc.npc.PlayerNpcSpawner.InteractListener());

        getLogger().info("PhilosNPC 已启用！");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            npcManager.saveAll();
            npcManager.despawnAll();
        }
        if (com.github.retrooper.packetevents.PacketEvents.getAPI() != null) {
            com.github.retrooper.packetevents.PacketEvents.getAPI().terminate();
        }
        getLogger().info("PhilosNPC 已禁用");
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public static PhilosNPCPlugin instance() { return instance; }
    public static Economy economy() { return economy; }
    public static NamespacedKey npcIdKey() { return npcIdKey; }
    public NPCManager npcManager() { return npcManager; }
    public GuiManager guiManager() { return guiManager; }

    private static final net.kyori.adventure.text.minimessage.MiniMessage MINI_MESSAGE =
            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

    public static net.kyori.adventure.text.minimessage.MiniMessage miniMessage() { return MINI_MESSAGE; }

    public static String cc(String msg) {
        return msg.replace('&', '\u00a7');
    }
}
