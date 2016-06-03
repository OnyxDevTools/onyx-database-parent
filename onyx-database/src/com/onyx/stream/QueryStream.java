package com.onyx.stream;

import com.onyx.persistence.manager.PersistenceManager;

import java.util.Objects;

/**
 * Created by tosborn1 on 5/19/16.
 */
@FunctionalInterface
public interface QueryStream<T>
{
    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument is a PersistenceManager
     */
    void accept(T t, PersistenceManager u);

    /**
     * Returns a composed {@code BiConsumer} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code BiConsumer} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default QueryStream<T> andThen(QueryStream<? super T> after) {
        Objects.requireNonNull(after);

        return (l, r) -> {
            accept(l, r);
            after.accept(l, r);
        };
    }
}
