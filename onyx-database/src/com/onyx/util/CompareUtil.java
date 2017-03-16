package com.onyx.util;

import com.onyx.exception.InvalidDataTypeForOperator;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 12/14/14.
 *
 * This is a utility class used to compare values against various operators
 */
public class CompareUtil
{

    /**
     * This method compares but suppresses the error if values do not match
     * @param object First object to compare
     * @param object2 second object to compare
     * @param throwError ignored, by virtue of this method being called we assume to suppress errors
     * @return Whether the values are equal
     */
    @SuppressWarnings("unused")
    public static boolean compare(Object object, Object object2, boolean throwError)
    {
        try {
            return compare(object, object2);
        } catch (InvalidDataTypeForOperator invalidDataTypeForOperator) {
            return false;
        }
    }

    /**
     * This method runs a compare but, swallows the errors.
     *
     * @param object First object
     * @param object2 Second object to compare
     * @return Whether they are equal or not
     */
    public static boolean forceCompare(Object object, Object object2)
    {
        try {
            return compare(object ,object2);
        } catch (InvalidDataTypeForOperator invalidDataTypeForOperator) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static Object castObject(Class clazz, Object object) {

        Method method = null;

        Class objectClass = object.getClass();
        if(clazz == int.class && objectClass == Integer.class)
            return object;
        else if(clazz == long.class && objectClass == Long.class)
            return object;
        else if(clazz == double.class && objectClass == Double.class)
            return object;
        else if(clazz == float.class && objectClass == Float.class)
            return object;
        else if(clazz == boolean.class && objectClass == Boolean.class)
            return object;
        else if(clazz == char.class && objectClass == Character.class)
            return object;
        else if(clazz == byte.class && objectClass == Byte.class)
            return object;
        else if(clazz == short.class && objectClass == Short.class)
            return object;
        else if(clazz == int.class && objectClass == Long.class)
            return ((Long)object).intValue();
        else if(clazz == long.class && objectClass == Integer.class)
            return ((Integer)object).longValue();

        try {
            if (clazz == Integer.class ||  clazz == int.class)
                method = objectClass.getMethod("intValue");
            else if (clazz == Long.class ||  clazz == long.class)
                method = objectClass.getMethod("longValue");
            else if (clazz == Short.class ||  clazz == short.class)
                method = objectClass.getMethod("shortValue");
            else if (clazz == Byte.class ||  clazz == byte.class)
                method = objectClass.getMethod("byteValue");
            else if (clazz == Boolean.class || clazz == boolean.class)
                method = objectClass.getMethod("booleanValue");
            else if (clazz == Float.class  || clazz == int.class)
                method = objectClass.getMethod("floatValue");
            else if (clazz == Double.class || clazz == double.class)
                method = objectClass.getMethod("doubleValue");
            else if (clazz == Character.class || clazz == char.class)
                method = objectClass.getMethod("toChar");
            else if (clazz == String.class)
                method = objectClass.getMethod("toString");

            if (method == null)
                return object;

            return method.invoke(object);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e)
        {
            return object;
        }
    }

    /**
     * Compare 2 objects, they can be any data type supported by the database
     *
     * @param object First object to compare
     * @param object2 second object to compare
     * @return Whether the values are equal
     */
    @SuppressWarnings({"unchecked", "WeakerAccess"})
    public static boolean compare(Object object, Object object2) throws InvalidDataTypeForOperator
    {

        if(object == object2)
            return true;

        // If the objects do not match, cast it to the correct object
        if(object2 != null
                && object != null
                && !object2.getClass().isAssignableFrom(object.getClass()))
            object2 = castObject(object.getClass(), object2);

        // This was added because string.equals is much more efficient than using comparable.
        // Comparable iterated through the entire character array whereas .equals does not.
        if(object instanceof String &&
                object2 instanceof String)
            return object.equals(object2);

        else if(object instanceof Comparable
                && object2 instanceof Comparable)
            return ((Comparable) object).compareTo(object2) == 0;

        // Null checkers
        else if(object instanceof String && object == QueryCriteria.NULL_STRING_VALUE)
            return object2 == null;
        else if(object instanceof Double && (Double)object == QueryCriteria.NULL_DOUBLE_VALUE)
            return object2 == null;
        else if(object instanceof Long && (Long)object == QueryCriteria.NULL_LONG_VALUE)
            return object2 == null;
        else if(object instanceof Integer && (Integer)object == QueryCriteria.NULL_INTEGER_VALUE)
            return object2 == null;
        else if(object instanceof Boolean && object == QueryCriteria.NULL_BOOLEAN_VALUE && (object2 instanceof Boolean || object2 == null))
            return object2 == null;
        else if(object instanceof Date && object == QueryCriteria.NULL_DATE_VALUE)
            return object2 == null;
        else if(object2 == null && object instanceof Date && ((Date)object).getTime() == QueryCriteria.NULL_DATE_VALUE.getTime())
            return true;
        else if(object == null && object2 != null)
            return false;
        else if(object != null && object2 == null)
            return false;
        else if(object != null && object2 != null)
            return object.equals(object2);

        // Comparison operator was not found, we should throw an exception because the data types are not supported
        throw new InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR);
    }

    /**
     * Compare without throwing exception
     *
     * @param object First object to compare
     * @param object2 Second object to compare
     * @param operator Operator to compare against
     *
     * @return If the critieria meet return true
     */
    public static boolean forceCompare(Object object, Object object2, QueryCriteriaOperator operator)
    {
        try {
            return compare(object, object2, operator);
        } catch (InvalidDataTypeForOperator invalidDataTypeForOperator) {
            return false;
        }
    }

