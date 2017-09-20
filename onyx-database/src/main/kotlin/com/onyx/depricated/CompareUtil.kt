package com.onyx.depricated

import com.onyx.descriptor.EntityDescriptor
import com.onyx.exception.OnyxException
import com.onyx.exception.InvalidDataTypeForOperator
import com.onyx.extension.common.castTo
import com.onyx.extension.get
import com.onyx.extension.getRelationshipFromStore
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.context.SchemaContext
import com.onyx.persistence.query.QueryCriteria
import com.onyx.persistence.query.QueryCriteriaOperator
import com.onyx.util.ReflectionUtil

import java.util.*
import kotlin.reflect.KClass

/**
 * Created by timothy.osborn on 12/14/14.
 *
 * This is a utility class used to compare values against various operators
 */
object CompareUtil {

    /**
     * Cast to will take the value passed in and attempt to cast it
     * as the class passed in.  This is enabled to support java types
     * but, it will use the kotlin class since it does not have to take into
     * account primitives.
     *
     * @param clazz Class to cast to
     * @param value to cast to specified class
     *
     * @return The type casted object
     */
    @JvmStatic
    fun castTo(clazz: Class<*>, value: Any?): Any? {

        val kotlinClass:KClass<*> = clazz.kotlin

        return when {
            // Cast to numeric value when value is null
            value == null -> when(kotlinClass) {
                Int::class -> 0
                Long::class -> 0L
                Double::class -> 0.0
                Float::class -> 0f
                Boolean::class -> false
                Char::class -> '0'
                Byte::class -> 0.toByte()
                Short::class -> 0.toShort()
                else -> null
            }
            // When value is number
            value is Number -> when (kotlinClass) {
                Int::class -> value.toInt()
                Long::class -> value.toLong()
                Double::class -> value.toDouble()
                Float::class -> value.toFloat()
                Boolean::class -> value.toInt() != 0
                Char::class -> value.toChar()
                Byte::class -> value.toByte()
                Short::class -> value.toShort()
                String::class -> value.toString()
                Date::class -> Date(value.toLong())
                else -> null
            }
            clazz == String::class -> return value.toString()
            value is Boolean -> return when (kotlinClass) {
                Date::class -> null
                Int::class -> if (value) 1 else 0
                Long::class -> if (value) 1L else 0L
                Double::class -> if (value) 1.0 else 0.0
                Float::class -> if (value) 1f else 0f
                Boolean::class -> value
                Char::class -> if (value) '1' else '0'
                Byte::class -> if (value) 1.toByte() else 0.toByte()
                Short::class -> if (value) 1.toShort() else 0.toShort()
                else -> null
            }
            value is Char -> return when (kotlinClass) {
                Int::class -> value.toInt()
                Long::class -> value.toLong()
                Double::class -> value.toDouble()
                Float::class -> value.toFloat()
                Boolean::class -> value.toInt() != 0
                Char::class -> value.toChar()
                Byte::class -> value.toByte()
                Short::class -> value.toShort()
                String::class -> value.toString()
                Char::class.javaPrimitiveType -> value.toChar()
                else -> null
            }
            value is Date -> return when (clazz) {
                Long::class -> value.time
                else -> null
            }
            else -> null
        }
    }

    /**
     * Compare without throwing exception
     *
     * @param `compare` First compare to compare
     * @param compareTo Second compare to compare
     * @param operator Operator to compare against
     *
     * @return If the criteria meet return true
     */
    @JvmStatic
    @JvmOverloads
    fun forceCompare(compare: Any, compareTo: Any, operator: QueryCriteriaOperator = QueryCriteriaOperator.EQUAL): Boolean = try {
        compare(compare, compareTo, operator)
        } catch (invalidDataTypeForOperator: InvalidDataTypeForOperator) {
            false
        }

