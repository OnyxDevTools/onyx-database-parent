package com.onyx.persistence.query;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.diskmap.serializer.ObjectBuffer;
import com.onyx.diskmap.serializer.ObjectSerializable;
import com.onyx.persistence.ManagedEntity;
import com.onyx.util.CompareUtil;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Specified query filter criteria.  This equates to a query predicates as well as relationship joins.  This can have nested query criteria.
 *
 *
 * @author Chris Osborn
 * @since 1.0.0
 *
 * <pre>
 * <code>
 *
 *   PersistenceManager manager = factory.getPersistenceManager(); // Get the Persistence manager from the persistence manager factory
 *
 *   QueryCriteria criteria = new QueryCriteria("name", QueryCriteriaOperator.EQUAL, "Bob")
 *                                .or("name", QueryCriteriaOperator.LIKE "Jame")
 *                                .and(
 *                                  new QueryCriteria("title", QueryCriteriaOperator.NOT_EQUAL, "The Boss")
 *                                  .or(new QueryCriteria("job.positionCode", QueryCriteriaOperator.EQUAL, 3)
 *                                  ));
 *
 *   Query query = new Query(MyEntity.class, criteria);
 *   query.setFirstRow(100);
 *   query.setMaxResults(1000);
 *
 *   List results = manager.executeQuery(query);
 *
 * </code>
 * </pre>
 *
 * @see com.onyx.persistence.manager.PersistenceManager
 * @see com.onyx.persistence.query.Query
 *
 */
@SuppressWarnings({"unused", "unchecked", "SuspiciousToArrayCall"})
public class QueryCriteria implements ObjectSerializable, Serializable
{

    @SuppressWarnings("unused")
    public static Boolean NULL_BOOLEAN_VALUE = null;
    @SuppressWarnings("unused")
    public static int NULL_INTEGER_VALUE = Integer.MIN_VALUE;
    @SuppressWarnings("unused")
    public static long NULL_LONG_VALUE = Long.MIN_VALUE;
    @SuppressWarnings("unused")
    public static double NULL_DOUBLE_VALUE = Double.MIN_VALUE;
    @SuppressWarnings("unused")
    public static Date NULL_DATE_VALUE = new Date(Long.MIN_VALUE);
    @SuppressWarnings("unused")
    public static String NULL_STRING_VALUE = null;

    private boolean not = false;

    private int level;

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * Default Constructor
     */
    @SuppressWarnings("unused")
    public QueryCriteria()
    {

    }

    /**
     * Comparison properties
     */
    @SuppressWarnings("unused")
    private String attribute;
    @SuppressWarnings("unused")
    private QueryCriteriaOperator operator;
    @SuppressWarnings("unused")
    private QueryCriteriaType type;

    @SuppressWarnings("unused")
    private Date dateValue;
    @SuppressWarnings("unused")
    private Long longValue;
    @SuppressWarnings("unused")
    private Integer integerValue;
    @SuppressWarnings("unused")
    private Boolean booleanValue;
    @SuppressWarnings("unused")
    private Double doubleValue;
    @SuppressWarnings("unused")
    private String stringValue;

    @SuppressWarnings("unused")
    private Float floatValue;
    @SuppressWarnings("unused")
    private Character characterValue;
    @SuppressWarnings("unused")
    private Byte byteValue;
    @SuppressWarnings("unused")
    private Short shortValue;
    @SuppressWarnings("unused")
    private ManagedEntity entityValue;
    @SuppressWarnings("unused")
    private Enum enumValue;

    @SuppressWarnings("unused")
    private List<Date> dateValueList;
    @SuppressWarnings("unused")
    private List<Long> longValueList;
    @SuppressWarnings("unused")
    private List<Integer> integerValueList;
    @SuppressWarnings("unused")
    private List<Double> doubleValueList;
    @SuppressWarnings("unused")
    private List<String> stringValueList;

