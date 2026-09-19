package com.phcraft.philosnpc.features;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

/** Vault 没有跨账户事务；只对明确失败的付款退款，未知结果交由管理员核对。 */
public final class Payments {
    private Payments() {}
    public enum Result { SUCCESS, FAILED, UNCERTAIN }

    public static Result transfer(Economy economy, OfflinePlayer buyer, OfflinePlayer seller, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return Result.FAILED;
        if (amount == 0 || seller != null && buyer.getUniqueId().equals(seller.getUniqueId())) {
            return Result.SUCCESS;
        }
        if (economy == null) return Result.FAILED;
        try {
            EconomyResponse debit = economy.withdrawPlayer(buyer, amount);
            if (debit == null) return Result.UNCERTAIN;
            if (!debit.transactionSuccess()) return Result.FAILED;
            if (seller == null) return Result.SUCCESS;
            EconomyResponse credit = economy.depositPlayer(seller, amount);
            if (credit == null) return Result.UNCERTAIN;
            if (credit.transactionSuccess()) return Result.SUCCESS;
            EconomyResponse refund = economy.depositPlayer(buyer, amount);
            return refund != null && refund.transactionSuccess() ? Result.FAILED : Result.UNCERTAIN;
        } catch (RuntimeException ex) {
            return Result.UNCERTAIN;
        }
    }
}
