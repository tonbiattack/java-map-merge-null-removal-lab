package jp.tonbiattack.debuglab.inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * SKUごとの在庫量を追跡します。数量ゼロでもSKUは追跡対象として残す契約です。
 */
public class StockLedger {

    private final Map<String, Integer> quantities = new HashMap<>();

    public void recordInitialQuantity(String sku, int quantity) {
        quantities.put(sku, quantity);
    }

    public StockAdjustmentOutcome applyAdjustment(String sku, int adjustment) {
        Integer quantity = quantities.merge(sku, adjustment, (current, delta) -> {
            int total = current + delta;
            return total == 0 ? null : total;
        });
        return quantity == null
                ? StockAdjustmentOutcome.REMOVED_FROM_TRACKING
                : StockAdjustmentOutcome.ADJUSTED_TO_ZERO;
    }

    public int quantityOf(String sku) {
        return quantities.getOrDefault(sku, -1);
    }

    public boolean isTracked(String sku) {
        return quantities.containsKey(sku);
    }

    public int trackedSkuCount() {
        return quantities.size();
    }
}
