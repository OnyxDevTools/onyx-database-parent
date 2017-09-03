package util.function;


/**
 * Created by tosborn1 on 3/7/17.
 *
 * This class is added because Android library requires to be compiled with
 * the functional interface definitions.
 *
 * @since 1.2.2
 */
@SuppressWarnings("unused")
public interface BiConsumer<T, U> {

    /**
     * Performs this operation on the given arguments.
     *
     * @param t the first input argument
     * @param u the second input argument
     * @since 1.2.2
     */
    @SuppressWarnings("unused")
    void accept(T t, U u);

}