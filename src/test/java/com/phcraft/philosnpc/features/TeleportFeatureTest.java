package com.phcraft.philosnpc.features;
import com.phcraft.philosnpc.PhilosNPCPlugin;
import net.milkbowl.vault.economy.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeleportFeatureTest {
    private static Location testLocation() {
        var location = mock(Location.class);
        var world = mock(World.class);
        when(location.getWorld()).thenReturn(world);
        return location;
    }

    private static Player testPlayer() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        var pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
        var blocked = new java.util.concurrent.atomic.AtomicBoolean();
        when(player.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(NamespacedKey.class))).thenAnswer(i -> blocked.get());
        doAnswer(i -> { blocked.set(true); return null; }).when(pdc).set(any(NamespacedKey.class),
                eq(org.bukkit.persistence.PersistentDataType.DOUBLE), anyDouble());
        doAnswer(i -> { blocked.set(false); return null; }).when(pdc).remove(any(NamespacedKey.class));
        return player;
    }

    @Test void uncertainDebitBlocksRetriesUntilAdminReconciles() {
        for (boolean throwsAfterDebit : new boolean[]{false, true}) {
            var player = testPlayer(); var economy = mock(Economy.class);
            var target = testLocation();
            var instance = mock(PhilosNPCPlugin.class);
            when(instance.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
            if (throwsAfterDebit) when(economy.withdrawPlayer(player, 5)).thenThrow(new IllegalStateException());
            try (var plugin = mockStatic(PhilosNPCPlugin.class)) {
                plugin.when(PhilosNPCPlugin::economy).thenReturn(economy);
                plugin.when(PhilosNPCPlugin::instance).thenReturn(instance);
                assertFalse(TeleportFeature.teleport(player, target, 5));
                assertFalse(TeleportFeature.teleport(player, target, 5));
                verify(economy, times(1)).withdrawPlayer(player, 5);
                verify(player, never()).teleport(target);
                verify(player).saveData();
                // 模拟重新连接后恢复的持久容器，不能因 Player 实例变化绕过锁定。
                var reconnected = testPlayer();
                var savedData = player.getPersistentDataContainer();
                when(reconnected.getPersistentDataContainer()).thenReturn(savedData);
                assertFalse(TeleportFeature.teleport(reconnected, target, 5));
                verify(economy, never()).withdrawPlayer(reconnected, 5);
                assertTrue(TeleportFeature.reconcilePayment(player));
                doReturn(PaymentsTest.response(true)).when(economy).withdrawPlayer(player, 5);
                when(player.teleport(target)).thenReturn(true);
                assertTrue(TeleportFeature.teleport(player, target, 5));
                verify(economy, times(2)).withdrawPlayer(player, 5);
            }
        }
    }

    @Test void failedRefundAlsoBlocksFurtherChargesButNotFreeTeleport() {
        var player = testPlayer(); var economy = mock(Economy.class);
        var target = testLocation();
        var instance = mock(PhilosNPCPlugin.class);
        when(instance.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(economy.withdrawPlayer(player, 5)).thenReturn(PaymentsTest.response(true));
        when(economy.depositPlayer(player, 5)).thenReturn(PaymentsTest.response(false));
        try (var plugin = mockStatic(PhilosNPCPlugin.class)) {
            plugin.when(PhilosNPCPlugin::economy).thenReturn(economy);
            plugin.when(PhilosNPCPlugin::instance).thenReturn(instance);
            assertFalse(TeleportFeature.teleport(player, target, 5));
            assertFalse(TeleportFeature.teleport(player, target, 5));
            verify(economy, times(1)).withdrawPlayer(player, 5);
            when(player.teleport(target)).thenReturn(true);
            assertTrue(TeleportFeature.teleport(player, target, 0));
            verify(economy, never()).withdrawPlayer(player, 0);
        }
    }

    @Test void cancelledTeleportRefundsExactlyOnce() {
        var player = testPlayer(); var economy = mock(Economy.class);
        var target = testLocation();
        when(economy.withdrawPlayer(player, 5)).thenReturn(PaymentsTest.response(true));
        when(economy.depositPlayer(player, 5)).thenReturn(PaymentsTest.response(true));
        when(player.teleport(target)).thenReturn(false);
        try (var plugin = mockStatic(PhilosNPCPlugin.class)) {
            plugin.when(PhilosNPCPlugin::economy).thenReturn(economy);
            assertFalse(TeleportFeature.teleport(player, target, 5));
            verify(economy).withdrawPlayer(player, 5); verify(economy).depositPlayer(player, 5);
        }
    }
    @Test void missingProviderDoesNotTeleportForFree() {
        var player = testPlayer(); var target = testLocation();
        try (var plugin = mockStatic(PhilosNPCPlugin.class)) {
            assertFalse(TeleportFeature.teleport(player, target, 5));
            verify(player, never()).teleport(target);
        }
    }
    @Test void successDoesNotRefund() {
        var player = testPlayer(); var economy = mock(Economy.class);
        var target = testLocation();
        when(economy.withdrawPlayer(player, 5)).thenReturn(PaymentsTest.response(true));
        when(player.teleport(target)).thenReturn(true);
        try (var plugin = mockStatic(PhilosNPCPlugin.class)) {
            plugin.when(PhilosNPCPlugin::economy).thenReturn(economy);
            assertTrue(TeleportFeature.teleport(player, target, 5));
            verify(economy, never()).depositPlayer(player, 5);
        }
    }
}
