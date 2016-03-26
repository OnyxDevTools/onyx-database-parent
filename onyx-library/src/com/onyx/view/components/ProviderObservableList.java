package com.onyx.view.components;

import com.sun.javafx.collections.ObservableListWrapper;
import javafx.collections.ObservableList;
import javafx.util.Callback;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by timothy.osborn on 9/11/14.
 */
public class ProviderObservableList<T,E> extends ObservableListWrapper<E> implements ObservableList<E>
{

    protected Map<E, T> entityMap = new HashMap<>();
    protected Map<T, E> indexMap = new HashMap<>();

    private Class eClass;
    private Class tClass;

    public ProviderObservableList(List list, Class tClass, Class eClass)
    {

        super(new ArrayList<E>());

        this.eClass = eClass;
        this.tClass = tClass;

        List newList = new ArrayList<>();

        T entity = null;
        Constructor<E> constructor = null;
        E provider = null;

        try {

            for(Object object : list)
            {
                constructor = eClass.getConstructor(tClass);
                entity = (T) object;
                provider = (E) constructor.newInstance(entity);
                newList.add(provider);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        setAll(newList);
    }

    public ProviderObservableList(List list, Callback extractor)
    {
        super(list, extractor);
    }

    @Override
    protected E doSet(int index, E element)
    {
        T value = (T)((ProviderObservableValue)element).getEntity();
        Integer integerIndex = Integer.valueOf(index);
        entityMap.put(element, value);
        indexMap.put(value, element);
        return super.doSet(index, element);
    }

    @Override
    protected void doAdd(int index, E element)
    {
        Integer integerIndex = Integer.valueOf(index);
        T value = (T)((ProviderObservableValue)element).getEntity();
        entityMap.put(element, value);
        indexMap.put(value, element);
        super.doAdd(index, element);
    }

    @Override
    protected E doRemove(int index)
    {
        T value = entityMap.remove(Integer.valueOf(index));
        indexMap.remove(value);
        return super.doRemove(index);
    }

    public E getProvider(T entity) {
        return indexMap.get(entity);
    }

    public T getEntity(E element)
    {
        Integer index = Integer.valueOf(super.indexOf(element));
        return entityMap.get(index);
    }

    public T removeEntity(T entity)
    {
        E element = indexMap.get(entity);
        this.remove(element);
        return entity;
    }

    public Boolean containsEntity(T entity)
    {
        return (indexMap.containsKey(entity));
    }

    public T setEntity(T entity)
    {
        E obj = null;

        if(!indexMap.containsKey(entity))
        {

            try {
                Constructor constructor = eClass.getConstructor();
                try {
                    obj = (E)constructor.newInstance();
                    ((ProviderObservableValue)obj).setEntity(entity);
                    this.add(obj);
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        else
        {
            E element = indexMap.get(entity);
            ((ProviderObservableValue)element).setEntity(entity);
            if(indexOf(element) > -1)
            {
                this.doSet(indexOf(element), element);
            }
            else
            {
                this.doAdd(0, element);
            }
        }

        return entity;
    }
}
