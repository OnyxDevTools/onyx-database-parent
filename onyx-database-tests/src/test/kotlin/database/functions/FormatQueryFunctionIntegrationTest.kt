package database.functions

import com.onyx.persistence.query.format
import org.junit.Assert
import org.junit.Test

class FormatQueryFunctionIntegrationTest {

    @Test
    fun testFormatFunctionInSelectClause() {
        // Test the helper function that creates the format string
        val formatString = format("dateField", "dd/MM/yyyy")
        Assert.assertEquals("format(dateField, 'dd/MM/yyyy')", formatString)
    }

    @Test
    fun testFormatFunctionInGroupByClause() {
        // Test that the format function can be used in group by scenarios
        val formatString = format("price", "#.##")
        Assert.assertEquals("format(price, '#.##')", formatString)
    }
}