package com.onyx.interactors.classfinder

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
    fun forName(name:String?): Class<*> = Class.forName(name)

    /**
     * Load class from module named
     *
     * @param module Sub Class loader to get class from
     * @param name Full qualified name of class
     * @since 2.0.0
     */
    fun forName(module:String, name:String) = forName(name)

}