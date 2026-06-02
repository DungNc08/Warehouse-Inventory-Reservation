package com.warehouse.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryTest {

    @Test
    void reserve_movesStockFromAvailableToReserved() {
        Inventory inventory = inventory("A100", 100, 100, 0);

        inventory.reserve(30);

        assertThat(inventory.getAvailableStock()).isEqualTo(70);
        assertThat(inventory.getReservedStock()).isEqualTo(30);
        assertThat(inventory.getTotalStock()).isEqualTo(100);
    }

    @Test
    void reserve_throwsWhenQuantityExceedsAvailable() {
        Inventory inventory = inventory("A100", 100, 10, 90);

        assertThatThrownBy(() -> inventory.reserve(11))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient stock for SKU A100");

        assertThat(inventory.getAvailableStock()).isEqualTo(10);
        assertThat(inventory.getReservedStock()).isEqualTo(90);
    }

    @Test
    void release_movesStockFromReservedToAvailable() {
        Inventory inventory = inventory("B200", 50, 20, 30);

        inventory.release(10);

        assertThat(inventory.getAvailableStock()).isEqualTo(30);
        assertThat(inventory.getReservedStock()).isEqualTo(20);
    }

    @Test
    void release_throwsWhenQuantityExceedsReserved() {
        Inventory inventory = inventory("B200", 50, 40, 10);

        assertThatThrownBy(() -> inventory.release(11))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot release more than reserved stock for SKU B200");

        assertThat(inventory.getAvailableStock()).isEqualTo(40);
        assertThat(inventory.getReservedStock()).isEqualTo(10);
    }

    @Test
    void hasAvailable_returnsFalseWhenStockIsInsufficient() {
        Inventory inventory = inventory("A100", 100, 5, 95);

        assertThat(inventory.hasAvailable(5)).isTrue();
        assertThat(inventory.hasAvailable(6)).isFalse();
    }

    private static Inventory inventory(String sku, int total, int available, int reserved) {
        Inventory inventory = newInventoryInstance();
        ReflectionTestUtils.setField(inventory, "sku", sku);
        ReflectionTestUtils.setField(inventory, "totalStock", total);
        ReflectionTestUtils.setField(inventory, "availableStock", available);
        ReflectionTestUtils.setField(inventory, "reservedStock", reserved);
        return inventory;
    }

    private static Inventory newInventoryInstance() {
        return new Inventory();
    }
}
