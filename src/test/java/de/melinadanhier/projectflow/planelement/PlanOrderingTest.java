package de.melinadanhier.projectflow.planelement;

import de.melinadanhier.projectflow.planelement.service.PlanOrdering;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanOrderingTest {

    @Test
    void initialReindexUsesHundredStepGaps() {
        List<Item> items = items(0, 0, 0);
        PlanOrdering.reindex(items, Item::setOrder);
        assertThat(items).extracting(Item::order).containsExactly(100, 200, 300);
    }

    @Test
    void movingBetweenBeginningAndEndNormallyChangesOnlyMovedItem() {
        Item first = item(100); Item second = item(200); Item third = item(300);
        List<Item> middle = new ArrayList<>(List.of(first, second));
        PlanOrdering.place(middle, third, 1, Item::order, Item::setOrder);
        assertThat(middle).extracting(Item::order).containsExactly(100, 150, 200);
        assertThat(first.order()).isEqualTo(100);
        assertThat(second.order()).isEqualTo(200);

        Item beginning = item(999);
        PlanOrdering.place(middle, beginning, 0, Item::order, Item::setOrder);
        assertThat(beginning.order()).isZero();
        Item end = item(999);
        PlanOrdering.place(middle, end, middle.size(), Item::order, Item::setOrder);
        assertThat(end.order()).isEqualTo(300);
    }

    @Test
    void repeatedInsertionsUseGapsAndEventuallyReindexOnlyThatContainer() {
        Item first = item(100); Item second = item(200); Item foreign = item(17);
        List<Item> affected = new ArrayList<>(List.of(first, second));
        for (int index = 0; index < 7; index++) {
            PlanOrdering.place(affected, item(0), 1, Item::order, Item::setOrder);
        }
        assertThat(affected).extracting(Item::order)
                .isSorted().doesNotHaveDuplicates();
        assertThat(foreign.order()).isEqualTo(17);
    }

    @Test
    void missingIntegerGapReindexesIncludingMovedItem() {
        List<Item> items = new ArrayList<>(List.of(item(100), item(101)));
        PlanOrdering.place(items, item(0), 1, Item::order, Item::setOrder);
        assertThat(items).extracting(Item::order).containsExactly(100, 200, 300);
    }

    @Test
    void datedOrderUsesNullsLastManualOrderAndStableId() {
        UUID lowId = new UUID(0, 1); UUID highId = new UUID(0, 2);
        Item later = new Item(highId, 100, LocalDate.of(2027, 2, 1));
        Item tiedHigh = new Item(highId, 200, LocalDate.of(2027, 1, 1));
        Item tiedLow = new Item(lowId, 200, LocalDate.of(2027, 1, 1));
        Item undated = new Item(lowId, 0, null);
        List<Item> items = new ArrayList<>(List.of(undated, later, tiedHigh, tiedLow));
        items.sort(PlanOrdering.dated(Item::date, Item::order, Item::id));
        assertThat(items).containsExactly(tiedLow, tiedHigh, later, undated);
    }

    @Test
    void manualOrderIgnoresDate() {
        Item first = new Item(new UUID(0, 1), 100, null);
        Item second = new Item(new UUID(0, 2), 200, LocalDate.of(2020, 1, 1));
        List<Item> items = new ArrayList<>(List.of(second, first));
        items.sort(PlanOrdering.manual(Item::order, Item::id));
        assertThat(items).containsExactly(first, second);
    }

    private List<Item> items(int... orders) {
        List<Item> result = new ArrayList<>();
        for (int order : orders) result.add(item(order));
        return result;
    }

    private Item item(int order) {
        return new Item(UUID.randomUUID(), order, null);
    }

    private static final class Item {
        private final UUID id;
        private int order;
        private final LocalDate date;
        private Item(UUID id, int order, LocalDate date) {
            this.id = id; this.order = order; this.date = date;
        }
        UUID id() { return id; }
        int order() { return order; }
        LocalDate date() { return date; }
        void setOrder(int order) { this.order = order; }
    }
}
