package util.function;

/**
 * Created by tosborn1 on 3/7/17.
 *
 * This class is added because Android library requires to be compiled with
 * the functional interface definitions.
 *
 */
@SuppressWarnings("unused")
public interface Predicate<T> {
    /**
     * Evaluates this predicate on the given argument.
     *
     * @param t the input argument
     * @return {@code true} if the input argument matches the predicate,
     * otherwise {@code false}
     * @since 1.2.2
     */
    @SuppressWarnings("unused")
    boolean test(T t);
}
