package com.onyx.classLoader

import com.onyx.entity.SystemEntity
import com.onyx.extension.common.ClassMetadata.classForName
import com.onyx.extension.common.catchAll
import com.onyx.persistence.annotations.values.CascadePolicy
import com.onyx.persistence.annotations.values.FetchPolicy
import com.onyx.persistence.annotations.values.IdentifierGenerator
import com.onyx.persistence.annotations.values.RelationshipType
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.context.impl.CacheSchemaContext

import javax.tools.StandardLocation
import javax.tools.ToolProvider
import java.io.File
import java.io.FileWriter
import java.net.URL
import java.net.URLClassLoader
import java.util.*

/**
 * Created by timothy.osborn on 3/6/15.
 *
 *
 * This class saves the entity information and formats the source on disk
 */
//noinspection SpellCheckingInspection
object EntityClassLoader {

    private val LOADED_CLASSES: MutableSet<String> = HashSet()
    private val WRITTEN_CLASSES: MutableSet<String> = HashSet()

    private val GENERATED_DIRECTORY = "generated"
    private val GENERATED_ENTITIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "entities"
    private val GENERATED_QUERIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "queries"

    private val SOURCE_DIRECTORY = "source"
    private val XTEND_SOURCE_DIRECTORY = "xtend"
    private val SOURCE_ENTITIES_DIRECTORY = SOURCE_DIRECTORY + File.separator + "entities"
    private val XTEND_SOURCE_ENTITIES_DIRECTORY = XTEND_SOURCE_DIRECTORY + File.separator + "entities"

    /**
     * Write an enum to file
     * @param enumClass Enum Class Name
     * @param enumValues Enum values separated by comma and ended with a semicolon.
     * @param outputDirectory Source directory
     */
    private fun writeEnum(enumClass: String?, enumValues: String?, outputDirectory: String) {
        val nameTokens = enumClass!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var packageName = "package "
        for (i in 0 until nameTokens.size - 1)
            packageName = packageName + nameTokens[i] + "."

        packageName = packageName.substring(0, packageName.length - 1)
        packageName += ";\n\n"

        val classDef = "public enum " + nameTokens[nameTokens.size - 1] + " { \n"
        val end = "\n}"
        val classContents = packageName + classDef + enumValues + end

        writeClassContents(outputDirectory, enumClass, classContents)
    }

    /**
     * Helper method used to write the class contents to a file
     *
     * @param outputDirectory Source directory
     * @param className Name of class including package
     * @param classContents Class source code
     * @param extension file extension
     */
    private fun writeClassContents(outputDirectory: String, className: String, classContents: String, extension: String = ".java") {
        // File output
        val classFile = File(outputDirectory + File.separator + className.replace("\\.".toRegex(), "/") + extension)
        classFile.parentFile.mkdirs()
        classFile.createNewFile()

        val file = FileWriter(outputDirectory + File.separator + className.replace("\\.".toRegex(), "/") + extension)

        file.write(classContents)
        file.flush()
        file.close()
    }

    /**
     * Write Xtend class in order to support DSL within the Database Browser
     *
     * @param systemEntity Class entity metadata
     * @param databaseLocation Location where the database lives
     * @param schemaContext Database context
     * @param idType Entity identifier type
     * @param idName Name of the identifier attribute
     */
    private fun writeXtendClass(systemEntity: SystemEntity, databaseLocation: String, schemaContext: SchemaContext, idType: String, idName: String) {
        val packageName = systemEntity.name.replace("." + systemEntity.className!!, "")
        val outputDirectory = databaseLocation + File.separator + XTEND_SOURCE_ENTITIES_DIRECTORY
        val className = systemEntity.className
        val generatorType = IdentifierGenerator.values()[systemEntity.identifier!!.generator.toInt()].toString()
        val fileName = systemEntity.fileName
        val idLoadFactor = systemEntity.identifier!!.loadFactor.toString()

        val builder = StringBuilder()
        builder.append("package ")
        builder.append(packageName)
        builder.append("\n" +
                "\n" +
                "import com.onyx.persistence.annotations.*\n" +
                "import com.onyx.persistence.*\n" +
                "import net.sagittarian.onyx.annotations.OnyxFields\n" +
                "import net.sagittarian.onyx.annotations.OnyxJoins\n" +
                "import org.eclipse.xtend.lib.annotations.Accessors" +
                "\n\n" +
                "@Entity(fileName = \"")
                .append(fileName)
                .append("\")\n")
                .append("@OnyxFields\n")
                .append("@OnyxJoins\n")
                .append("@Accessors\n")
                .append("class ")
                .append(className)
                .append(" extends ManagedEntity implements IManagedEntity\n{\n\n")
                .append("    @Attribute\n")
                .append("    @Identifier(generator = ")
                .append(generatorType)
                .append(", loadFactor = ")
                .append(idLoadFactor)
                .append(")\n    ")
                .append(idType)
                .append(" ")
                .append(idName)
                .append(";\n\n")

        systemEntity.attributes.forEach {
            if (it.name == systemEntity.identifier!!.name)
                return@forEach

            if (it.isEnum)
                writeEnum(it.dataType, it.enumValues, outputDirectory)

            val isIndex = systemEntity.indexes.firstOrNull { (name) -> name == it.name} != null

            builder.append("\n    @Attribute\n")
            if (isIndex)
                builder.append("    @Index\n")

            if (systemEntity.partition != null && systemEntity.partition!!.name == it.name)
                builder.append("    @Partition\n")

            builder.append("    ").append(it.dataType).append(" ").append(it.name).append("\n")
        }

        builder.append("\n\n")

        systemEntity.relationships.forEach {
            val type: String
            val genericType = it.inverseClass

            type = if (it.relationshipType.toInt() == RelationshipType.ONE_TO_MANY.ordinal || it.relationshipType.toInt() == RelationshipType.MANY_TO_MANY.ordinal) {
                val collectionClass = List::class.java.name
                "$collectionClass<$genericType>"
            } else {
                genericType
            }

            builder.append("    @Relationship(type = ")
                    .append(RelationshipType.values()[it.relationshipType.toInt()].name)
                    .append(", inverseClass = ")
                    .append(genericType)
                    .append(", inverse = \"")
                    .append(it.inverse)
                    .append("\", fetchPolicy = ")
                    .append(FetchPolicy.values()[it.fetchPolicy.toInt()].name)
                    .append(", cascadePolicy = ")
                    .append(CascadePolicy.values()[it.cascadePolicy.toInt()].name)
                    .append(", loadFactor = ")
                    .append(it.loadFactor.toString())
                    .append(")\n    ")
                    .append(type)
                    .append(" ")
                    .append(it.name)
                    .append("\n")

            // Get the base Descriptor so that we ensure they get generated also
            catchAll {
                schemaContext.getBaseDescriptorForEntity(classForName(genericType))
            }
        }

        builder.append("\n}\n")

        writeClassContents(outputDirectory, systemEntity.name, builder.toString(), ".xtend")
    }

