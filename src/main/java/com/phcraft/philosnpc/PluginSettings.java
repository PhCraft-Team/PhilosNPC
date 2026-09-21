package com.phcraft.philosnpc;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * 可配置的费用与限制项（config.yml），OP 可通过编辑配置 + reload 热调整
 */
public final class PluginSettings {

    private static double createCost = 500.0;
    private static double tpToNpcCost = 10.0;
    private static double teleportFeatureCost = 5.0;
    private static double featureAddCost = 100.0;
    private static double deleteRefund = 250.0;
    private static int maxFeatures = 5;

    private PluginSettings() {}

    public static void load(FileConfiguration config) {
        createCost = config.getDouble("create-cost", 500.0);
        tpToNpcCost = config.getDouble("tp-to-npc-cost", 10.0);
        teleportFeatureCost = config.getDouble("teleport-feature-cost", 5.0);
        featureAddCost = config.getDouble("feature-add-cost", 100.0);
        deleteRefund = config.getDouble("delete-refund", 250.0);
        maxFeatures = Math.max(1, config.getInt("max-features", 5));
    }

    public static double createCost() { return createCost; }
    public static double tpToNpcCost() { return tpToNpcCost; }
    public static double teleportFeatureCost() { return teleportFeatureCost; }
    public static double featureAddCost() { return featureAddCost; }
    public static double deleteRefund() { return deleteRefund; }
    public static int maxFeatures() { return maxFeatures; }
}
