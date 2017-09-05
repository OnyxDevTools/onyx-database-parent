package com.onyx

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <R, T> mutableLazy(initializer: () -> T?): MutableLazy<R,T> = MutableLazy(initializer)

@Suppress("UNCHECKED_CAST")
class MutableLazy<in R, T>(private var initializer: () -> T?) : ReadWriteProperty<R,T> {

    @Volatile
    private var lazyInit:Lazy<T> = lazy { initializer() as T }

    override fun getValue(thisRef: R, property: KProperty<*>): T = lazyInit.value

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        lazyInit = lazy { initializer() as T }
    }
}

