package com.onyx.persistence.query

import com.onyx.buffer.BufferStreamable
import com.onyx.descriptor.AttributeDescriptor
import com.onyx.persistence.IManagedEntity
import com.onyx.persistence.ManagedEntity

import java.util.*

/**
 * Specified query filter criteria.  This equates to a query predicates as well as relationship joins.  This can have nested query criteria.
 *
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 *
 * PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 * QueryCriteria criteria = new QueryCriteria("name", QueryCriteriaOperator.EQUAL, "Bob")
 *      .or("name", QueryCriteriaOperator.LIKE "Jame")
 *      .and(
 *          new QueryCriteria("title", QueryCriteriaOperator.NOT_EQUAL, "The Boss")
 *      .or(new QueryCriteria("job.positionCode", QueryCriteriaOperator.EQUAL, 3)
 * ));
 *
 * Query query = new Query(MyEntity.class, criteria);
 * query.setFirstRow(100);
 * query.setMaxResults(1000);
 *
 * List results = manager.executeQuery(query);
 *
 *
 * @see com.onyx.persistence.manager.PersistenceManager
 *
 * @see com.onyx.persistence.query.Query
 */
@Suppress("ALL")
class QueryCriteria : BufferStreamable {

    constructor()

    var isNot = false
    var level: Int = 0
    var isAnd = false
    var isOr = false
    var flip = false
    var meetsCriteria = false

    var attribute: String? = null
    var operator: QueryCriteriaOperator? = null
    var type: QueryCriteriaType? = null

    // ALL of the value objects are public since they are needed for web services.  Most serializers cannot serialize Any? or Object
    // type so they are strongly typed.  Also they cannot serialize private values.  So, we will have to live with the lint
    // check.  Also, there is no way to suppress WEAKER_ACCESS in kotlin
    var dateValue: Date? = null
    var longValue: Long? = null
    var integerValue: Int? = null
    var booleanValue: Boolean? = null
    var doubleValue: Double? = null
    var stringValue: String? = null

    var floatValue: Float? = null
    var characterValue: Char? = null
    var byteValue: Byte? = null
    var shortValue: Short? = null
    var entityValue: ManagedEntity? = null
    var enumValue: Enum<*>? = null

    var dateValueList: List<Date>? = null
    var longValueList: List<Long>? = null
    var integerValueList: List<Int>? = null
    var doubleValueList: List<Double>? = null
    var stringValueList: List<String>? = null

    var floatValueList: List<Float>? = null
    var characterValueList: List<Char>? = null
    var byteValueList: List<Byte>? = null
    var shortValueList: List<Short>? = null
    var entityValueList: List<ManagedEntity>? = null

    var subCriteria: MutableList<QueryCriteria> = ArrayList()

    @Transient
    var parentCriteria: QueryCriteria? = null

    @Transient
    var attributeDescriptor: AttributeDescriptor? = null

    @Transient
    var isRelationship:Boolean? = null
        get() {
            if(field == null)
                field = attribute?.contains(".") == true
            return field
        }

    /**
     * Constructor with attribute and operator
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     */
    constructor(attribute: String, criteriaEnum: QueryCriteriaOperator) {
        this.attribute = attribute
        this.operator = criteriaEnum
    }

    /**
     * Constructor with long key
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Long key
     */
    constructor(attribute: String, criteriaEnum: QueryCriteriaOperator, value: Any?) {
        this.attribute = attribute
        this.operator = criteriaEnum

        @Suppress("UNCHECKED_CAST")
        when(value)  {
            is Int -> { integerValue = value;                               type = QueryCriteriaType.INTEGER }
            is Long -> { longValue = value;                                 type = QueryCriteriaType.LONG }
            is Boolean -> { booleanValue = value;                           type = QueryCriteriaType.BOOLEAN }
            is Date -> { dateValue = value;                                 type = QueryCriteriaType.DATE }
            is Double -> { doubleValue = value;                             type = QueryCriteriaType.DOUBLE }
            is String -> { stringValue = value;                             type = QueryCriteriaType.STRING }
            is Float -> { floatValue = value;                               type = QueryCriteriaType.FLOAT }
            is Short -> { shortValue = value;                               type = QueryCriteriaType.SHORT }
            is Byte -> { byteValue = value;                                 type = QueryCriteriaType.BYTE }
            is Char -> { characterValue = value;                            type = QueryCriteriaType.CHARACTER }
            is IManagedEntity -> { entityValue = value as ManagedEntity;    type = QueryCriteriaType.ENTITY }
            is Enum<*> -> { enumValue = value;                              type = QueryCriteriaType.ENUM }
            is List<*> -> {
                when(value[0]) {
                    is Int -> { integerValueList = value as List<Int>;                     type = QueryCriteriaType.LIST_INTEGER }
                    is Long -> { longValueList = value as List<Long>;                      type = QueryCriteriaType.LIST_LONG }
                    is Date -> { dateValueList = value as List<Date>;                      type = QueryCriteriaType.LIST_DATE }
                    is Double -> { doubleValueList = value as List<Double>;                type = QueryCriteriaType.LIST_DOUBLE }
                    is String -> { stringValueList = value as List<String>;                type = QueryCriteriaType.LIST_STRING }
                    is Float -> { floatValueList = value as List<Float>;                   type = QueryCriteriaType.LIST_FLOAT }
                    is Short -> { shortValueList = value as List<Short>;                   type = QueryCriteriaType.LIST_SHORT }
                    is Byte -> { byteValueList = value as List<Byte>;                      type = QueryCriteriaType.LIST_BYTE }
                    is Char -> { characterValueList = value as List<Char>;                 type = QueryCriteriaType.LIST_CHARACTER }
                    is IManagedEntity -> { entityValueList = value as List<ManagedEntity>; type = QueryCriteriaType.LIST_ENTITY }
                }
            }

        }
    }

