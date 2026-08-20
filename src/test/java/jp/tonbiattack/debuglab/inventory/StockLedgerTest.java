package jp.tonbiattack.debuglab.inventory;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StockLedgerTest {

    @Test
    void adjustmentToZero_keepsSkuTrackedAtZero() {
        StockLedger ledger = new StockLedger();
        ledger.recordInitialQuantity("tea", 5);

        StockAdjustmentOutcome outcome = ledger.applyAdjustment("tea", -5);

        assertAll(
                () -> assertEquals(StockAdjustmentOutcome.ADJUSTED_TO_ZERO, outcome,
                        "在庫ゼロへの調整は追跡継続として結果を返す"),
                () -> assertEquals(0, ledger.quantityOf("tea"),
                        "SKUの在庫量をゼロとして保存する"),
                () -> assertTrue(ledger.isTracked("tea"),
                        "数量ゼロでもSKUの追跡を維持する"),
                () -> assertEquals(1, ledger.trackedSkuCount(),
                        "追跡SKU件数を一件のまま保つ")
        );
    }

}
