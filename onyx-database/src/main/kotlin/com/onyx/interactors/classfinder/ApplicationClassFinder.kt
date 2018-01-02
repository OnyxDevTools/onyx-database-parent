package com.onyx.interactors.classfinder

import com.onyx.interactors.classfinder.impl.DefaultEntityClassFinder
import com.onyx.persistence.context.SchemaContext

/**
 * Default implementation of the class finder.  This is a wrapper for Java9 compatibility.  Since, in some
 * cases we may want to load classes via runtime.  It may want to grab it from a sub module class loader
 *
 * @since 2.0.0
 */
object ApplicationClassFinder : EntityClassFinder {

    var instance:EntityClassFinder = DefaultEntityClassFinder

    /**
     * Get Class for name
     *
     * @param name Full qualified name of class
     * @since 2.0.0
     */
    override fun forName(name:String?, schemaContext: SchemaContext?) = instance.forName(name, schemaContext)

}