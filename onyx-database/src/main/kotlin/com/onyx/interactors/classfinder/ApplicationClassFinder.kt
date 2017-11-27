package com.onyx.interactors.classfinder

import com.onyx.interactors.classfinder.impl.DefaultEntityClassFinder

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
    override fun forName(name:String?) = instance.forName(name)

    /**
     * Load class from module named
     *
     * @param module Sub Class loader to get class from
     * @param name Full qualified name of class
     * @since 2.0.0
     */
    override fun forName(module:String, name:String) = instance.forName(module, name)

}