    /**
     * Generate Write a class to disk.  This is used for remote purposes.  Since we do not have a handle on the entity descriptors, we use the system entity to load
     *
     * @param systemEntity     System entity
     * @param databaseLocation Database location
     */
    @Synchronized
    fun writeClass(systemEntity: SystemEntity, databaseLocation: String, context: SchemaContext) {
        if (context is CacheSchemaContext)
            return

        val outputDirectory = databaseLocation + File.separator + SOURCE_ENTITIES_DIRECTORY
        File(outputDirectory).mkdirs()

        WRITTEN_CLASSES.add(systemEntity.name)

        val attribute = systemEntity.attributes.find { it.name == systemEntity.identifier!!.name }!!
        writeXtendClass(systemEntity, databaseLocation, context, attribute.dataType!!, attribute.name)
    }

    /**
     * Load the class source from file and load it into class loader.
     *
     * @param context Schema Context
     * @param location Location to load classes from
     */
    @JvmOverloads
    fun loadClasses(context: SchemaContext, location: String = context.location) {

        val entitiesSourceDirectory = File(location + File.separator + SOURCE_ENTITIES_DIRECTORY)
        val entitiesGeneratedDirectory = File(location + File.separator + GENERATED_ENTITIES_DIRECTORY)
        entitiesGeneratedDirectory.mkdirs()
        entitiesSourceDirectory.mkdirs()

        val compiler = ToolProvider.getSystemJavaCompiler()
        val fileManager = compiler.getStandardFileManager(null, null, null)

        val classes = ArrayList<File>()

        File(entitiesGeneratedDirectory.path).walkTopDown().filter {it.isDirectory && !it.isHidden && it.path.endsWith(".java") }.forEach { classes.add(it) }
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, listOf(entitiesGeneratedDirectory))

        // Compile the file
        compiler.getTask(null, fileManager, null, null, null, fileManager.getJavaFileObjectsFromFiles(classes)).call()

        fileManager.close()

        val systemClassLoader = ClassLoader.getSystemClassLoader() as URLClassLoader

        addClassPaths(context)

        File(entitiesSourceDirectory.path).walkTopDown().filter {it.isDirectory && !it.isHidden && it.path.endsWith(".java") }.forEach {
            var path = it.path.replace(entitiesSourceDirectory.path + File.separator, "")
            path = path.replace("\\.java".toRegex(), "")
            path = path.replace("\\\\".toRegex(), ".")
            path = path.replace("/".toRegex(), ".")
            try {
                LOADED_CLASSES.add(systemClassLoader.loadClass(path).name)
            } catch (e:Exception) { e.printStackTrace() }
        }
    }

    /**
     * Add class paths for a database
     *
     * @param schemaContext Context of the database
     */
    private fun addClassPaths(schemaContext: SchemaContext) {
        val systemClassLoader = ClassLoader.getSystemClassLoader() as URLClassLoader

        // Add URL to class path
        val systemClass = URLClassLoader::class.java

        val method = systemClass.getDeclaredMethod("addURL", URL::class.java)
        method.isAccessible = true
        method.invoke(systemClassLoader, File(schemaContext.location + File.separator + GENERATED_ENTITIES_DIRECTORY).toURI().toURL())
        method.invoke(systemClassLoader, File(schemaContext.location + File.separator + GENERATED_QUERIES_DIRECTORY).toURI().toURL())
    }
}
