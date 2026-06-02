package com.warehouse.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    private String sku;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock;

    @Version
    private long version;

    protected Inventory() {
    }

    public String getSku() {
        return sku;
    }

    public int getTotalStock() {
        return totalStock;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public long getVersion() {
        return version;
    }

    public boolean hasAvailable(int quantity) {
        return availableStock >= quantity;
    }

    public void reserve(int quantity) {
        if (!hasAvailable(quantity)) {
            throw new IllegalStateException("Insufficient stock for SKU " + sku);
        }
        availableStock -= quantity;
        reservedStock += quantity;
    }

    public void release(int quantity) {
        if (reservedStock < quantity) {
            throw new IllegalStateException("Cannot release more than reserved stock for SKU " + sku);
        }
        reservedStock -= quantity;
        availableStock += quantity;
    }
}
