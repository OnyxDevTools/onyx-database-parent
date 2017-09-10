package com.onyx.descriptor

import com.onyx.exception.OnyxException
import com.onyx.persistence.annotations.*
import com.onyx.validators.EntityValidation
import java.io.Serializable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

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
        var entity: Entity? = null,
        var identifier: IdentifierDescriptor? = null,
        var partition: PartitionDescriptor? = null,
        var preUpdateCallback: Method? = null,
        var preInsertCallback: Method? = null,
        var preRemoveCallback: Method? = null,
        var prePersistCallback: Method? = null,
        var postUpdateCallback: Method? = null,
        var postInsertCallback: Method? = null,
        var postRemoveCallback: Method? = null,
        var postPersistCallback: Method? = null,
        var attributes: MutableMap<String, AttributeDescriptor> = TreeMap(),
        var indexes: MutableMap<String, IndexDescriptor> = TreeMap(),
        var relationships: MutableMap<String, RelationshipDescriptor> = TreeMap()
) : Serializable {

    /**
     * All fields from entityClass and its descendants
     */
    private val fields: List<Field> by lazy {
        val fields = ArrayList<Field>()

        var tmpClass: Class<*> = entityClass

        while (tmpClass != Any::class.java) {
            fields.addAll(Arrays.asList(*tmpClass.declaredFields))
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

        while (tmpClass != Any::class.java) {
            val declaredMethods = tmpClass.declaredMethods
            declaredMethods.filter { methodMap[it.name] == null }.forEach { methodMap[it.name] = it}
            tmpClass = tmpClass.superclass
        }

        return@lazy methodMap.values
    }

    init {

        EntityValidation.validateIsManagedEntity(entityClass)

        this.entity = entityClass.getAnnotation(Entity::class.java) as Entity

        assignIdentifier()
        assignAttributes()
        assignRelationships()
        assignIndexes()
        assignPartition()
        assignEntityCallbacks()

        // Validate Entity
        EntityValidation.validateIdentifier(entityClass, identifier)
        EntityValidation.validateAttributes(attributes)
        EntityValidation.validateRelationships(entityClass, relationships)
        EntityValidation.validateIndexes(entityClass, indexes)
    }

    /**
     * Find and define identifier
     */
    private fun assignIdentifier() = // Find the first identifier annotation
            fields.find {
                it.getAnnotation(Identifier::class.java) != null
            }.let {
                // Build relationship descriptor
                if(it != null) {
                    val annotation = it.getAnnotation(Identifier::class.java)
                    identifier = IdentifierDescriptor()
                    identifier!!.name = it.name
                    identifier!!.generator = annotation.generator
                    identifier!!.type = it.type
                    identifier!!.loadFactor = annotation.loadFactor.toByte()
                    identifier!!.entityDescriptor = this
                    identifier!!.setReflectionField(it)
                }
            }

    /**
     * Assign attributes for all entities containing @Attribute & @Identifier annotation
     */
    private fun assignAttributes() =
            fields.filter {
                it.getAnnotation(Attribute::class.java) != null || it.getAnnotation(Identifier::class.java) != null || it.getAnnotation(Index::class.java) != null || it.getAnnotation(Partition::class.java) != null
            }
            .forEach {
                // Build Attribute descriptor
                val annotation = it.getAnnotation(Attribute::class.java)
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
                            .forEach { enumValues = enumValues + it.toString() + "," }
                    enumValues = enumValues.substring(0, enumValues.length - 1)
                    enumValues += ";"
                    attribute.enumValues = enumValues
                }

                it.isAccessible = true
                attribute.setReflectionField(it)
                attributes.put(it.name, attribute)
            }

    /**
     * Find and define relationships for all variables containing the Relationship
     */
    private fun assignRelationships() =
        fields.filter {
            it.getAnnotation(Relationship::class.java) != null
        }.forEach {
            // Build relationship descriptor
            val annotation = it.getAnnotation(Relationship::class.java)
            val relationship = RelationshipDescriptor(cascadePolicy = annotation.cascadePolicy, fetchPolicy = annotation.fetchPolicy, inverse = annotation.inverse, inverseClass = annotation.inverseClass.java, parentClass = entityClass, relationshipType = annotation.type)
            relationship.name = it.name
            relationship.type = it.type
            relationship.loadFactor = annotation.loadFactor.toByte()
            relationship.entityDescriptor = this
            relationship.setReflectionField(it)
            relationships.put(it.name, relationship)
        }


    /**
     * Assign Indexes from annotated fields
     */
    private fun assignIndexes() =
        fields.filter {
            it.getAnnotation(Index::class.java) != null
        }.forEach {
            val annotation = it.getAnnotation(Index::class.java)

            // Build Index descriptor
            val index = IndexDescriptor()
            index.name = it.name
            index.loadFactor = annotation.loadFactor
            index.type = it.type
            index.entityDescriptor = this
            index.setReflectionField(it)

            indexes.put(it.name, index)
        }


    /**
     * Get Partition Properties from annotated class
     */
    private fun assignPartition() =
        fields.filter {
            it.getAnnotation(Partition::class.java) != null
        }.forEach {
            if (it.getAnnotation(Partition::class.java) != null) {
                this.partition = PartitionDescriptor()
                this.partition!!.name = it.name
                this.partition!!.type = it.type
                this.partition!!.setReflectionField(it)
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
            "data.dat"
        } else entity!!.fileName

}
