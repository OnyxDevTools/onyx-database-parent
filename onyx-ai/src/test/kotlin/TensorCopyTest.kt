package com.onyxdevtools.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TensorCopyTest {
    
    @Test
    fun testTensorCopy() {
        // Create a test tensor with known dimensions and values
        val rows = 3
        val cols = 4
        val originalTensor = createTensor(rows, cols) { r, c -> (r * cols + c).toFloat() }
        
        // Test copying the tensor
        val copiedTensor = originalTensor.copy()
        
        // Verify dimensions
        assertEquals(rows, copiedTensor.rows)
        assertEquals(cols, copiedTensor.cols)
        
        // Verify values
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                assertEquals(originalTensor[r, c], copiedTensor[r, c])
            }
        }
    }
    
    @Test
    fun testTensorConversions() {
        val rows = 2
        val cols = 3
        val originalTensor = createTensor(rows, cols) { r, c -> (r + c).toFloat() }
        
        // Test toTensorHeap
        val heapTensor = originalTensor.toTensorHeap()
        assertEquals(rows, heapTensor.rows)
        assertEquals(cols, heapTensor.cols)
        
        // Test toTensorMetal
        val metalTensor = originalTensor.toTensorMetal()
        assertEquals(rows, metalTensor.rows)
        assertEquals(cols, metalTensor.cols)
        
        // Test toTensorGPU
        val gpuTensor = originalTensor.toTensorGPU()
        assertEquals(rows, gpuTensor.rows)
        assertEquals(cols, gpuTensor.cols)
        
        // Verify all conversions preserve values
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val originalValue = originalTensor[r, c]
                assertEquals(originalValue, heapTensor[r, c])
                assertEquals(originalValue, metalTensor[r, c])
                assertEquals(originalValue, gpuTensor[r, c])
            }
        }
    }
    
    @Test
    fun testTensorRowAccess() {
        val rows = 5
        val cols = 10
        val tensor = createTensor(rows, cols) { r, c -> (r * 100 + c).toFloat() }
        
        // Test that row access doesn't throw IndexOutOfBoundsException
        for (r in 0 until rows) {
            val row = tensor[r]
            assertEquals(cols, row.size)
            for (c in 0 until cols) {
                assertEquals((r * 100 + c).toFloat(), row[c])
            }
        }
    }
}
