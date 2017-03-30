package java.util.function;

/**
 * Created by tosborn1 on 3/7/17.
 *
 * This class is added because Android library requires to be compiled with
 * the functional interface definitions.
 *
 * @since 1.2.2
 */
@SuppressWarnings("unused")
public interface BiFunction<T, U, R> {

    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param u the second function argument
     * @return the function result
     * @since 1.2.2
     */
    @SuppressWarnings("unused")
    R apply(T t, U u);
}