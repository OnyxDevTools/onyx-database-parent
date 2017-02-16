package com.onyx.persistence.query;

import com.onyx.persistence.ManagedEntity;
import com.onyx.structure.serializer.ObjectBuffer;
import com.onyx.structure.serializer.ObjectSerializable;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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

    public static Boolean NULL_BOOLEAN_VALUE = null;
    public static int NULL_INTEGER_VALUE = Integer.MIN_VALUE;
    public static long NULL_LONG_VALUE = Long.MIN_VALUE;
    public static double NULL_DOUBLE_VALUE = Double.MIN_VALUE;
    public static Date NULL_DATE_VALUE = new Date(Long.MIN_VALUE);
    public static String NULL_STRING_VALUE = null;

    /**
     * Default Constructor
     */
    public QueryCriteria()
    {

    }

    /**
     * Comparison properties
     */
    private String attribute;
    private QueryCriteriaOperator operator;
    private QueryCriteriaType type;

    private Date dateValue;
    private Long longValue;
    private Integer integerValue;
    private Boolean booleanValue;
    private Double doubleValue;
    private String stringValue;

    private Float floatValue;
    private Character characterValue;
    private Byte byteValue;
    private Short shortValue;
    private ManagedEntity entityValue;
    private Enum enumValue;

    private List<Date> dateValueList;
    private List<Long> longValueList;
    private List<Integer> integerValueList;
    private List<Double> doubleValueList;
    private List<String> stringValueList;

    private List<Float> floatValueList;
    private List<Character> characterValueList;
    private List<Byte> byteValueList;
    private List<Short> shortValueList;
    private List<ManagedEntity> entityValueList;


    private List<QueryCriteria> andCriteria = new ArrayList();
    private List<QueryCriteria> orCriteria = new ArrayList();

    /**
     * Get Or Sub Criteria
     * @since 1.0.0
     * @return Or Sub Criteria
     */
    public List<QueryCriteria> getOrCriteria()
    {
        return orCriteria;
    }

    /**
     * Get And Sub Criteria
     * @since 1.0.0
     * @return And Sub Criteria
     */
    public List<QueryCriteria> getAndCriteria()
    {
        return andCriteria;
    }


    /**
     * Constructor with attribute and operator
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     */
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
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Long value)
    {
        final QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        andCriteria.add(criteria);
        return this;
    }

    /**
     * And with sub-query
     *
     * @since 1.0.0
     * @param andGroup And sub query
     * @return New Query Criteria with added and sub query
     */
    public QueryCriteria and(QueryCriteria andGroup)
    {
        this.andCriteria.add(andGroup);
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
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Integer value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        andCriteria.add(criteria);
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
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Double value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        andCriteria.add(criteria);
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
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Boolean value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.type = QueryCriteriaType.BOOLEAN;
        andCriteria.add(criteria);
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
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, boolean value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.type = QueryCriteriaType.BOOLEAN;
        andCriteria.add(criteria);
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
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, String value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        andCriteria.add(criteria);
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
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Date value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        andCriteria.add(criteria);
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
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, List<Object> valueList)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, valueList);
        andCriteria.add(criteria);
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
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Long value)
    {
        final QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        orCriteria.add(criteria);
        return this;
    }

    /**
     * Or with sub-query
     *
     * @since 1.0.0
     * @param orGroup Or Sub Query
     * @return New Query Criteria with added or sub query
     */
    public QueryCriteria or(QueryCriteria orGroup)
    {
        this.orCriteria.add(orGroup);
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
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Integer value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        orCriteria.add(criteria);
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
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Double value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        orCriteria.add(criteria);
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
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Boolean value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        criteria.type = QueryCriteriaType.BOOLEAN;
        orCriteria.add(criteria);
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
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, String value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        orCriteria.add(criteria);
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
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, Date value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        orCriteria.add(criteria);
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
    public QueryCriteria or(String attribute, QueryCriteriaOperator criteriaEnum, List<Object> valueList)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, valueList);
        orCriteria.add(criteria);
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
    public String getAttribute()
    {
        return attribute;
    }

    /**
     * Set Attribute name
     * @since 1.0.0
     * @param attribute attribute name
     */
    public void setAttribute(String attribute)
    {
        this.attribute = attribute;
    }

    /**
     * Get Operator
     * @since 1.0.0
     * @return Query operator
     */
    public QueryCriteriaOperator getOperator()
    {
        return operator;
    }

    /**
     * Get Criteria type
     * @since 1.0.0
     * @return Criteria attribute Type
     */
    public QueryCriteriaType getType()
    {
        return type;
    }

    /**
     * Get Query attribute key
     * @since 1.0.0
     * @return attribute key
     */
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

    public Date getDateValue()
    {
        return dateValue;
    }

    public Long getLongValue()
    {
        return longValue;
    }

    public Integer getIntegerValue()
    {
        return integerValue;
    }

    public Boolean getBooleanValue()
    {
        return booleanValue;
    }

    public Double getDoubleValue()
    {
        return doubleValue;
    }

    public String getStringValue()
    {
        return stringValue;
    }

    public List<Date> getDateValueList()
    {
        return dateValueList;
    }

    public List<Long> getLongValueList()
    {
        return longValueList;
    }

    public List<Integer> getIntegerValueList()
    {
        return integerValueList;
    }

    public List<Double> getDoubleValueList()
    {
        return doubleValueList;
    }

    public List<String> getStringValueList()
    {
        return stringValueList;
    }

    public void setOperator(QueryCriteriaOperator operator)
    {
        this.operator = operator;
    }

    public void setType(QueryCriteriaType type)
    {
        this.type = type;
    }

    public void setDateValue(Date dateValue)
    {
        this.dateValue = dateValue;
    }

    public void setLongValue(Long longValue)
    {
        this.longValue = longValue;
    }

    public void setIntegerValue(Integer integerValue)
    {
        this.integerValue = integerValue;
    }

    public void setBooleanValue(Boolean booleanValue)
    {
        this.booleanValue = booleanValue;
    }

    public void setDoubleValue(Double doubleValue)
    {
        this.doubleValue = doubleValue;
    }

    public void setStringValue(String stringValue)
    {
        this.stringValue = stringValue;
    }

    public void setDateValueList(List<Date> dateValueList)
    {
        this.dateValueList = dateValueList;
    }

    public void setLongValueList(List<Long> longValueList)
    {
        this.longValueList = longValueList;
    }

    public void setIntegerValueList(List<Integer> integerValueList)
    {
        this.integerValueList = integerValueList;
    }

    public void setDoubleValueList(List<Double> doubleValueList)
    {
        this.doubleValueList = doubleValueList;
    }

    public void setStringValueList(List<String> stringValueList)
    {
        this.stringValueList = stringValueList;
    }

    public void setAndCriteria(List<QueryCriteria> andCriteria)
    {
        this.andCriteria = andCriteria;
    }

    public void setOrCriteria(List<QueryCriteria> orCriteria)
    {
        this.orCriteria = orCriteria;
    }

    public Float getFloatValue() {
        return floatValue;
    }

    public void setFloatValue(Float floatValue) {
        this.floatValue = floatValue;
    }

    public Character getCharacterValue() {
        return characterValue;
    }

    public void setCharacterValue(Character characterValue) {
        this.characterValue = characterValue;
    }

    public Byte getByteValue() {
        return byteValue;
    }

    public void setByteValue(Byte byteValue) {
        this.byteValue = byteValue;
    }

    public Short getShortValue() {
        return shortValue;
    }

    public void setShortValue(Short shortValue) {
        this.shortValue = shortValue;
    }

    public ManagedEntity getEntityValue() {
        return entityValue;
    }

    public void setEntityValue(ManagedEntity entityValue) {
        this.entityValue = entityValue;
    }

    public List<Float> getFloatValueList() {
        return floatValueList;
    }

    public void setFloatValueList(List<Float> floatValueList) {
        this.floatValueList = floatValueList;
    }

    public List<Character> getCharacterValueList() {
        return characterValueList;
    }

    public void setCharacterValueList(List<Character> characterValueList) {
        this.characterValueList = characterValueList;
    }

    public List<Byte> getByteValueList() {
        return byteValueList;
    }

    public void setByteValueList(List<Byte> byteValueList) {
        this.byteValueList = byteValueList;
    }

    public List<Short> getShortValueList() {
        return shortValueList;
    }

    public void setShortValueList(List<Short> shortValueList) {
        this.shortValueList = shortValueList;
    }

    public List<ManagedEntity> getEntityValueList() {
        return entityValueList;
    }

    public void setEntityValueList(List<ManagedEntity> entityValueList) {
        this.entityValueList = entityValueList;
    }

    public Enum getEnumValue() {
        return enumValue;
    }

    public void setEnumValue(Enum enumValue) {
        this.enumValue = enumValue;
    }

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
        buffer.writeObject(andCriteria);
        buffer.writeObject(orCriteria);

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

    }

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
        andCriteria = (List<QueryCriteria>)buffer.readObject();
        orCriteria = (List<QueryCriteria>)buffer.readObject();

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
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position) throws IOException
    {
        readObject(buffer);
    }

    @Override
    public void readObject(ObjectBuffer buffer, long position, int serializerId) throws IOException {

    }

}
