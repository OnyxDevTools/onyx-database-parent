package com.onyx.vector

import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class QuantizedCosinePortabilityTest {
    @Test
    fun `missing vector API and module system never load the SIMD implementation`() {
        verifyUnavailableApi { name -> throw ClassNotFoundException(name) }
    }

    @Test
    fun `unavailable vector API dependencies fall back to scalar arithmetic`() {
        verifyUnavailableApi { name -> throw NoClassDefFoundError(name) }
    }

    private fun verifyUnavailableApi(fail: (String) -> Nothing) {
        val vectorClass = QuantizedCosineVector::class.java
        val root = vectorClass.protectionDomain.codeSource.location
        var probes = 0
        object : URLClassLoader(arrayOf(root), vectorClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                check(name != "java.lang.ModuleLayer") { "Android has no Java module system" }
                if (name.startsWith("jdk.incubator.vector.")) {
                    probes++
                    fail(name)
                }
                check(name != "com.onyx.vector.VectorizedByteDotProduct") {
                    "The SIMD implementation must remain unloaded when its API is absent"
                }
                if (name.startsWith("com.onyx.vector.QuantizedCosineVector")) {
                    return (findLoadedClass(name) ?: findClass(name)).also { if (resolve) resolveClass(it) }
                }
                return super.loadClass(name, resolve)
            }
        }.use { loader ->
            val isolated = loader.loadClass(vectorClass.name)
            val companion = isolated.getField("Companion").get(null)
            val fromBytes = companion.javaClass.getMethod("fromBytes", ByteArray::class.java)
            val score = isolated.getMethod("cosineSimilarity", isolated)
            val bytes = ByteArray(688) { if (it % 2 == 0) -128 else 127 }
            val left = fromBytes.invoke(companion, bytes)
            val right = fromBytes.invoke(companion, bytes.copyOf())
            assertEquals(1f, score.invoke(left, right))
            assertContentEquals(bytes, isolated.getMethod("toByteArray").invoke(left) as ByteArray)
            assertEquals(1, probes, "Availability must be checked only once per class loader")
        }
    }
}
