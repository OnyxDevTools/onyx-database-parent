package com.onyx.cloud.extensions

import kotlin.test.*

/**
 * Unit tests for parsing cascade instruction strings.
 */
class CascadeExtensionsTest {
    @Test
    fun `toCascadeInstructions parses single mapping`() {
        val raw = "rel:Type(field1, field2)"
        val instructions = raw.toCascadeInstructions()
        assertEquals(1, instructions.size)
        with(instructions[0]) {
            assertEquals("rel", attribute)
            assertEquals("Type", type)
            assertEquals("field1", targetField)
            assertEquals("field2", sourceField)
        }
    }

    @Test
    fun `toCascadeInstructions parses multiple mappings`() {
        val raw = "a:Type1(x,y),b:Type2(u,v)"
        val instructions = raw.toCascadeInstructions()
        assertEquals(2, instructions.size)
        assertEquals("a", instructions[0].attribute)
        assertEquals("Type1", instructions[0].type)
        assertEquals("x", instructions[0].targetField)
        assertEquals("y", instructions[0].sourceField)
        assertEquals("b", instructions[1].attribute)
        assertEquals("Type2", instructions[1].type)
        assertEquals("u", instructions[1].targetField)
        assertEquals("v", instructions[1].sourceField)
    }

    @Test
    fun `toCascadeInstructions decodes URI components`() {
        val encoded = "r1%3AType%28src%2Ctgt%29"
        val inst = encoded.toCascadeInstructions()
        assertEquals(1, inst.size)
        assertEquals("r1", inst[0].attribute)
        assertEquals("Type", inst[0].type)
        assertEquals("src", inst[0].targetField)
        assertEquals("tgt", inst[0].sourceField)
    }

    @Test
    fun `toCascadeInstructions invalid format throws`() {
        assertFailsWith<IllegalArgumentException> { "invalidFormat".toCascadeInstructions() }
    }
}
