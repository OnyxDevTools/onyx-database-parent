package com.onyx.interactors.classfinder

import com.onyx.persistence.context.SchemaContext

/**
 * The purpose of this class is to make onyx database backwards compatable with Java8
 * while also supporting class loading in Java 9.  This is to support runtime class
 * loading via a sub module
 * @since 2.0.0
 */
interface EntityClassFinder {

    /**
     * Get Class for name
     *
     * @param name Full qualified name of class
     * @since 2.0.0
     */
    fun forName(name:String?, schemaContext: SchemaContext? = null): Class<*> = schemaContext?.classLoader?.loadClass(name) ?: Class.forName(name)

}