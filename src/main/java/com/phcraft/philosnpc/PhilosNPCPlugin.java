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

    public static final double CREATE_COST = 500.0;
    public static final double TP_TO_NPC_COST = 10.0;
    public static final int MAX_FEATURES = 4;

    @Override
    public void onEnable() {
        instance = this;
        npcIdKey = new NamespacedKey(this, "npc_id");

        saveDefaultConfig();

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

        // 启动头部动画任务
        npcManager.startHeadAnimation();

        // 区块加载时重生NPC
        Bukkit.getPluginManager().registerEvents(new ChunkListener(npcManager), this);

        getLogger().info("PhilosNPC 已启用！");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) {
            npcManager.saveAll();
            npcManager.despawnAll();
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

    public static String cc(String msg) {
        return msg.replace('&', '\u00a7');
    }
}
