package de.tecca.zyrox.shop.gui;

import de.tecca.zyrox.shop.storage.ShopRecord;
import de.tecca.zyrox.shop.storage.ShopTransactionRecord;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.time.Duration;
import java.util.List;

public final class ShopEditorHolder implements InventoryHolder {

    public enum View {
        EDITOR,
        DISCOUNT,
        HISTORY
    }

    public enum DiscountDuration {
        PERMANENT(null),
        THIRTY_MINUTES(Duration.ofMinutes(30)),
        TWO_HOURS(Duration.ofHours(2)),
        SEVEN_DAYS(Duration.ofDays(7));

        private final Duration duration;

        DiscountDuration(Duration duration) {
            this.duration = duration;
        }

        public Duration duration() {
            return duration;
        }
    }

    private ShopRecord shop;
    private View view = View.EDITOR;
    private int historyPage = 1;
    private int discountPercent;
    private DiscountDuration discountDuration = DiscountDuration.PERMANENT;
    private List<ShopTransactionRecord> history = List.of();

    public ShopEditorHolder(ShopRecord shop) {
        this.shop = shop;
        this.discountPercent = Math.max(5, shop.activeDiscountPercent(java.time.Clock.systemUTC()));
    }

    public ShopRecord shop() {
        return shop;
    }

    public View view() {
        return view;
    }

    public void view(View view) {
        this.view = view;
    }

    public int historyPage() {
        return historyPage;
    }

    public void historyPage(int historyPage) {
        this.historyPage = Math.max(1, historyPage);
    }

    public int discountPercent() {
        return discountPercent;
    }

    public void discountPercent(int discountPercent) {
        this.discountPercent = Math.max(1, Math.min(90, discountPercent));
    }

    public DiscountDuration discountDuration() {
        return discountDuration;
    }

    public void discountDuration(DiscountDuration discountDuration) {
        this.discountDuration = discountDuration;
    }

    public List<ShopTransactionRecord> history() {
        return history;
    }

    public void history(List<ShopTransactionRecord> history) {
        this.history = history == null ? List.of() : List.copyOf(history);
    }

    public void update(ShopRecord shop) {
        this.shop = shop;
        if (shop.activeDiscountPercent(java.time.Clock.systemUTC()) > 0) {
            this.discountPercent = shop.activeDiscountPercent(java.time.Clock.systemUTC());
        }
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
