package com.onyx.ai.agent

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertTrue

class IntegrationTest {
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeEach
    fun setup() {
        ProjectIO.root = tempDir
    }
    
    @Test
    fun testCompleteKotlinProjectCreation() {
        // Create a mock AI agent that simulates creating a complete Kotlin project
        val mockAgent = MockCodingAgent()
        
        // Test request: Create a simple calculator project
        val request = """
            Create a Kotlin calculator project with:
            1. A Calculator class with add, subtract, multiply, divide functions
            2. Unit tests for all functions
            3. A build.gradle.kts file
        """.trimIndent()
        
        // Execute the request
        mockAgent.executeScenario()
        
        // Verify the project structure was created
        assertTrue(tempDir.resolve("src/main/kotlin/Calculator.kt").toFile().exists())
        assertTrue(tempDir.resolve("src/test/kotlin/CalculatorTest.kt").toFile().exists())
        assertTrue(tempDir.resolve("build.gradle.kts").toFile().exists())
        
        // Verify file contents
        val calculatorContent = ProjectIO.read("src/main/kotlin/Calculator.kt")
        assertTrue(calculatorContent.contains("class Calculator"))
        assertTrue(calculatorContent.contains("fun add"))
        assertTrue(calculatorContent.contains("fun subtract"))
        
        val testContent = ProjectIO.read("src/test/kotlin/CalculatorTest.kt")
        assertTrue(testContent.contains("@Test"))
        assertTrue(testContent.contains("testAdd"))
    }
    
    @Test
    fun testProjectCompilation() {
        // Create a minimal working Kotlin project
        createMinimalProject()
        
        // Verify files exist and contain expected content
        val mainClass = ProjectIO.read("src/main/kotlin/HelloWorld.kt")
        assertTrue(mainClass.contains("fun main"))
        
        val buildFile = ProjectIO.read("build.gradle.kts")
        assertTrue(buildFile.contains("kotlin(\"jvm\")"))
    }
    
    private fun createMinimalProject() {
        // Create a minimal Kotlin project that will compile
        ProjectIO.write("build.gradle.kts", """
plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
        """.trimIndent())
        
        ProjectIO.write("src/main/kotlin/HelloWorld.kt", """
package com.example

fun main() {
    println("Hello, World!")
    val result = Calculator().add(2, 3)
    println("2 + 3 = ${'$'}result")
}

class Calculator {
    fun add(a: Int, b: Int): Int = a + b
    fun subtract(a: Int, b: Int): Int = a - b
    fun multiply(a: Int, b: Int): Int = a * b
    fun divide(a: Int, b: Int): Double = a.toDouble() / b.toDouble()
}
        """.trimIndent())
        
        ProjectIO.write("src/test/kotlin/CalculatorTest.kt", """
package com.example

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CalculatorTest {
    private val calculator = Calculator()
    
    @Test
    fun testAdd() {
        assertEquals(5, calculator.add(2, 3))
        assertEquals(0, calculator.add(-1, 1))
    }
    
    @Test  
    fun testSubtract() {
        assertEquals(1, calculator.subtract(3, 2))
        assertEquals(-2, calculator.subtract(-1, 1))
    }
    
    @Test
    fun testMultiply() {
        assertEquals(6, calculator.multiply(2, 3))
        assertEquals(0, calculator.multiply(0, 5))
    }
    
    @Test
    fun testDivide() {
        assertEquals(2.5, calculator.divide(5, 2))
        assertEquals(1.0, calculator.divide(3, 3))
    }
}
        """.trimIndent())
        
        // Create gradle wrapper properties to ensure compatibility
        ProjectIO.write("gradle/wrapper/gradle-wrapper.properties", """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
        """.trimIndent())
    }
}

/**
 * Mock AI agent that simulates the behavior without calling actual LLM
 */
class MockCodingAgent {
    fun executeScenario() {
        // Simulate creating files that the AI agent would create
        
        // 1. Create build.gradle.kts
        ProjectIO.write("build.gradle.kts", """
plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
        """.trimIndent())
        
        // 2. Create Calculator.kt
        ProjectIO.write("src/main/kotlin/Calculator.kt", """
package com.example

class Calculator {
    fun add(a: Double, b: Double): Double = a + b
    
    fun subtract(a: Double, b: Double): Double = a - b
    
    fun multiply(a: Double, b: Double): Double = a * b
    
    fun divide(a: Double, b: Double): Double {
        require(b != 0.0) { "Cannot divide by zero" }
        return a / b
    }
}
        """.trimIndent())
        
        // 3. Create CalculatorTest.kt
        ProjectIO.write("src/test/kotlin/CalculatorTest.kt", """
package com.example

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows

class CalculatorTest {
    private val calculator = Calculator()
    
    @Test
    fun testAdd() {
        assertEquals(5.0, calculator.add(2.0, 3.0), 0.001)
        assertEquals(0.0, calculator.add(-1.0, 1.0), 0.001)
    }
    
    @Test
    fun testSubtract() {
        assertEquals(1.0, calculator.subtract(3.0, 2.0), 0.001)
        assertEquals(-2.0, calculator.subtract(-1.0, 1.0), 0.001)
    }
    
    @Test
    fun testMultiply() {
        assertEquals(6.0, calculator.multiply(2.0, 3.0), 0.001)
        assertEquals(0.0, calculator.multiply(0.0, 5.0), 0.001)
    }
    
    @Test
    fun testDivide() {
        assertEquals(2.5, calculator.divide(5.0, 2.0), 0.001)
        assertEquals(1.0, calculator.divide(3.0, 3.0), 0.001)
        
        assertThrows(IllegalArgumentException::class.java) {
            calculator.divide(5.0, 0.0)
        }
    }
}
        """.trimIndent())
    }
}
