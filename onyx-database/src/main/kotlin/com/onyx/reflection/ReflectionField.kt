package com.onyx.reflection

import java.lang.reflect.Field

/**
 * Created by tosborn1 on 8/2/16.
 *
 * This field is a wrapper for the unsafe field and the calculated offset
 * It is used for using reflection using the unsafe api.
 *
 */
class ReflectionField(val name: String, val field: Field) {

    val type: Class<*> = field.type

    init {
        this.field.isAccessible = true
    }

}