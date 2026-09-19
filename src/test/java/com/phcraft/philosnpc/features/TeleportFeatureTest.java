package com.phcraft.philosnpc.features;
import com.phcraft.philosnpc.PhilosNPCPlugin;
import net.milkbowl.vault.economy.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeleportFeatureTest {
    @Test void cancelledTeleportRefundsExactlyOnce() {
        var player = mock(Player.class); var economy = mock(Economy.class);
        var target = new Location(mock(World.class), 0, 64, 0);
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
        var player = mock(Player.class); var target = new Location(mock(World.class), 0, 64, 0);
        try (var plugin = mockStatic(PhilosNPCPlugin.class)) {
            assertFalse(TeleportFeature.teleport(player, target, 5));
            verify(player, never()).teleport(target);
        }
    }
    @Test void successDoesNotRefund() {
        var player = mock(Player.class); var economy = mock(Economy.class);
        var target = new Location(mock(World.class), 0, 64, 0);
        when(economy.withdrawPlayer(player, 5)).thenReturn(PaymentsTest.response(true));
        when(player.teleport(target)).thenReturn(true);
        try (var plugin = mockStatic(PhilosNPCPlugin.class)) {
            plugin.when(PhilosNPCPlugin::economy).thenReturn(economy);
            assertTrue(TeleportFeature.teleport(player, target, 5));
            verify(economy, never()).depositPlayer(player, 5);
        }
    }
}