    @SuppressWarnings("unused")
    private List<Float> floatValueList;
    @SuppressWarnings("unused")
    private List<Character> characterValueList;
    @SuppressWarnings("unused")
    private List<Byte> byteValueList;
    @SuppressWarnings("unused")
    private List<Short> shortValueList;
    @SuppressWarnings("unused")
    private List<ManagedEntity> entityValueList;

    @SuppressWarnings("unused")
    private List<QueryCriteria> subCriteria = new ArrayList();

    private boolean isAnd = false;
    private boolean isOr = false;

    /**
     * Get And Sub Criteria
     * @since 1.0.0
     * @return And Sub Criteria
     */
    @SuppressWarnings("unused")
    public List<QueryCriteria> getSubCriteria()
    {
        return this.subCriteria;
    }


    /**
     * Constructor with attribute and operator
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum)
    {
        this.attribute = attribute;
        this.operator = criteriaEnum;
    }

    /**
     * Constructor with long key
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Long key
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Long value)
    {
        this.attribute = attribute;
        this.operator = criteriaEnum;
        this.longValue = value;
        this.type = QueryCriteriaType.LONG;
    }

    /**
     * Constructor with boolean key
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Boolean key
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Boolean value)
    {
        this.attribute = attribute;
        this.operator = criteriaEnum;
        this.booleanValue = value;
        this.type = QueryCriteriaType.BOOLEAN;
    }

    /**
     * Constructor for int key
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Integer Value
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Integer value)
    {
        this.attribute = attribute;
        if (value == null)
        {
            this.integerValue = NULL_INTEGER_VALUE;
        }
        else
        {
            this.integerValue = value;
        }
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.INTEGER;
    }

    /**
     * Constructor with double key
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Double key
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Double value)
    {
        this.attribute = attribute;
        this.doubleValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.DOUBLE;
    }

    /**
     * Constructor for string key
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value String key
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, String value)
    {
        this.attribute = attribute;
        this.stringValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.STRING;
    }

    /**
     * Constructor for date key
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Date Value
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Date value)
    {
        this.attribute = attribute;
        this.dateValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.DATE;
    }

    /**
     * Constructor for float key
     *
     * @since 1.2.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Float Value
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Float value)
    {
        this.attribute = attribute;
        this.floatValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.FLOAT;
    }

    /**
     * Constructor for Character key
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Character Value
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Character value)
    {
        this.attribute = attribute;
        this.characterValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.CHARACTER;
    }

    /**
     * Constructor for date key
     *
     * @since 1.2.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Byte Value
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Byte value)
    {
        this.attribute = attribute;
        this.byteValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.BYTE;
    }

    /**
     * Constructor for date key
     *
     * @since 1.2.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Short Value
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Short value)
    {
        this.attribute = attribute;
        this.shortValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.SHORT;
    }

    /**
     * Constructor for date key
     *
     * @since 1.2.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Managed Entity Value
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, ManagedEntity value)
    {
        this.attribute = attribute;
        this.entityValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.ENTITY;
    }

    /**
     * Constructor for enum key
     *
     * @since 1.2.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Enum Value
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Enum value)
    {
        this.attribute = attribute;
        this.enumValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.ENUM;
    }

    /**
     * Constructor for in clause
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.IN
     * @param valueList List of values
     */
    @SuppressWarnings("unused")
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, List<Object> valueList)
    {
        this.attribute = attribute;
        this.operator = criteriaEnum;
        if (valueList.get(0) instanceof String)
        {
            this.type = QueryCriteriaType.LIST_STRING;
            String[] values = valueList.toArray(new String[valueList.size()]);
            this.stringValueList = Arrays.asList(values);
        }
        else if (valueList.get(0) instanceof Double)
        {
            Double[] values = valueList.toArray(new Double[valueList.size()]);
            this.doubleValueList = Arrays.asList(values);

            this.type = QueryCriteriaType.LIST_DOUBLE;
        }
        else if (valueList.get(0) instanceof Date)
        {
            Date[] values = valueList.toArray(new Date[valueList.size()]);
            this.dateValueList = Arrays.asList(values);
            this.type = QueryCriteriaType.LIST_DATE;
        }
        else if (valueList.get(0) instanceof Long)
        {
            Long[] values = valueList.toArray(new Long[valueList.size()]);
            this.longValueList = Arrays.asList(values);
            this.type = QueryCriteriaType.LIST_LONG;
        }
        else if (valueList.get(0) instanceof Integer)
        {
            Integer[] values = valueList.toArray(new Integer[valueList.size()]);
            this.integerValueList = Arrays.asList(values);
            this.type = QueryCriteriaType.LIST_INTEGER;
        }
        else if (valueList.get(0) instanceof Float)
        {
            Float[] values = valueList.toArray(new Float[valueList.size()]);
            this.floatValueList = Arrays.asList(values);
            this.type = QueryCriteriaType.LIST_FLOAT;
        }
        else if (valueList.get(0) instanceof Character)
        {
            Character[] values = valueList.toArray(new Character[valueList.size()]);
            this.characterValueList = Arrays.asList(values);
            this.type = QueryCriteriaType.LIST_CHARACTER;
        }
        else if (valueList.get(0) instanceof Short)
        {
            Short[] values = valueList.toArray(new Short[valueList.size()]);
            this.shortValueList = Arrays.asList(values);
            this.type = QueryCriteriaType.LIST_SHORT;
        }
        else if (valueList.get(0) instanceof Byte)
        {
            Byte[] values = valueList.toArray(new Byte[valueList.size()]);
            this.byteValueList = Arrays.asList(values);
            this.type = QueryCriteriaType.LIST_BYTE;
        }
        else if (valueList.get(0) instanceof ManagedEntity)
        {
            ManagedEntity[] values = valueList.toArray(new ManagedEntity[valueList.size()]);
            this.entityValueList = Arrays.asList(values);
            this.type = QueryCriteriaType.LIST_ENTITY;
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
    @SuppressWarnings("unused")
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Long value)
    {
        final QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isAnd = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * And with sub-query
     *
     * @since 1.0.0
     * @param andGroup And sub query
     * @return New Query Criteria with added and sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria and(QueryCriteria andGroup)
    {
        andGroup.isAnd = true;
        this.subCriteria.add(andGroup);
        return this;
    }

    /**
     * And with int
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Int key
     * @return New Query Criteria with added and sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Integer value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isAnd = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * And with double
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Double key
     * @return New Query Criteria with added and sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Double value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isAnd = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * And with boolean
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Boolean key
     * @return New Query Criteria with added and sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Boolean value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.type = QueryCriteriaType.BOOLEAN;
        criteria.isAnd = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * And with immutable boolean
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Mutable boolean key
     * @return New Query Criteria with added and sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, boolean value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.type = QueryCriteriaType.BOOLEAN;
        criteria.isAnd = true;
        subCriteria.add(criteria);
        return this;
    }


    /**
     * And Value with String
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value String key
     * @return New Query Criteria with added and sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, String value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isAnd = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * And Value with date
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Date key
     * @return New Query Criteria with added and sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Date value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isAnd = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * And key with list of objects for in clause
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.IN
     * @param valueList list of mutable objects
     * @return New Query Criteria with added and sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, List<Object> valueList)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, valueList);
        criteria.isAnd = true;
        subCriteria.add(criteria);
        return this;
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
    @SuppressWarnings("unused")
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Long value)
    {
        final QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isOr = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * Or with sub-query
     *
     * @since 1.0.0
     * @param orGroup Or Sub Query
     * @return New Query Criteria with added or sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria or(QueryCriteria orGroup)
    {
        orGroup.isOr = true;
        this.subCriteria.add(orGroup);
        return this;
    }

    /**
     * Or with int
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Integer Value
     * @return New Query Criteria with added or sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Integer value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isOr = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * Or with double
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Double Value
     * @return New Query Criteria with added or sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Double value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isOr = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * Or with boolean - Note
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Or key
     * @return New Query Criteria with added or sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Boolean value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.type = QueryCriteriaType.BOOLEAN;
        criteria.isOr = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * Or Value with String
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value String key
     * @return New Query Criteria with added or sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, String value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isOr = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * Or Value with date
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Date Value
     * @return New Query Criteria with added or sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Date value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.isOr = true;
        subCriteria.add(criteria);
        return this;

    }

    /**
     * Or key with list of objects for in clause
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param valueList List of mutable object values
     * @return New Query Criteria with added or sub query
     */
    @SuppressWarnings("unused")
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, List<Object> valueList)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, valueList);
        criteria.isOr = true;
        subCriteria.add(criteria);
        return this;
    }

    /**
     * Indicate you would like the inverse of the QueryCriteria grouping.
     * <p>
     * Usage:
     * <p>
     * QueryCriteria firstCriteria = new QueryCriteria("age", QueryCriteriaOperator.GREATER_THAN, 18);
     * QueryCriteria secondCriteria = new QueryCriteria("canDrive", QueryCriteriaOperator.EQUAL, true);
     * <p>
     * // first.and(second).not() Criteria
     * persistenceManager.executeQuery(new Query(Person.class, first.and(second).not());
     * <p>
     * <p>
     * <p>
     * The equivalent using DSL would be:
     * <p>
     * val unqualifiedDrivers = db.query(Driver.Data)
     * .where [ !(age > 18 && canDrive == true) ]
     * .list
     *
     * @since 1.3.0 Added as enhancement #69
     */
    public QueryCriteria not() {
        this.not = true;
        return this;
    }

    //////////////////////////////////////////////////////////////////////
    //
    //  Getters
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get attribute name
     * @since 1.0.0
     * @return Attribute Name
     */
    @SuppressWarnings("unused")
    public String getAttribute()
    {
        return attribute;
    }

    /**
     * Set Attribute name
     * @since 1.0.0
     * @param attribute attribute name
     */
    @SuppressWarnings("unused")
    public void setAttribute(String attribute)
    {
        this.attribute = attribute;
    }

    /**
     * Get Operator
     * @since 1.0.0
     * @return Query operator
     */
    @SuppressWarnings("unused")
    public QueryCriteriaOperator getOperator()
    {
        return operator;
    }

    /**
     * Get Criteria type
     * @since 1.0.0
     * @return Criteria attribute Type
     */
    @SuppressWarnings("unused")
    public QueryCriteriaType getType()
    {
        return type;
    }

    /**
     * Get Query attribute key
     * @since 1.0.0
     * @return attribute key
     */
    @SuppressWarnings("unused")
    public Object getValue()
    {
        if(type == QueryCriteriaType.BOOLEAN)
        {
            return booleanValue;
        }
        else if(type == QueryCriteriaType.DATE)
        {
            return dateValue;
        }
        else if(type == QueryCriteriaType.INTEGER)
        {
            return integerValue;
        }
        else if(type == QueryCriteriaType.LONG)
        {
            return longValue;
        }
        else if(type == QueryCriteriaType.DOUBLE)
        {
            return doubleValue;
        }
        else if(type == QueryCriteriaType.STRING)
        {
            return stringValue;
        }

        else if(type == QueryCriteriaType.FLOAT)
        {
            return floatValue;
        }
        else if(type == QueryCriteriaType.CHARACTER)
        {
            return characterValue;
        }
        else if(type == QueryCriteriaType.BYTE)
        {
            return byteValue;
        }
        else if(type == QueryCriteriaType.SHORT)
        {
            return shortValue;
        }
        else if(type == QueryCriteriaType.ENTITY)
        {
            return entityValue;
        }
        else if(type == QueryCriteriaType.ENUM)
        {
            return enumValue;
        }
        else if(type == QueryCriteriaType.LIST_DATE)
        {
            return dateValueList;
        }
        else if(type == QueryCriteriaType.LIST_DOUBLE)
        {
            return doubleValueList;
        }
        else if(type == QueryCriteriaType.LIST_INTEGER)
        {
            return integerValueList;
        }
        else if(type == QueryCriteriaType.LIST_LONG)
        {
            return longValueList;
        }
        else if(type == QueryCriteriaType.LIST_STRING)
        {
            return stringValueList;
        }
        else if(type == QueryCriteriaType.LIST_FLOAT)
        {
            return floatValueList;
        }
        else if(type == QueryCriteriaType.LIST_CHARACTER)
        {
            return characterValueList;
        }
        else if(type == QueryCriteriaType.LIST_BYTE)
        {
            return byteValueList;
        }
        else if(type == QueryCriteriaType.LIST_SHORT)
        {
            return shortValueList;
        }
        else if(type == QueryCriteriaType.LIST_ENTITY)
        {
            return entityValueList;
        }

        return null;
    }

    @SuppressWarnings("unused")
    public Date getDateValue()
    {
        return dateValue;
    }

    @SuppressWarnings("unused")
    public Long getLongValue()
    {
        return longValue;
    }

    @SuppressWarnings("unused")
    public Integer getIntegerValue()
    {
        return integerValue;
    }

    @SuppressWarnings("unused")
    public Boolean getBooleanValue()
    {
        return booleanValue;
    }

    @SuppressWarnings("unused")
    public Double getDoubleValue()
    {
        return doubleValue;
    }

    @SuppressWarnings("unused")
    public String getStringValue()
    {
        return stringValue;
    }

    @SuppressWarnings("unused")
    public List<Date> getDateValueList()
    {
        return dateValueList;
    }

    @SuppressWarnings("unused")
    public List<Long> getLongValueList()
    {
        return longValueList;
    }

    @SuppressWarnings("unused")
    public List<Integer> getIntegerValueList()
    {
        return integerValueList;
    }

    @SuppressWarnings("unused")
    public List<Double> getDoubleValueList()
    {
        return doubleValueList;
    }

    @SuppressWarnings("unused")
    public List<String> getStringValueList()
    {
        return stringValueList;
    }

    @SuppressWarnings("unused")
    public void setOperator(QueryCriteriaOperator operator)
    {
        this.operator = operator;
    }

    @SuppressWarnings("unused")
    public void setType(QueryCriteriaType type)
    {
        this.type = type;
    }

    @SuppressWarnings("unused")
    public void setDateValue(Date dateValue)
    {
        this.dateValue = dateValue;
    }

    @SuppressWarnings("unused")
    public void setLongValue(Long longValue)
    {
        this.longValue = longValue;
    }

    @SuppressWarnings("unused")
    public void setIntegerValue(Integer integerValue)
    {
        this.integerValue = integerValue;
    }

    @SuppressWarnings("unused")
    public void setBooleanValue(Boolean booleanValue)
    {
        this.booleanValue = booleanValue;
    }

    @SuppressWarnings("unused")
    public void setDoubleValue(Double doubleValue)
    {
        this.doubleValue = doubleValue;
    }

    @SuppressWarnings("unused")
    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    @SuppressWarnings("unused")
    public void setDateValueList(List<Date> dateValueList)
    {
        this.dateValueList = dateValueList;
    }

    @SuppressWarnings("unused")
    public void setLongValueList(List<Long> longValueList)
    {
        this.longValueList = longValueList;
    }

    @SuppressWarnings("unused")
    public void setIntegerValueList(List<Integer> integerValueList)
    {
        this.integerValueList = integerValueList;
    }

    @SuppressWarnings("unused")
    public void setDoubleValueList(List<Double> doubleValueList)
    {
        this.doubleValueList = doubleValueList;
    }

    @SuppressWarnings("unused")
    public void setStringValueList(List<String> stringValueList)
    {
        this.stringValueList = stringValueList;
    }

    @SuppressWarnings("unused")
    public void setSubCriteria(List<QueryCriteria> andCriteria) {
        this.subCriteria = andCriteria;
    }

    @SuppressWarnings("unused")
    public Float getFloatValue() {
        return floatValue;
    }

    @SuppressWarnings("unused")
    public void setFloatValue(Float floatValue) {
        this.floatValue = floatValue;
    }

    @SuppressWarnings("unused")
    public Character getCharacterValue() {
        return characterValue;
    }

    @SuppressWarnings("unused")
    public void setCharacterValue(Character characterValue) {
        this.characterValue = characterValue;
    }

    @SuppressWarnings("unused")
    public Byte getByteValue() {
        return byteValue;
    }

    @SuppressWarnings("unused")
    public void setByteValue(Byte byteValue) {
        this.byteValue = byteValue;
    }

    @SuppressWarnings("unused")
    public Short getShortValue() {
        return shortValue;
    }

    @SuppressWarnings("unused")
    public void setShortValue(Short shortValue) {
        this.shortValue = shortValue;
    }

    @SuppressWarnings("unused")
    public ManagedEntity getEntityValue() {
        return entityValue;
    }

    @SuppressWarnings("unused")
    public void setEntityValue(ManagedEntity entityValue) {
        this.entityValue = entityValue;
    }

    @SuppressWarnings("unused")
    public List<Float> getFloatValueList() {
        return floatValueList;
    }

    @SuppressWarnings("unused")
    public void setFloatValueList(List<Float> floatValueList) {
        this.floatValueList = floatValueList;
    }

    @SuppressWarnings("unused")
    public List<Character> getCharacterValueList() {
        return characterValueList;
    }

    @SuppressWarnings("unused")
    public void setCharacterValueList(List<Character> characterValueList) {
        this.characterValueList = characterValueList;
    }

    @SuppressWarnings("unused")
    public List<Byte> getByteValueList() {
        return byteValueList;
    }

    @SuppressWarnings("unused")
    public void setByteValueList(List<Byte> byteValueList) {
        this.byteValueList = byteValueList;
    }

    @SuppressWarnings("unused")
    public List<Short> getShortValueList() {
        return shortValueList;
    }

    @SuppressWarnings("unused")
    public void setShortValueList(List<Short> shortValueList) {
        this.shortValueList = shortValueList;
    }

    @SuppressWarnings("unused")
    public List<ManagedEntity> getEntityValueList() {
        return entityValueList;
    }

    @SuppressWarnings("unused")
    public void setEntityValueList(List<ManagedEntity> entityValueList) {
        this.entityValueList = entityValueList;
    }

    @SuppressWarnings("unused")
    public Enum getEnumValue() {
        return enumValue;
    }

    @SuppressWarnings("unused")
    public void setEnumValue(Enum enumValue) {
        this.enumValue = enumValue;
    }

    @SuppressWarnings("unused")
    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(attribute);
        buffer.writeInt(operator.ordinal());
        buffer.writeInt(type.ordinal());
        buffer.writeObject(dateValue);
        buffer.writeObject(longValue);
        buffer.writeObject(integerValue);
        buffer.writeObject(booleanValue);
        buffer.writeObject(doubleValue);
        buffer.writeObject(stringValue);
        buffer.writeObject(dateValueList);
        buffer.writeObject(longValueList);
        buffer.writeObject(integerValueList);
        buffer.writeObject(doubleValueList);
        buffer.writeObject(stringValueList);
        buffer.writeObject(subCriteria);

        buffer.writeObject(enumValue);
        buffer.writeObject(floatValue);
        buffer.writeObject(characterValue);
        buffer.writeObject(byteValue);
        buffer.writeObject(shortValue);
        buffer.writeObject(entityValue);
        buffer.writeObject(floatValueList);
        buffer.writeObject(characterValueList);
        buffer.writeObject(byteValueList);
        buffer.writeObject(shortValueList);
        buffer.writeObject(entityValueList);
        buffer.writeBoolean(not);
        buffer.writeBoolean(isAnd);
        buffer.writeBoolean(isOr);

    }

    @SuppressWarnings("unused")
    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        attribute = (String)buffer.readObject();
        operator = QueryCriteriaOperator.values()[buffer.readInt()];
        type = QueryCriteriaType.values()[buffer.readInt()];
        dateValue = (Date)buffer.readObject();
        longValue = (Long)buffer.readObject();
        integerValue = (Integer)buffer.readObject();
        booleanValue = (Boolean)buffer.readObject();
        doubleValue = (Double)buffer.readObject();
        stringValue = (String)buffer.readObject();
        dateValueList = (List<Date>)buffer.readObject();
        integerValueList = (List<Integer>)buffer.readObject();
        doubleValueList = (List<Double>)buffer.readObject();
        stringValueList =(List<String>) buffer.readObject();
        subCriteria = (List<QueryCriteria>) buffer.readObject();

        enumValue = (Enum)buffer.readObject();
        floatValue = (Float)buffer.readObject();
        characterValue = (Character)buffer.readObject();
        byteValue = (Byte)buffer.readObject();
        shortValue = (Short)buffer.readObject();
        entityValue = (ManagedEntity)buffer.readObject();
        floatValueList = (List<Float>)buffer.readObject();
        characterValueList = (List<Character>)buffer.readObject();
        byteValueList = (List<Byte>)buffer.readObject();
        shortValueList = (List<Short>)buffer.readObject();
        entityValueList = (List<ManagedEntity>)buffer.readObject();
        not = buffer.readBoolean();
        isAnd = buffer.readBoolean();
        isOr = buffer.readBoolean();
    }

    @SuppressWarnings("unused")
    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    @SuppressWarnings("unused")
    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }

    /**
     * Getter for not inverse modifier
     *
     * @return Value of modifier
     * @since 1.3.0
     */
    public boolean isNot() {
        return not;
    }

    public boolean isAnd() {
        return isAnd;
    }

    public boolean isOr() {
        return isOr;
    }

    public void setNot(boolean not) {
        this.not = not;
    }

    public void setAnd(boolean and) {
        isAnd = and;
    }

    public void setOr(boolean or) {
        isOr = or;
    }

    private QueryCriteria parentCriteria;

    public QueryCriteria getParentCriteria() {
        return parentCriteria;
    }

    public void setParentCriteria(QueryCriteria parentCriteria) {
        this.parentCriteria = parentCriteria;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(attribute, getValue(), operator, subCriteria, isAnd, isOr, isNot());
    }

    @Override
    public boolean equals(Object other)
    {
        if(this == other)
            return true;

        if(other instanceof QueryCriteria)
        {
            QueryCriteria criteria = (QueryCriteria)other;

            if(criteria.isAnd != this.isAnd)
                return false;
            else if(criteria.isOr != this.isOr)
                return false;
            else if(criteria.not != this.not)
                return false;
            else if(criteria.operator != this.operator)
                return false;
            else if(!CompareUtil.forceCompare(criteria.attribute, this.attribute))
                return false;
            else if(!CompareUtil.forceCompare(criteria.getValue(), this.getValue()))
                return false;
            else if(!criteria.subCriteria.equals(this.subCriteria))
                return false;

            return true;
        }
        return false;
    }

    public boolean meetsCritieria = false;

    private transient AttributeDescriptor attributeDescriptor;

    public AttributeDescriptor getAttributeDescriptor() {
        return attributeDescriptor;
    }

    public void setAttributeDescriptor(AttributeDescriptor attributeDescriptor) {
        this.attributeDescriptor = attributeDescriptor;
    }
}
