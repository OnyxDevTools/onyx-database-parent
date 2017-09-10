package com.onyx.extension

/**
 * Method to help reduce boiler plate code for ignoring exceptions
 *
 * @since 2.0.0
 */
@Suppress("UNCHECKED_CAST")
fun catchAll(body: () -> Unit) = try {
    body.invoke()
} catch (ignore:Exception){}