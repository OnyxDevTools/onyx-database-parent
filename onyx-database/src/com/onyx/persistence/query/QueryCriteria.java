package com.onyx.persistence.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.onyx.map.serializer.ObjectBuffer;
import com.onyx.map.serializer.ObjectSerializable;

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
    protected String attribute;
    protected QueryCriteriaOperator operator;
    protected QueryCriteriaType type;

    protected Date dateValue;
    protected Long longValue;
    protected Integer integerValue;
    protected Boolean booleanValue;
    protected Double doubleValue;
    protected String stringValue;

    protected List<Date> dateValueList;
    protected List<Long> longValueList;
    protected List<Integer> integerValueList;
    protected List<Double> doubleValueList;
    protected List<String> stringValueList;

    protected List<QueryCriteria> andCriteria = new ArrayList<QueryCriteria>();
    protected List<QueryCriteria> orCriteria = new ArrayList<QueryCriteria>();

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
     * Constructor with long value
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Long value
     */
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Long value)
    {
        this.attribute = attribute;
        this.operator = criteriaEnum;
        this.longValue = value;
        this.type = QueryCriteriaType.LONG;
    }

    /**
     * Constructor with boolean value
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Boolean value
     */
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Boolean value)
    {
        this.attribute = attribute;
        this.operator = criteriaEnum;
        this.booleanValue = value;
        this.type = QueryCriteriaType.BOOLEAN;
    }

    /**
     * Constructor for int value
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
            this.integerValue = Integer.valueOf(NULL_INTEGER_VALUE);
        }
        else
        {
            this.integerValue = Integer.valueOf(value);
        }
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.INTEGER;
    }

    /**
     * Constructor with double value
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Double value
     */
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, Double value)
    {
        this.attribute = attribute;
        this.doubleValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.DOUBLE;
    }

    /**
     * Constructor for string value
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value String value
     */
    public QueryCriteria(String attribute, QueryCriteriaOperator criteriaEnum, String value)
    {
        this.attribute = attribute;
        this.stringValue = value;
        this.operator = criteriaEnum;
        this.type = QueryCriteriaType.STRING;
    }

    /**
     * Constructor for date value
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
    }

    /**
     * And sub criteria with long value
     *
     * @since 1.0.0
     * @param attribute Attribute
     * @param criteriaEnum Criteria Operator e.x QueryCriteriaOperator.EQUAL
     * @param value Long value
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
     * @param value Int value
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
     * @param value Double value
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
     * @param value Boolean value
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
     * @param value Mutable boolean value
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
     * @param value String value
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
     * @param value Date value
     * @return New Query Criteria with added and sub query
     */
    public QueryCriteria and(String attribute, QueryCriteriaOperator criteriaEnum, Date value)
    {
        QueryCriteria criteria = new QueryCriteria(attribute, criteriaEnum, value);
        andCriteria.add(criteria);
        return this;
    }

    /**
     * And value with list of objects for in clause
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
     * @param value long value
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
     * @param value Or value
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
     * @param value String value
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
     * Or value with list of objects for in clause
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
     * Get Query attribute value
     * @since 1.0.0
     * @return attribute value
     */
    @JsonIgnore
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


    @Override
    public void writeObject(ObjectBuffer buffer) throws IOException
    {
        buffer.writeObject(attribute);
        buffer.writeInt(operator.ordinal());
        buffer.writeInt(type.ordinal());
        buffer.writeDate(dateValue);
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
    }

    @Override
    public void readObject(ObjectBuffer buffer) throws IOException
    {
        attribute = (String)buffer.readObject();
        operator = QueryCriteriaOperator.values()[buffer.readInt()];
        type = QueryCriteriaType.values()[buffer.readInt()];
        dateValue = buffer.readDate();
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
