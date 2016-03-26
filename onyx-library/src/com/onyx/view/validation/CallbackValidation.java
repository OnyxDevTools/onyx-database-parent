package com.onyx.view.validation;

import java.util.function.Consumer;

/**
 * Created by timothy.osborn on 9/14/14.
 */
public interface CallbackValidation extends Validation
{

    void setResponseCallback(Consumer<Boolean> callback);
    void isValid(Consumer<Boolean> callback);

}
