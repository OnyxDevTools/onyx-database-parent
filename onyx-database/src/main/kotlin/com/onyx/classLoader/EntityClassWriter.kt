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

import java.io.File
import java.io.FileWriter
import java.util.*

/**
 * Created by timothy.osborn on 3/6/15.
 *
 *
 * This class saves the entity information and formats the source on disk
 */
//noinspection SpellCheckingInspection
object EntityClassWriter {

    @Suppress("MemberVisibilityCanPrivate")
    val WRITTEN_CLASSES: MutableSet<String> = HashSet()
    @Suppress("MemberVisibilityCanPrivate")
    val OUTPUT_DIRECTORY = "target"
    @Suppress("UNUSED")
    val OUTPUT_ENTITIES_DIRECTORY = OUTPUT_DIRECTORY + File.separator + "entities"
    @Suppress("UNUSED")
    val OUTPUT_QUERIES_DIRECTORY = OUTPUT_DIRECTORY + File.separator + "queries"
    @Suppress("MemberVisibilityCanPrivate")
    val SOURCE_DIRECTORY = "source"
    @Suppress("MemberVisibilityCanPrivate")
    val SOURCE_ENTITIES_DIRECTORY = SOURCE_DIRECTORY + File.separator + "entities"

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
        val outputDirectory = databaseLocation + File.separator + SOURCE_ENTITIES_DIRECTORY
        val className = systemEntity.className
        val generatorType = IdentifierGenerator.values()[systemEntity.identifier!!.generator.toInt()].toString()
        val fileName = systemEntity.fileName
        val idLoadFactor = systemEntity.identifier!!.loadFactor.toString()

        val builder = StringBuilder()
        builder.append("package ")
        builder.append(packageName)
        builder.append(";\n" +
                "\n" +
                "import com.onyx.persistence.annotations.*;\n" +
                "import com.onyx.persistence.annotations.values.*;\n" +
                "import com.onyx.persistence.collections.*;\n" +
                "import com.onyx.persistence.query.*;\n" +
                "import com.onyx.persistence.stream.*;\n" +
                "import com.onyx.persistence.*;\n" +
                "\n\n" +
                "@Entity(fileName = \"")
                .append(fileName)
                .append("\")\n")
                .append("public class ")
                .append(className)
                .append(" extends ManagedEntity implements IManagedEntity\n{\n\n")
                .append("    @Attribute\n")
                .append("    @Identifier(generator = IdentifierGenerator.")
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

            builder.append("    ").append(it.dataType).append(" ").append(it.name).append(";\n")
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

            builder.append("    @Relationship(type = RelationshipType.")
                    .append(RelationshipType.values()[it.relationshipType.toInt()].name)
                    .append(", inverseClass = ")
                    .append(genericType)
                    .append(".class, inverse = \"")
                    .append(it.inverse)
                    .append("\", fetchPolicy = FetchPolicy.")
                    .append(FetchPolicy.values()[it.fetchPolicy.toInt()].name)
                    .append(", cascadePolicy = CascadePolicy.")
                    .append(CascadePolicy.values()[it.cascadePolicy.toInt()].name)
                    .append(", loadFactor = ")
                    .append(it.loadFactor.toString())
                    .append(")\n    ")
                    .append(type)
                    .append(" ")
                    .append(it.name)
                    .append(";\n")

            // Get the base Descriptor so that we ensure they get generated also
            catchAll {
                schemaContext.getBaseDescriptorForEntity(classForName(genericType))
            }
        }

        builder.append("\n}\n")

        writeClassContents(outputDirectory, systemEntity.name, builder.toString(), ".java")
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

}
