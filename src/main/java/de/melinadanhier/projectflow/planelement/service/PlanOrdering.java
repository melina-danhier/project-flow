package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;
import de.melinadanhier.projectflow.plancontainer.model.SortMode;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/** Shared deterministic display and sparse persistent ordering rules for plan elements. */
public final class PlanOrdering {

    public static final int GAP = 100;

    private PlanOrdering() {
    }

    public static <T> Comparator<T> manual(ToIntFunction<T> order, Function<T, UUID> id) {
        return Comparator.comparingInt(order).thenComparing(id);
    }

    /** Keeps undated elements in their manual slots and sorts only dated elements among those slots. */
    public static <T> List<T> display(
            List<T> manualOrder,
            SortMode sortMode,
            Function<T, LocalDate> date
    ) {
        if (sortMode != SortMode.DATE) {
            return List.copyOf(manualOrder);
        }
        List<T> dated = manualOrder.stream()
                .filter(element -> date.apply(element) != null)
                .sorted(Comparator.comparing(date))
                .toList();
        List<T> result = new ArrayList<>(manualOrder);
        int datedIndex = 0;
        for (int index = 0; index < result.size(); index++) {
            if (date.apply(result.get(index)) != null) {
                result.set(index, dated.get(datedIndex++));
            }
        }
        return List.copyOf(result);
    }

    /** Inserts {@code moved} and changes only it unless the target gap must be rebuilt. */
    public static <T> void place(
            List<T> orderedWithoutMoved,
            T moved,
            int targetPosition,
            ToIntFunction<T> order,
            ObjIntConsumer<T> setOrder
    ) {
        if (targetPosition < 0 || targetPosition > orderedWithoutMoved.size()) {
            throw new DomainValidationException("Die Zielposition ist ungültig.");
        }
        if (orderedWithoutMoved.isEmpty()) {
            setOrder.accept(moved, GAP);
            orderedWithoutMoved.add(moved);
            return;
        }

        Integer candidate = candidate(orderedWithoutMoved, targetPosition, order);
        orderedWithoutMoved.add(targetPosition, moved);
        if (candidate == null) {
            reindex(orderedWithoutMoved, setOrder);
        } else {
            setOrder.accept(moved, candidate);
        }
    }

    public static <T> void reindex(List<T> ordered, ObjIntConsumer<T> setOrder) {
        for (int index = 0; index < ordered.size(); index++) {
            setOrder.accept(ordered.get(index), Math.multiplyExact(index + 1, GAP));
        }
    }

    private static <T> Integer candidate(List<T> ordered, int position, ToIntFunction<T> order) {
        if (position == 0) {
            int next = order.applyAsInt(ordered.getFirst());
            return next >= GAP ? next - GAP : null;
        }
        if (position == ordered.size()) {
            int previous = order.applyAsInt(ordered.getLast());
            return previous <= Integer.MAX_VALUE - GAP ? previous + GAP : null;
        }
        int previous = order.applyAsInt(ordered.get(position - 1));
        int next = order.applyAsInt(ordered.get(position));
        long distance = (long) next - previous;
        return distance > 1 ? (int) (previous + distance / 2) : null;
    }
}
