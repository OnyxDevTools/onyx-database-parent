package com.onyx.extension.common

import com.onyx.persistence.annotations.*
import com.onyx.util.ReflectionField
import com.onyx.util.map.OptimisticLockingMap
import java.lang.reflect.Modifier
import java.util.ArrayList

private val classFields = OptimisticLockingMap<Class<*>, List<ReflectionField>>(HashMap<Class<*>, List<ReflectionField>>(HashMap()))

fun Any.getFields() : List<ReflectionField> {
    val clazz = this.javaClass
    val isManagedEntity = this.javaClass.isAnnotationPresent(Entity::class.java)

    return classFields.getOrPut(clazz) {
        val fields = ArrayList<ReflectionField>()
        var aClass:Class<*> = this.javaClass
        while (aClass != Any::class.java
                && aClass != Exception::class.java
                && aClass != Throwable::class.java) {
            aClass.declaredFields
                    .asSequence()
                    .filter { it.modifiers and Modifier.STATIC == 0 && !Modifier.isTransient(it.modifiers) && it.type != Exception::class.java && it.type != Throwable::class.java }
                    .forEach {
                        if (!isManagedEntity) {
                            fields.add(ReflectionField(it.name, it))
                        } else if (it.isAnnotationPresent(Attribute::class.java)
                                || it.isAnnotationPresent(Index::class.java)
                                || it.isAnnotationPresent(Partition::class.java)
                                || it.isAnnotationPresent(Identifier::class.java)
                                || it.isAnnotationPresent(Relationship::class.java)) {
                            fields.add(ReflectionField(it.name, it))
                        }
                    }
            aClass = aClass.superclass
        }

        fields.sortBy { it.name }
        fields
    }
}