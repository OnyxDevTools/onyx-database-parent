package com.onyx.extension.common

/**
 * Method to help reduce boiler plate code for ignoring exceptions
 *
 * @since 2.0.0
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any?> catchAll(body: () -> T):T = try {
        body.invoke()
    } catch (ignore:Exception) {
        null as T
}