    /**
     * And sub criteria with long key
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Long key
     * @return New Query Criteria with added and sub query
     */
    fun <K : Any> and(attribute: String, criteriaEnum: QueryCriteriaOperator, value: K): QueryCriteria {
        val criteria = QueryCriteria(attribute, criteriaEnum, value)
        criteria.isAnd = true
        subCriteria.add(criteria)
        return this
    }

    /**
     * And with sub-query
     *
     * @since 1.0.0
     * @param andGroup And sub query
     * @return New Query Criteria with added and sub query
     */
    infix fun and(andGroup: QueryCriteria): QueryCriteria {
        andGroup.isAnd = true
        this.subCriteria.add(andGroup)
        return this
    }

    /**
     * Or with long
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value long key
     * @return New Query Criteria with added or sub query
     */
    fun <R : Any> or(attribute: String, criteriaEnum: QueryCriteriaOperator, value: R): QueryCriteria {
        val criteria = QueryCriteria(attribute, criteriaEnum, value)
        criteria.isOr = true
        subCriteria.add(criteria)
        return this
    }

    /**
     * Or with sub-query
     *
     * @since 1.0.0
     * @param orGroup Or Sub Query
     * @return New Query Criteria with added or sub query
     */
    fun or(orGroup: QueryCriteria): QueryCriteria {
        orGroup.isOr = true
        this.subCriteria.add(orGroup)
        return this
    }

    /**
     * Indicate you would like the inverse of the QueryCriteria grouping.
     *
     *
     * Usage:
     *
     *
     * QueryCriteria firstCriteria = new QueryCriteria("age", QueryCriteriaOperator.GREATER_THAN, 18);
     * QueryCriteria secondCriteria = new QueryCriteria("canDrive", QueryCriteriaOperator.EQUAL, true);
     *
     *
     * // first.and(second).not() Criteria
     * persistenceManager.executeQuery(new Query(Person.class, first.and(second).not());
     *
     * The equivalent using DSL would be:
     *
     *
     * val unqualifiedDrivers = db.query(Driver.Data)
     *      .where [ !(age > 18 && canDrive == true) ]
     *      .list
     *
     * @since 1.3.0 Added as enhancement #69
     */
    operator fun not(): QueryCriteria {
        if(subCriteria.isEmpty()) {
            operator = operator!!.inverse // Invert the criteria rather than checking all the criteria
            this.isNot = false
        }
        else {
            val referenceCriteria = QueryCriteria("", QueryCriteriaOperator.EQUAL, "")
            referenceCriteria.flip = true
            and(referenceCriteria)
            isNot = true
        }
        return this
    }

    /**
     * Get Query attribute key
     * @since 1.0.0
     * @return attribute key
     */
    val value: Any?
        get() {
            return when (type) {
                QueryCriteriaType.BOOLEAN -> return booleanValue
                QueryCriteriaType.DATE -> return dateValue
                QueryCriteriaType.INTEGER -> return integerValue
                QueryCriteriaType.LONG -> return longValue
                QueryCriteriaType.DOUBLE -> return doubleValue
                QueryCriteriaType.STRING -> return stringValue
                QueryCriteriaType.FLOAT -> return floatValue
                QueryCriteriaType.CHARACTER -> return characterValue
                QueryCriteriaType.BYTE -> return byteValue
                QueryCriteriaType.SHORT -> return shortValue
                QueryCriteriaType.ENTITY -> return entityValue
                QueryCriteriaType.ENUM -> return enumValue
                QueryCriteriaType.LIST_DATE -> return dateValueList
                QueryCriteriaType.LIST_DOUBLE -> return doubleValueList
                QueryCriteriaType.LIST_INTEGER -> return integerValueList
                QueryCriteriaType.LIST_LONG -> return longValueList
                QueryCriteriaType.LIST_STRING -> return stringValueList
                QueryCriteriaType.LIST_FLOAT -> return floatValueList
                QueryCriteriaType.LIST_CHARACTER -> return characterValueList
                QueryCriteriaType.LIST_BYTE -> return byteValueList
                QueryCriteriaType.LIST_SHORT -> return shortValueList
                QueryCriteriaType.LIST_ENTITY -> return entityValueList
                else -> null
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueryCriteria

        if (isNot != other.isNot) return false
        if (isAnd != other.isAnd) return false
        if (isOr != other.isOr) return false
        if (attribute != other.attribute) return false
        if (operator != other.operator) return false
        if (type != other.type) return false
        if (value != other.value) return false
        if (subCriteria != other.subCriteria) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isNot.hashCode()
        result = 31 * result + isAnd.hashCode()
        result = 31 * result + isOr.hashCode()
        result = 31 * result + flip.hashCode()
        result = 31 * result + (attribute?.hashCode() ?: 0)
        result = 31 * result + (operator?.hashCode() ?: 0)
        result = 31 * result + (value?.hashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + subCriteria.hashCode()
        return result
    }
}
