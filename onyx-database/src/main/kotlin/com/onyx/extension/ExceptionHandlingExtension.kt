package com.onyx.extension

@Suppress("UNCHECKED_CAST")
fun catchAll(body: () -> Unit) {
    try {
        body.invoke()
    } catch (ignore:Exception){}
}