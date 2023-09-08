
package com.onyx.descriptor

import com.onyx.extension.common.ClassMetadata
import com.onyx.exception.OnyxException
import com.onyx.persistence.annotations.*
import com.onyx.persistence.context.SchemaContext
import com.onyx.extension.validate
import com.onyx.extension.validateIsManagedEntity
import java.io.File
import java.io.Serializable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

/**
 * Created by timothy.osborn on 12/11/14.
 *
 *
 * This class defines the properties of an entity
 */
data class EntityDescriptor
@Throws(OnyxException::class)
@JvmOverloads
constructor(
        val entityClass: Class<*>,
        var partition: PartitionDescriptor? = null
) : Serializable {

    var entity: Entity? = null
    var identifier: IdentifierDescriptor? = null
    var preUpdateCallback: Method? = null
    var preInsertCallback: Method? = null
    var preRemoveCallback: Method? = null
    var prePersistCallback: Method? = null
    var postUpdateCallback: Method? = null
    var postInsertCallback: Method? = null
    var postRemoveCallback: Method? = null
    var postPersistCallback: Method? = null
    var attributes: MutableMap<String, AttributeDescriptor> = TreeMap()
    var indexes: MutableMap<String, IndexDescriptor> = TreeMap()
    var relationships: MutableMap<String, RelationshipDescriptor> = TreeMap()

    lateinit var context: SchemaContext

    /**
     * All fields from entityClass and its descendants
     */
    private val fields: List<Field> by lazy {
        val fields = ArrayList<Field>()

        var tmpClass: Class<*> = entityClass

        while (tmpClass != ClassMetadata.ANY_CLASS) {
            val newFields = listOf(*tmpClass.declaredFields)
            // Only add ones that have not already been added
            fields.addAll(newFields.filter { newField ->
                fields.find { it.name == newField.name } == null
            })

            tmpClass = tmpClass.superclass
        }
        return@lazy fields
    }

    /**
     * All methods from entityClass and its descendants
     */
    private val methods: Collection<Method> by lazy {
        val methodMap:MutableMap<String, Method> = HashMap()
        var tmpClass: Class<*> = entityClass

        while (tmpClass != ClassMetadata.ANY_CLASS) {
            val declaredMethods = tmpClass.declaredMethods
            declaredMethods.filter { methodMap[it.name] == null }.forEach { methodMap[it.name] = it}
            tmpClass = tmpClass.superclass
        }

        return@lazy methodMap.values
    }

    /**
     * Reflection fields used to get all fields for an entity including attributes and relationships
     */
    val reflectionFields: Map<String, Field> by lazy {
        val returnValue = LinkedHashMap<String, Field>()
        attributes.values.forEach { returnValue[it.name] = it.field }
        relationships.values.forEach { returnValue[it.name] = it.field }
        return@lazy returnValue
    }


    init {

        validateIsManagedEntity()

        this.entity = entityClass.getAnnotation(ClassMetadata.ENTITY_ANNOTATION) as Entity

        assignIdentifier()
        assignAttributes()
        assignRelationships()
        assignIndexes()
        assignPartition()
        assignEntityCallbacks()

        // Validate Entity
        validate()
    }

    /**
     * Find and define identifier
     */
    private fun assignIdentifier() = // Find the first identifier annotation
            fields.find {
                it.getAnnotation(ClassMetadata.IDENTIFIER_ANNOTATION) != null
            }.let {
                // Build relationship descriptor
                if(it != null) {
                    val annotation = it.getAnnotation(ClassMetadata.IDENTIFIER_ANNOTATION)
                    identifier = IdentifierDescriptor()
                    identifier!!.name = it.name
                    identifier!!.generator = annotation.generator
                    identifier!!.type = it.type
                    identifier!!.entityDescriptor = this
                    it.isAccessible = true
                    identifier!!.field = it
                }
            }

    /**
     * Assign attributes for all entities containing @Attribute & @Identifier annotation
     */
    private fun assignAttributes() =
            fields.filter {
                it.getAnnotation(ClassMetadata.ATTRIBUTE_ANNOTATION) != null || it.getAnnotation(ClassMetadata.IDENTIFIER_ANNOTATION) != null || it.getAnnotation(ClassMetadata.INDEX_ANNOTATION) != null || it.getAnnotation(ClassMetadata.PARTITION_ANNOTATION) != null
            }
            .forEach { it ->
                // Build Attribute descriptor
                val annotation = it.getAnnotation(ClassMetadata.ATTRIBUTE_ANNOTATION)
                val attribute = AttributeDescriptor()
                attribute.name = it.name
                attribute.type = it.type
                attribute.isNullable = annotation?.nullable != false
                attribute.size = annotation?.size ?: -1
                attribute.isEnum = attribute.type.isEnum

                if (attribute.isEnum) {
                    var enumValues = ""
                    attribute.type.enumConstants
                            .asSequence()
                            .map { it as Enum<*> }
                            .forEach { enumValues = "$enumValues$it," }
                    enumValues = enumValues.substring(0, enumValues.length - 1)
                    enumValues += ";"
                    attribute.enumValues = enumValues
                }

                it.isAccessible = true
                attribute.field = it
                attributes[it.name] = attribute
            }

    /**
     * Find and define relationships for all variables containing the Relationship
     */
    private fun assignRelationships() =
        fields.filter {
            it.getAnnotation(ClassMetadata.RELATIONSHIP_ANNOTATION) != null
        }.forEach {
            // Build relationship descriptor
            val annotation = it.getAnnotation(ClassMetadata.RELATIONSHIP_ANNOTATION)
            val relationship = RelationshipDescriptor(cascadePolicy = annotation.cascadePolicy, fetchPolicy = annotation.fetchPolicy, inverse = annotation.inverse, inverseClass = annotation.inverseClass.java, parentClass = entityClass, relationshipType = annotation.type)
            relationship.name = it.name
            relationship.type = it.type
            relationship.entityDescriptor = this
            it.isAccessible = true
            relationship.field = it
            relationships[it.name] = relationship
        }


    /**
     * Assign Indexes from annotated fields
     */
    private fun assignIndexes() =
        fields.filter {
            it.getAnnotation(ClassMetadata.INDEX_ANNOTATION) != null
        }.forEach {
            // Build Index descriptor
            val index = IndexDescriptor()
            index.name = it.name
            index.type = it.type
            index.entityDescriptor = this
            it.isAccessible = true
            index.field = it

            indexes[it.name] = index
        }


    /**
     * Get Partition Properties from annotated class
     */
    private fun assignPartition() =
        fields.filter {
            it.getAnnotation(ClassMetadata.PARTITION_ANNOTATION) != null
        }.forEach {
            if (it.getAnnotation(ClassMetadata.PARTITION_ANNOTATION) != null) {
                this.partition = PartitionDescriptor()
                this.partition!!.name = it.name
                this.partition!!.type = it.type
                it.isAccessible = true
                this.partition!!.field = it
            }
        }


    /**
     * Get entity callbacks from the annotations on the entity.
     *
     */
    private fun assignEntityCallbacks() {
        this.preInsertCallback = methods.find { it.isAnnotationPresent(PreInsert::class.java) }
        this.preUpdateCallback = methods.find { it.isAnnotationPresent(PreUpdate::class.java) }
        this.preRemoveCallback = methods.find { it.isAnnotationPresent(PreRemove::class.java) }
        this.prePersistCallback = methods.find { it.isAnnotationPresent(PrePersist::class.java) }
        this.postUpdateCallback = methods.find { it.isAnnotationPresent(PostUpdate::class.java) }
        this.postInsertCallback = methods.find { it.isAnnotationPresent(PostInsert::class.java) }
        this.postRemoveCallback = methods.find { it.isAnnotationPresent(PostRemove::class.java) }
        this.postPersistCallback = methods.find { it.isAnnotationPresent(PostPersist::class.java) }

        this.preInsertCallback?.isAccessible = true
        this.preUpdateCallback?.isAccessible = true
        this.preRemoveCallback?.isAccessible = true
        this.prePersistCallback?.isAccessible = true
        this.postUpdateCallback?.isAccessible = true
        this.postInsertCallback?.isAccessible = true
        this.postRemoveCallback?.isAccessible = true
        this.postPersistCallback?.isAccessible = true
    }

    /**
     * Get file name for data storage.
     *
     * @return get file name for data storage.
     */
    val fileName: String
        get() = if (entity!!.fileName == "") {
            DEFAULT_DATA_FILE
        } else entity!!.fileName

    val archiveDirectories: Array<String>
        get() = entity?.archiveDirectories ?: arrayOf()

    private var _primaryLocation: String? = null
    var primaryLocationSearched: Boolean = false

    val primaryLocation: String?
        get() {
            if(!primaryLocationSearched) {
                val key = if (this.partition == null) this.fileName else this.fileName + this.partition!!.partitionValue
                _primaryLocation = archiveDirectories.firstOrNull {
                    File("${it}/$key").exists()
                }
                primaryLocationSearched = true
            }
            return _primaryLocation
        }

    val hasIndexes: Boolean
        get() = indexes.isNotEmpty()

    val hasRelationships: Boolean
        get() = relationships.isNotEmpty()

    val hasPartition:Boolean
        get() = partition != null
}

const val DEFAULT_DATA_FILE = "data.dat"