    /**
     * Generic method use to compare values with a given operator
     *
     * @param object First object to compare
     * @param object2 second object to compare
     * @param operator Any QueryCriteriaOperator
     * @return whether the objects meet the criteria of the operator
     * @throws InvalidDataTypeForOperator If the values cannot be compared
     */
    @SuppressWarnings("unchecked")
    public static boolean compare(Object object, Object object2, QueryCriteriaOperator operator) throws InvalidDataTypeForOperator
    {

        // If the objects do not match, cast it to the correct object
        if(object2 != null
                && object != null
                && !object2.getClass().isAssignableFrom(object.getClass()))
            object2 = castObject(object.getClass(), object2);

        if(operator == QueryCriteriaOperator.NOT_NULL)
            return (object2 != null);

        // Equal - this should take a generic object key
        else if(operator == QueryCriteriaOperator.EQUAL)
            return compare(object, object2);

        // Not equal - this should take a generic object key
        else if(operator == QueryCriteriaOperator.NOT_EQUAL)
            return !compare(object, object2);

        // In, the first parameter must be a list of items if using the list key
        else if(operator == QueryCriteriaOperator.IN && object instanceof List)
        {
            List values = (List)object;
            for(Object value : values)
            {
                if(compare(value, object2))
                {
                    return true;
                }
            }
            return false;
        }

        // Not in, the first parameter must be a list of items if using the list key
        else if(operator == QueryCriteriaOperator.NOT_IN && object instanceof List)
        {
            List values = (List)object;
            for(Object value : values)
            {
                if(compare(value, object2))
                {
                    return false;
                }
            }
            return true;
        }

        // Contains , only valid for strings
        else if(operator == QueryCriteriaOperator.CONTAINS && object instanceof String && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return true;
            }
            else if(object2 == null)
            {
                return false;
            }
            return ((String) object2).contains((String) object);
        }

        // Like, only valid for strings
        else if(operator == QueryCriteriaOperator.LIKE && object instanceof String && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return true;
            }
            else if(object2 == null)
            {
                return false;
            }
            return ((String) object2).equalsIgnoreCase((String) object);
        }

        // Starts with, only valid for strings
        else if(operator == QueryCriteriaOperator.STARTS_WITH && (object instanceof String || object == null) && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return true;
            }
            else if(object2 == null)
            {
                return false;
            }
            else if(object == null)
            {
                return false;
            }
            return ((String) object2).startsWith((String) object);
        }

        else if(operator == QueryCriteriaOperator.NOT_STARTS_WITH && (object instanceof String || object == null) && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return false;
            }
            else if(object2 == null)
            {
                return true;
            }
            else if(object == null)
            {
                return true;
            }
            return !((String) object2).startsWith((String) object);
        }

        // Not in, the first parameter must be a list of items if using the list key
        else if(operator == QueryCriteriaOperator.STARTS_WITH)
        {
            List values = (List)object;
            boolean startsWith = false;

            for(Object value : values)
            {
                if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                else if(object == null)
                {
                    return false;
                }

                if (((String) object2).startsWith((String) value))
                {
                    startsWith = true;
                }

            }
            return startsWith;
        }

        else if(operator == QueryCriteriaOperator.NOT_STARTS_WITH)
        {
            List values = (List)object;
            boolean startsWith = false;

            for(Object value : values)
            {
                if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
                {
                    return false;
                }
                else if(object2 == null)
                {
                    return true;
                }
                else if(object == null)
                {
                    return true;
                }

                if (((String) object2).startsWith((String) value))
                {
                    startsWith = false;
                }

            }
            return startsWith;
        }

        // Matches, Use a regex, only valid with strings
        else if(operator == QueryCriteriaOperator.MATCHES
                && object instanceof String && (object2 instanceof String || object2 == null))
        {
            if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
            {
                return true;
            }
            else if(object2 == null)
            {
                return false;
            }
            return ((String) object2).matches((String)object);
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.GREATER_THAN)
        {
            if(object == null && object2 == null)
            {
                return false;
            }

            if(object2 == null)
            {
                return false;
            }
            else if(object == null)
            {
                return true;
            }

            else if(object instanceof Comparable
                    && object2 instanceof Comparable)
            {
                return ((Comparable) object2).compareTo(object) > 0;
            }

            return false;
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.LESS_THAN)
        {
            if(object == null && object2 == null)
            {
                return false;
            }

            if(object2 == null)
            {
                return true;
            }
            else if(object == null)
            {
                return false;
            }

            else if(object instanceof Comparable
                    && object2 instanceof Comparable)
            {
                return ((Comparable) object).compareTo(object2) > 0;
            }

            return false;
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.LESS_THAN_EQUAL)
        {
            if(object == null && object2 == null)
            {
                return true;
            }

            if(object2 == null)
            {
                return false;
            }
            else if(object == null)
            {
                return true;
            }

            else if(object instanceof Comparable
                    && object2 instanceof Comparable)
            {
                return ((Comparable) object).compareTo(object2) >= 0;
            }

            return false;
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.GREATER_THAN_EQUAL)
        {
            if(object == null && object2 == null)
            {
                return true;
            }

            if(object2 == null)
            {
                return false;
            }
            else if(object == null)
            {
                return true;
            }

            else if(object instanceof Comparable
                    && object2 instanceof Comparable)
            {
                return ((Comparable) object2).compareTo(object) >= 0;
            }

            return false;
        }

        // Comparison operator was not found, we should throw an exception because the data types are not supported
        throw new InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR);
    }

}
