package com.onyx.persistence.function.impl

import com.onyx.persistence.function.QueryFunction
import com.onyx.persistence.query.QueryFunctionType
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Query function that formats values based on their type and a provided pattern
 */
class FormatQueryFunction(attribute: String = "", val pattern: String? = null) :
    BaseQueryFunction(attribute, QueryFunctionType.FORMAT), QueryFunction {

    override fun execute(value: Any?): Any {
        if (value == null) return ""

        return when (value) {
            is Date -> formatDate(value, pattern ?: "yyyy-MM-dd")
            is Number -> formatNumber(value, pattern ?: "#.##")
            else -> value.toString()
        }
    }

    private fun formatDate(date: Date, pattern: String): String {
        return try {
            SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        } catch (e: IllegalArgumentException) {
            // If the pattern is invalid, fall back to default format
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        }
    }

    private fun formatNumber(number: Number, pattern: String): String {
        return try {
            // If the pattern has decimal places, we want to show them for all values
            // e.g., #.## should show 2.00 for value 2.0, not just 2
            val adjustedPattern = if (pattern.contains(".")) {
                adjustZeroPattern(pattern)
            } else {
                pattern
            }
            DecimalFormat(adjustedPattern).format(number)
        } catch (e: IllegalArgumentException) {
            // If the pattern is invalid, fall back to default format
            number.toString()
        }
    }

    private fun adjustZeroPattern(pattern: String): String {
        val parts = pattern.split(".", limit = 2)
        if (parts.size != 2) return pattern
        
        val integerPart = parts[0]
        val decimalPart = parts[1]
        
        // Convert # symbols to 0 for zero values in both integer and decimal parts
        val adjustedIntegerPart = integerPart.replace("#", "0")
        val adjustedDecimalPart = decimalPart.replace("#", "0")
        return "$adjustedIntegerPart.$adjustedDecimalPart"
    }

    override fun newInstance(): QueryFunction = FormatQueryFunction(attribute, pattern)
}
