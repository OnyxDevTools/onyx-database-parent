package com.onyx.persistence.query

import com.onyx.buffer.BufferStreamable
import com.onyx.descriptor.AttributeDescriptor

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
    var value:Any? = null
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
        this.value = value
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
    infix fun or(orGroup: QueryCriteria): QueryCriteria {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueryCriteria

        if (isNot != other.isNot) return false
        if (isAnd != other.isAnd) return false
        if (isOr != other.isOr) return false
        if (attribute != other.attribute) return false
        if (operator != other.operator) return false
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
        result = 31 * result + subCriteria.hashCode()
        return result
    }
}
