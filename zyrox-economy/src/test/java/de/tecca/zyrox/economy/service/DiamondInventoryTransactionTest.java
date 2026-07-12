package de.tecca.zyrox.economy.service;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiamondInventoryTransactionTest {

    @Test
    void shopCreationFeeOfOneLeavesNineteenOfTwentyDiamonds() {
        TestInventory inventory = inventory(stack(Material.DIAMOND, 20), stack(Material.STONE, 16));

        DiamondInventoryTransaction.Result result = DiamondInventoryTransaction.withdraw(
                inventory.proxy(), Material.DIAMOND, 1, () -> {
                }
        );

        assertTrue(result.success());
        assertEquals(20, result.beforeItems());
        assertEquals(19, result.finalItems());
        assertEquals(19, inventory.amount(0));
        assertEquals(16, inventory.amount(1));
    }

    @Test
    void multiItemShopPurchaseRemovesOnlyRequestedQuantity() {
        TestInventory inventory = inventory(stack(Material.DIAMOND, 20));

        DiamondInventoryTransaction.Result result = DiamondInventoryTransaction.withdraw(
                inventory.proxy(), Material.DIAMOND, 3, () -> {
                }
        );

        assertTrue(result.success());
        assertEquals(17, inventory.amount(0));
    }

    @Test
    void withdrawalSpansMultipleStacksExactly() {
        TestInventory inventory = inventory(
                stack(Material.DIAMOND, 64),
                stack(Material.STONE, 12),
                stack(Material.DIAMOND, 20)
        );

        DiamondInventoryTransaction.Result result = DiamondInventoryTransaction.withdraw(
                inventory.proxy(), Material.DIAMOND, 65, () -> {
                }
        );

        assertTrue(result.success());
        assertNull(inventory.item(0));
        assertEquals(12, inventory.amount(1));
        assertEquals(19, inventory.amount(2));
    }

    @Test
    void insufficientAndInvalidWithdrawalsDoNotMutateInventory() {
        TestInventory insufficient = inventory(stack(Material.DIAMOND, 2));
        AtomicBoolean committed = new AtomicBoolean();

        DiamondInventoryTransaction.Result insufficientResult = DiamondInventoryTransaction.withdraw(
                insufficient.proxy(), Material.DIAMOND, 3, () -> committed.set(true)
        );
        DiamondInventoryTransaction.Result invalidResult = DiamondInventoryTransaction.withdraw(
                insufficient.proxy(), Material.DIAMOND, 0, () -> committed.set(true)
        );

        assertEquals(DiamondInventoryTransaction.Status.INSUFFICIENT_ITEMS, insufficientResult.status());
        assertEquals(DiamondInventoryTransaction.Status.INVALID_AMOUNT, invalidResult.status());
        assertEquals(2, insufficient.amount(0));
        assertFalse(committed.get());
    }

    @Test
    void ledgerFailureRestoresCompleteInventorySnapshot() {
        TestInventory inventory = inventory(
                stack(Material.DIAMOND, 10),
                stack(Material.STONE, 7),
                stack(Material.DIAMOND, 10)
        );

        DiamondInventoryTransaction.Result result = DiamondInventoryTransaction.withdraw(
                inventory.proxy(),
                Material.DIAMOND,
                12,
                () -> {
                    throw new SQLException("simulated ledger failure");
                }
        );

        assertEquals(DiamondInventoryTransaction.Status.COMMIT_FAILED, result.status());
        assertTrue(result.rollbackVerified());
        assertEquals(10, inventory.amount(0));
        assertEquals(7, inventory.amount(1));
        assertEquals(10, inventory.amount(2));
    }

    @Test
    void unexpectedAllDiamondRemovalIsDetectedAndRolledBack() {
        TestInventory inventory = inventory(stack(Material.DIAMOND, 20));
        inventory.removeAllDiamondsOnNextWrite();

        DiamondInventoryTransaction.Result result = DiamondInventoryTransaction.withdraw(
                inventory.proxy(), Material.DIAMOND, 1, () -> {
                }
        );

        assertEquals(DiamondInventoryTransaction.Status.DELTA_MISMATCH, result.status());
        assertEquals(0, result.observedAfterItems());
        assertTrue(result.rollbackVerified());
        assertEquals(20, inventory.amount(0));
    }

    private static ItemStack stack(Material material, int amount) {
        AtomicInteger currentAmount = new AtomicInteger(amount);
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenAnswer(ignored -> currentAmount.get());
        doAnswer(invocation -> {
            currentAmount.set(invocation.getArgument(0));
            return null;
        }).when(item).setAmount(org.mockito.ArgumentMatchers.anyInt());
        when(item.clone()).thenAnswer(ignored -> stack(material, currentAmount.get()));
        return item;
    }

    private static TestInventory inventory(ItemStack... items) {
        return new TestInventory(items);
    }

    private static final class TestInventory {

        private final ItemStack[] contents = new ItemStack[36];
        private final Inventory proxy;
        private boolean removeAllDiamondsOnNextWrite;

        private TestInventory(ItemStack[] items) {
            for (int slot = 0; slot < items.length; slot++) {
                contents[slot] = clone(items[slot]);
            }
            proxy = (Inventory) Proxy.newProxyInstance(
                    Inventory.class.getClassLoader(),
                    new Class<?>[]{Inventory.class},
                    (ignored, method, args) -> switch (method.getName()) {
                        case "getStorageContents", "getContents" -> clone(contents);
                        case "getItem" -> clone(contents[(int) args[0]]);
                        case "setItem" -> {
                            if (removeAllDiamondsOnNextWrite) {
                                removeAllDiamondsOnNextWrite = false;
                                for (int slot = 0; slot < contents.length; slot++) {
                                    if (contents[slot] != null && contents[slot].getType() == Material.DIAMOND) {
                                        contents[slot] = null;
                                    }
                                }
                            } else {
                                contents[(int) args[0]] = clone((ItemStack) args[1]);
                            }
                            yield null;
                        }
                        case "getSize" -> contents.length;
                        case "isEmpty" -> DiamondInventoryTransaction.count(contents, Material.DIAMOND) == 0;
                        case "toString" -> "TestInventory";
                        case "hashCode" -> System.identityHashCode(this);
                        case "equals" -> ignored == args[0];
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Inventory proxy() {
            return proxy;
        }

        private ItemStack item(int slot) {
            return clone(contents[slot]);
        }

        private int amount(int slot) {
            return contents[slot] == null ? 0 : contents[slot].getAmount();
        }

        private void removeAllDiamondsOnNextWrite() {
            removeAllDiamondsOnNextWrite = true;
        }

        private static ItemStack[] clone(ItemStack[] source) {
            ItemStack[] copy = new ItemStack[source.length];
            for (int slot = 0; slot < source.length; slot++) {
                copy[slot] = clone(source[slot]);
            }
            return copy;
        }

        private static ItemStack clone(ItemStack item) {
            return item == null ? null : item.clone();
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        }
    }
}
