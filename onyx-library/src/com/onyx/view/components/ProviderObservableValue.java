package com.onyx.view.components;

/**
 * Created by timothy.osborn on 9/11/14.
 */
public interface ProviderObservableValue<T>
{
    void setEntity(T entity);
    T getEntity();
}
