package jp.tonbiattack.debuglab.inventory;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MapMergeNullObservationTest {

    @Test
    void nullFromRemappingFunctionRemovesTheSkuMapping() {
        Map<String, Integer> quantities = new HashMap<>();
        quantities.put("tea", 5);

        Integer merged = quantities.merge("tea", -5, (current, delta) -> {
            int total = current + delta;
            return total == 0 ? null : total;
        });

        assertAll(
                () -> assertEquals(null, merged,
                        "remapping関数がnullを返すとmergeもnullを返す"),
                () -> assertFalse(quantities.containsKey("tea"),
                        "nullは値として保存されず、SKUのマッピングが削除される"),
                () -> assertEquals(0, quantities.size(),
                        "MapからSKUが完全に消える")
        );
    }
}
