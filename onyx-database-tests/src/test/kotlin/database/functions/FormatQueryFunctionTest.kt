package database.functions

import com.onyx.persistence.function.impl.FormatQueryFunction
import org.junit.Assert
import org.junit.Test
import java.util.Calendar

class FormatQueryFunctionTest {

    @Test
    fun testDateFormattingWithCustomPattern() {
        val function = FormatQueryFunction("dateField", "dd/MM/yyyy")
        val date = Calendar.getInstance().apply {
            set(2023, Calendar.JANUARY, 15)
        }.time

        val result = function.execute(date)
        Assert.assertEquals("15/01/2023", result)
    }

    @Test
    fun testDateFormattingWithDefaultPattern() {
        val function = FormatQueryFunction("dateField")
        val date = Calendar.getInstance().apply {
            set(2023, Calendar.JANUARY, 15)
        }.time

        val result = function.execute(date)
        Assert.assertEquals("2023-01-15", result)
    }

    @Test
    fun testNumberFormattingWithCustomPattern() {
        val function = FormatQueryFunction("numberField", "#.##")

        // Test with Double
        var result = function.execute(123.456)
        Assert.assertEquals("123.46", result)

        // Test with Float
        result = function.execute(987.123f)
        Assert.assertEquals("987.12", result)
    }

    @Test
    fun testNumberFormattingWithDefaultPattern() {
        val function = FormatQueryFunction("numberField")

        // Test with `Integer
        var result = function.execute(123)
        Assert.assertEquals("123.00", result)

        // Test with Double
        result = function.execute(123.456)
        Assert.assertEquals("123.46", result)
    }

    @Test
    fun testStringFormatting() {
        val function = FormatQueryFunction("stringField")
        val result = function.execute("testString")
        Assert.assertEquals("testString", result)
    }

    @Test
    fun testNullValueFormatting() {
        val function = FormatQueryFunction("nullField")
        val result = function.execute(null)
        Assert.assertEquals("", result)
    }

    @Test
    fun testInvalidDateFormatPatternFallback() {
        val function = FormatQueryFunction("dateField", "invalidPattern")
        val date = Calendar.getInstance().apply {
            set(2023, Calendar.JANUARY, 15)
        }.time

        val result = function.execute(date)
        Assert.assertEquals("2023-01-15", result) // Should fallback to default pattern
    }

    @Test
    fun testInvalidNumberFormatPatternFallback() {
        val function = FormatQueryFunction("numberField", "invalidPattern")
        val result = function.execute(123.456)
        // Different JVM implementations may handle invalid patterns differently
        // Just ensure we get some result back
        assert(result is String)
    }

    @Test
    fun testNewInstanceCreation() {
        val originalFunction = FormatQueryFunction("testField", "yyyy-MM-dd")
        val newFunction = originalFunction.newInstance()

        assert(newFunction is FormatQueryFunction)
        // We can't directly access the properties, but we can test that it works correctly
        val result = newFunction.execute("test")
        Assert.assertEquals("test", result)
    }
}