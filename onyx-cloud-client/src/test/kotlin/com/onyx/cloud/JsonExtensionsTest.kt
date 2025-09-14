package com.onyx.cloud

import kotlin.test.*
import com.onyx.cloud.extensions.*

/**
 * Verifies JSON serialization and deserialization helpers.
 */
class JsonExtensionsTest {
    data class Person(val name: String, val age: Int)

    @Test
    fun serializeAndDeserializeRoundTrip() {
        val person = Person("Alice", 30)
        val json = person.toJson()
        assertTrue(json.contains("Alice", ignoreCase = false))
        val parsed = json.fromJson<Person>()
        assertEquals(person, parsed)
    }

    @Test
    fun fromJsonWithKClass() {
        val json = """{"name":"Bob","age":25}"""
        val parsed: Person = json.fromJson(Person::class)
        assertEquals("Bob", parsed.name)
        assertEquals(25, parsed.age)
    }

    @Test
    fun fromJsonListParsesArray() {
        val json = """[{"name":"A","age":1},{"name":"B","age":2}]"""
        val list: List<Person>? = json.fromJsonList(Person::class.java)
        assertNotNull(list)
        assertEquals(2, list!!.size)
        assertEquals("A", list[0].name)
        assertEquals(2, list[1].age)
    }

    @Test
    fun invalidJsonReturnsNull() {
        val result = "{bad json".fromJson<Person>()
        assertNull(result)
    }

    @Test
    fun cyclicReferenceProducesPlaceholder() {
        data class Node(var name: String, var child: Node? = null)
        val parent = Node("parent")
        parent.child = parent
        val json = parent.toJson()
        assertEquals("{\"name\":\"parent\"}", json)
    }

    @Test
    fun listRoundTrip() {
        val list = listOf(Person("A", 1), Person("B", 2))
        val json = list.toJson()
        val parsed: List<Person>? = json.fromJsonList(Person::class.java)
        assertEquals(list, parsed)
    }
}