    /**
     * Generic method use to compareTo values with a given operator
     * If there is a type mismatch, the compare object will be cast to the compareTo type.
     * This will have implications on values that could loose precision for instance casting a long to an int.
     *
     * @param compare compare compareTo to compareTo
     * @param compareTo First compareTo to compareTo
     * @param operator Any QueryCriteriaOperator
     * @return whether the objects meet the criteria of the operator
     * @throws InvalidDataTypeForOperator If the values cannot be compared
     */
    @Throws(InvalidDataTypeForOperator::class)
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    @JvmOverloads
    fun compare(compare: Any?, compareTo: Any?, operator: QueryCriteriaOperator? = QueryCriteriaOperator.EQUAL): Boolean {

        var first:Any? = compareTo
        val second:Any? = compare

        if(second != null && first != null && first::class != second::class
                && operator != QueryCriteriaOperator.IN // Expected as List when IN
                && operator != QueryCriteriaOperator.NOT_IN) {
            first = first.castTo(second::class.java)
        }

        try {
            return when (operator) {
                QueryCriteriaOperator.EQUAL -> first == second
                QueryCriteriaOperator.CONTAINS -> (first.toString()).contains(second.toString())
                QueryCriteriaOperator.GREATER_THAN -> {
                    when {
                        first == null && second == null -> false
                        second == null -> true
                        first == null -> false
                        else -> (first as Comparable<Any?>) > (second as Comparable<Any?>)
                    }
                }
                QueryCriteriaOperator.LESS_THAN -> {
                    when {
                        first == null && second == null -> false
                        second == null -> false
                        first == null -> true
                        else -> (first as Comparable<Any?>) < (second as Comparable<Any?>)
                    }
                }
                QueryCriteriaOperator.LESS_THAN_EQUAL -> {
                    when {
                        first == null && second == null -> true
                        second == null -> false
                        first == null -> true
                        else -> (first as Comparable<Any?>) <= (second as Comparable<Any?>)
                    }
                }
                QueryCriteriaOperator.GREATER_THAN_EQUAL -> {
                    when {
                        first == null && second == null -> true
                        second == null -> true
                        first == null -> false
                        else -> (first as Comparable<Any?>) >= (second as Comparable<Any?>)
                    }
                }
                QueryCriteriaOperator.NOT_EQUAL -> first != second
                QueryCriteriaOperator.NOT_STARTS_WITH -> !(first.toString()).startsWith(second.toString())
                QueryCriteriaOperator.NOT_NULL -> first != null
                QueryCriteriaOperator.IS_NULL -> first == null
                QueryCriteriaOperator.STARTS_WITH -> (first.toString()).startsWith(second.toString())
                QueryCriteriaOperator.NOT_CONTAINS -> !(first.toString()).contains(second.toString())
                QueryCriteriaOperator.LIKE -> (first.toString()).equals(second.toString(), true)
                QueryCriteriaOperator.NOT_LIKE -> !(first.toString()).equals(second.toString(), true)
                QueryCriteriaOperator.MATCHES -> (first.toString()).matches(Regex(second.toString()))
                QueryCriteriaOperator.NOT_MATCHES -> !(first.toString()).matches(Regex(second.toString()))
                QueryCriteriaOperator.IN -> {
                    val list = second as List<Any>
                    return list.find { compare(first, it, QueryCriteriaOperator.EQUAL) } != null
                }
                QueryCriteriaOperator.NOT_IN -> {
                    val list = second as List<Any>
                    return list.find { compare(first, it, QueryCriteriaOperator.EQUAL) } == null
                }
                null -> first == second
            }
        } catch (e:Exception) {
            // Comparison operator was not found, we should throw an exception because the data types are not supported
            throw InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR)
        }
    }

    /**
     * Relationship meets criteria.  This method will hydrate a relationship for an entity and
     * check its criteria to ensure the criteria is met
     *
     * @param entity Original entity containing the relationship.  This entity may or may not have
     * hydrated relationships.  For that reason we have to go back to the store to
     * retrieve the relationship entities.
     *
     * @param entityReference Used for quick reference so we do not have to retrieve the entities
     * reference before retrieving the relationship.
     *
     * @param criteria Criteria to check for to see if we meet the requirements
     *
     * @param context Schema context used to pull entity descriptors and record controllers and such
     *
     * @return Whether the relationship value has met all of the criteria
     *
     * @throws OnyxException Something bad happened.
     *
     * @since 1.3.0 - Used to remove the dependency on relationship scanners and to allow query caching
     * to do a quick reference to see if newly saved entities meet the criteria
     */
    @Deprecated("Moved to Query\$Calculation")
    @Throws(OnyxException::class)
    private fun relationshipMeetsCriteria(entity: IManagedEntity, criteria: QueryCriteria<*>, context: SchemaContext): Boolean {
        var meetsCriteria = false

        val items = criteria.attribute!!.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val attribute = items[items.size - 1]
        val relationship = items[items.size - 2]

        // Grab the relationship from the store
        val relationshipEntities = entity.getRelationshipFromStore(context, relationship = relationship)

        // If there are relationship values, check to see if they meet criteria
        if (relationshipEntities!!.isNotEmpty()) {
            meetsCriteria = relationshipEntities.find {
                compare(criteria.value, it.get(context = context, name = attribute), criteria.operator)
            } != null
        }
        return meetsCriteria
    }

    /**
     * Entity meets the query criteria.  This method is used to determine whether the entity meets all the
     * criteria of the query.  It was implemented so that we no longer have logic in the query controller
     * to sift through scans.  We can now only perform a full table scan once.
     *
     * @param allCriteria Contrived list of criteria to
     * @param rootCriteria Criteria to verify whether the entity meets
     * @param entity Entity to check for criteria
     * @param entityReference The entities reference
     * @param context Schema context used to pull entity descriptors, and such
     * @param descriptor Quick reference to the entities descriptor so we do not have to pull it from the schema context
     * @return Whether the entity meets all the criteria.
     * @throws OnyxException Cannot hydrate or pull an attribute from an entity
     *
     * @since 1.3.0 Simplified query criteria management
     */
    @Throws(OnyxException::class)
    @JvmStatic
    @Deprecated("Moved to Query\$Calculation")
    fun meetsCriteria(allCriteria: Set<QueryCriteria<*>>, rootCriteria: QueryCriteria<*>, entity: IManagedEntity, entityReference: Any, context: SchemaContext, descriptor: EntityDescriptor): Boolean {

        var subCriteria: Boolean

        // Iterate through
        allCriteria.forEach {
            if (it.attribute!!.contains(".")) {
                // Compare operator for relationship object
                subCriteria = relationshipMeetsCriteria(entity, it, context)
            } else {
                // Compare operator for attribute object
                if (it.attributeDescriptor == null)
                    it.attributeDescriptor = descriptor.attributes[it.attribute!!]
                val offsetField = it.attributeDescriptor!!.field
                subCriteria = compare(it.value, ReflectionUtil.getAny(entity, offsetField), it.operator)
            }
            it.meetsCriteria = subCriteria
        }

        return calculateCriteriaMet(rootCriteria)
    }

    /**
     * Calculates the result of the parent criteria and correlates
     * its set of children criteria.  A pre-requisite to invoking this method
     * is that all of the criteria have the meet criteria field set and
     * it does NOT take into account the not modifier in the pre-requisite.
     *
     *
     * @param criteria Root criteria to check.  This maintains the order of operations
     * @return Whether all the criteria are met taking into account the order of operations
     * and the not() modifier
     *
     * @since 1.3.0 Added to enhance insertion based criteria checking
     */
    @JvmStatic
    @Deprecated("Moved to Query\$Calculation")
    private fun calculateCriteriaMet(criteria: QueryCriteria<*>): Boolean {
        var meetsCriteria = criteria.meetsCriteria

        if (criteria.subCriteria.size > 0) {
            criteria.subCriteria.forEach {
                meetsCriteria = if (it.isOr) { calculateCriteriaMet(it) || meetsCriteria } else { calculateCriteriaMet(it) && meetsCriteria }
            }
        }

        if (criteria.isNot)
            meetsCriteria = !meetsCriteria
        return meetsCriteria
    }
}
