package de.melinadanhier.projectflow.planelement.service;

import de.melinadanhier.projectflow.common.exception.DomainValidationException;

import java.time.LocalDate;
import java.util.Comparator;
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

    public static <T> Comparator<T> dated(
            Function<T, LocalDate> date,
            ToIntFunction<T> order,
            Function<T, UUID> id
    ) {
        return Comparator.comparing(date, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(order)
                .thenComparing(id);
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
