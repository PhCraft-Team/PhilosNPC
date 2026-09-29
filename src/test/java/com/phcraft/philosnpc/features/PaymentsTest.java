package com.phcraft.philosnpc.features;
import org.junit.jupiter.api.Test;
import net.milkbowl.vault.economy.*;
import org.bukkit.OfflinePlayer;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentsTest {
    final Economy economy = mock(Economy.class);
    final OfflinePlayer buyer = player();
    final OfflinePlayer seller = player();
    static OfflinePlayer player() {
        var player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
    static EconomyResponse response(boolean success) {
        return new EconomyResponse(10, 100, success ? EconomyResponse.ResponseType.SUCCESS
                : EconomyResponse.ResponseType.FAILURE, "test");
    }
    @Test void selfPurchaseDoesNotMoveMoney() {
        assertEquals(Payments.Result.SUCCESS, Payments.transfer(economy, buyer, buyer, 10));
        verifyNoInteractions(economy);
    }
    @Test void rejectsInvalidPriceBeforeCallingProvider() {
        for (double price : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1})
            assertEquals(Payments.Result.FAILED, Payments.transfer(economy, buyer, seller, price));
        verifyNoInteractions(economy);
    }
    @Test void successfulSaleCreditsSellerExactlyOnce() {
        when(economy.withdrawPlayer(buyer, 10)).thenReturn(response(true));
        when(economy.depositPlayer(seller, 10)).thenReturn(response(true));
        assertEquals(Payments.Result.SUCCESS, Payments.transfer(economy, buyer, seller, 10));
        verify(economy).withdrawPlayer(buyer, 10);
        verify(economy).depositPlayer(seller, 10);
        verifyNoMoreInteractions(economy);
    }
    @Test void failedDebitDoesNotPaySeller() {
        when(economy.withdrawPlayer(buyer, 10)).thenReturn(response(false));
        assertEquals(Payments.Result.FAILED, Payments.transfer(economy, buyer, seller, 10));
        verify(economy, never()).depositPlayer(any(OfflinePlayer.class), anyDouble());
    }
    @Test void explicitCreditFailureRefundsBuyer() {
        when(economy.withdrawPlayer(buyer, 10)).thenReturn(response(true));
        when(economy.depositPlayer(seller, 10)).thenReturn(response(false));
        when(economy.depositPlayer(buyer, 10)).thenReturn(response(true));
        assertEquals(Payments.Result.FAILED, Payments.transfer(economy, buyer, seller, 10));
        verify(economy).depositPlayer(buyer, 10);
    }
    @Test void ambiguousCreditNeverRetriesOrMintsRefund() {
        when(economy.withdrawPlayer(buyer, 10)).thenReturn(response(true));
        when(economy.depositPlayer(seller, 10)).thenThrow(new IllegalStateException());
        assertEquals(Payments.Result.UNCERTAIN, Payments.transfer(economy, buyer, seller, 10));
        verify(economy, never()).depositPlayer(buyer, 10);
    }
    @Test void failedRefundRequiresReconciliation() {
        when(economy.withdrawPlayer(buyer, 10)).thenReturn(response(true));
        when(economy.depositPlayer(seller, 10)).thenReturn(response(false));
        when(economy.depositPlayer(buyer, 10)).thenReturn(response(false));
        assertEquals(Payments.Result.UNCERTAIN, Payments.transfer(economy, buyer, seller, 10));
    }
    @Test void missingProviderBlocksPaidButAllowsFreeTeleport() {
        assertEquals(Payments.Result.FAILED, Payments.transfer(null, buyer, null, 10));
        assertEquals(Payments.Result.SUCCESS, Payments.transfer(null, buyer, null, 0));
    }
}
