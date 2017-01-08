package com.onyx.util;

import com.onyx.exception.InvalidDataTypeForOperator;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;

import java.util.Date;
import java.util.List;

/**
 * Created by timothy.osborn on 12/14/14.
 */
public class CompareUtil
{

    public static boolean compare(Object object, Object object2, boolean throwError)
    {
        try {
            return compare(object, object2);
        } catch (InvalidDataTypeForOperator invalidDataTypeForOperator) {
            return false;
        }
    }

    /**
     * Compare 2 objects, they can be any data type supported by the database
     *
     * @param object
     * @param object2
     * @return
     */
    public static boolean compare(Object object, Object object2) throws InvalidDataTypeForOperator
    {
        if(object == null && object2 == null)
        {
            return true;
        }
        // Null checkers
        else if(object instanceof String && object == QueryCriteria.NULL_STRING_VALUE)
        {
            if(object2 == null)
            {
                return true;
            }
            return false;
        }
        else if(object instanceof Double && (Double)object == QueryCriteria.NULL_DOUBLE_VALUE)
        {
            if(object2 == null)
            {
                return true;
            }
            return false;
        }
        else if(object instanceof Long && (Long)object == QueryCriteria.NULL_LONG_VALUE)
        {
            if(object2 == null)
            {
                return true;
            }
            return false;
        }
        else if(object instanceof Integer && (Integer)object == QueryCriteria.NULL_INTEGER_VALUE)
        {
            if(object2 == null)
            {
                return true;
            }
            return false;
        }
        else if(object instanceof Boolean && object == QueryCriteria.NULL_BOOLEAN_VALUE && (object2 instanceof Boolean || object2 == null))
        {
            if(object2 == null)
            {
                return true;
            }
            return false;
        }


        else if(object instanceof Date && (Date)object == QueryCriteria.NULL_DATE_VALUE)
        {
            if(object2 == null)
            {
                return true;
            }
            return false;
        }
        else if(object2 == null &&  object instanceof Date && ((Date)object).getTime() == QueryCriteria.NULL_DATE_VALUE.getTime())
            return true;
        else if(object == null && object2 != null)
        {
            return false;
        }
        else if(object != null && object2 == null)
        {
            return false;
        }
        else if(object.getClass() == String.class)
        {
            String val1 = (String) object;
            String val2 = (String) object2;
            return val1.equals(val2);
        }
        else if(object.getClass() == int.class || object.getClass() == Integer.class)
        {
            int val1 = (int) object;
            int val2 = 0;
            if(object2 instanceof Long)
                val2 = ((Long)object2).intValue();
            else
                val2 = (int) object2;
            return val1 == val2;
        }
        else if(object.getClass() == long.class || object.getClass() == Long.class)
        {
            long val1 = (long) object;
            long val2 = 0;

            if(object2 instanceof Integer)
                val2 = ((Integer)object2).longValue();
            else
                val2 = (long) object2;
            return val1 == val2;
        }
        else if(object.getClass() == Date.class)
        {
            Date val1 = (Date) object;
            Date val2 = (Date) object2;
            return val1.getTime() == val2.getTime();
        }
        else if(object.getClass() == double.class || object.getClass() == Double.class)
        {
            double val1 = (double) object;
            double val2 = (double) object2;
            return val1 == val2;
        }
        else if(object.getClass() == boolean.class || object.getClass() == Boolean.class)
        {
            boolean val1 = (boolean) object;
            boolean val2 = (boolean) object2;
            return val1 == val2;
        }
        else if(object.getClass() == Date.class || object.getClass() == Date.class)
        {
            Date val1 = (Date) object;
            Date val2 = (Date) object2;
            return val1.getTime() == val2.getTime();
        }
        else if(object != null && object2 != null)
        {
            return object.equals(object2);
        }

        // Comparison operator was not found, we should throw an exception because the data types are not supported
        throw new InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR);
    }

    /**
     * Generic method use to compare values with a given operator
     *
     * @param object
     * @param object2
     * @param operator
     * @return
     * @throws InvalidDataTypeForOperator
     */
    public static boolean compare(Object object, Object object2, QueryCriteriaOperator operator) throws InvalidDataTypeForOperator
    {

        if(operator == QueryCriteriaOperator.NOT_NULL)
        {
            return (object2 != null);
        }

        // Equal - this should take a generic object key
        if(operator == QueryCriteriaOperator.EQUAL)
        {
            return compare(object, object2);
        }

        // Not equal - this should take a generic object key
        else if(operator == QueryCriteriaOperator.NOT_EQUAL)
        {
            return !compare(object, object2);
        }

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
        else if(operator == QueryCriteriaOperator.STARTS_WITH && object instanceof List)
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

        else if(operator == QueryCriteriaOperator.NOT_STARTS_WITH && object instanceof List)
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
        else if(operator == QueryCriteriaOperator.MATCHES && object instanceof String && (object2 instanceof String || object2 == null))
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


            if(object instanceof String)
            {
                return (((String) object2).compareTo((String)object)) > 0;
            }
            else if(object instanceof Long)
            {
                return (((Long) object2) > ((Long)object));
            }
            else if(object instanceof Integer)
            {
                return (((Integer) object2) > ((Integer)object));
            }
            else if(object instanceof Double)
            {
                return (((Double) object2) > ((Double)object));
            }
            else if(object.getClass() == int.class)
            {
                return (((int) object2) > ((int)object));
            }
            else if(object.getClass() == long.class)
            {
                return (((long) object2) > ((long)object));
            }
            else if(object.getClass() == double.class)
            {
                return (((double) object2) > ((double)object));
            }
            else if(object.getClass() == Date.class)
            {
                if(object2 == null)
                {
                    return false;
                }
                return (((Date) object2).getTime() > ((Date) object).getTime());
            }

            else if(object.getClass() == Boolean.class || object.getClass() == boolean.class)
            {
                if(object2 == null && (Boolean)object == QueryCriteria.NULL_BOOLEAN_VALUE)
                {
                    return false;
                }
                else if(object2 == null)
                {
                    return false;
                }
                else if((boolean) object2 == false && (boolean) object == true)
                {
                    return false;
                }
                else if((boolean) object == (boolean) object2)
                {
                    return false;
                }

                return true;
            }
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

            if(object instanceof String)
            {
                return (((String) object2).compareTo((String)object)) < 0;
            }
            else if(object instanceof Long)
            {
                return (((Long) object2) < ((Long)object));
            }
            else if(object instanceof Integer)
            {
                return (((Integer) object2) < ((Integer)object));
            }
            else if(object instanceof Double)
            {
                return (((Double) object2) < ((Double)object));
            }
            else if(object.getClass() == int.class)
            {
                return (((int) object2) < ((int)object));
            }
            else if(object.getClass() == long.class)
            {
                return (((long) object2) < ((long)object));
            }
            else if(object.getClass() == double.class)
            {
                return (((double) object2) < ((double)object));
            }
            else if(object.getClass() == Date.class)
            {
                if(object2 == null)
                {
                    return false;
                }
                return (((Date) object2).getTime() < ((Date) object).getTime());
            }
            else if(object.getClass() == Boolean.class || object.getClass() == boolean.class)
            {
                if(object2 == null && (Boolean)object == QueryCriteria.NULL_BOOLEAN_VALUE)
                {
                    return false;
                }
                if(object2 == null && (Boolean)object == null)
                {
                    return false;
                }
                else if(object2 == null)
                {
                    return true;
                }
                else if((boolean) object2 == false && (boolean) object == true)
                {
                    return true;
                }
                else if((boolean) object == (boolean) object2)
                {
                    return false;
                }

                return false;
            }
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.LESS_THAN_EQUAL)
        {
            if(object instanceof String)
            {
                if(object2 == null && (String)object == QueryCriteria.NULL_STRING_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((String) object2).compareTo((String)object)) <= 0;
            }
            else if(object instanceof Long)
            {
                if(object2 == null && (Long)object == QueryCriteria.NULL_LONG_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((Long) object2) <= ((Long)object));
            }
            else if(object instanceof Integer)
            {
                if(object2 == null && (Integer)object == QueryCriteria.NULL_INTEGER_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((Integer) object2) <= ((Integer)object));
            }
            else if(object instanceof Double)
            {
                if(object2 == null && (Double)object == QueryCriteria.NULL_DOUBLE_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((Double) object2) <= ((Double)object));
            }
            else if(object.getClass() == int.class)
            {
                return (((int) object2) <= ((int)object));
            }
            else if(object.getClass() == long.class)
            {
                return (((long) object2) <= ((long)object));
            }
            else if(object.getClass() == double.class)
            {
                return (((double) object2) <= ((double)object));
            }
            else if(object.getClass() == Date.class)
            {
                if(object2 == null && object2 == QueryCriteria.NULL_DATE_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((Date) object2).getTime() <= ((Date) object).getTime());
            }
            else if(object.getClass() == Boolean.class)
            {
                if(object2 == null && (Boolean)object == QueryCriteria.NULL_BOOLEAN_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return true;
                }
                else if((Boolean) object2 == false && (Boolean) object == true)
                {
                    return true;
                }
                else if((Boolean) object == (Boolean) object2)
                {
                    return true;
                }

                return false;
            }
        }

        // Greater than, valid for strings, Long, long, Integer, int, Double, double
        else if(operator == QueryCriteriaOperator.GREATER_THAN_EQUAL)
        {
            if(object instanceof String)
            {
                if(object2 == null && object == QueryCriteria.NULL_STRING_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((String) object2).compareTo((String)object)) >= 0;
            }
            else if(object instanceof Long)
            {
                if(object2 == null && (Long)object == QueryCriteria.NULL_LONG_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }

                return (((Long) object2) >= ((Long)object));
            }
            else if(object instanceof Integer)
            {
                if(object2 == null && (Integer)object == QueryCriteria.NULL_INTEGER_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((Integer) object2) >= ((Integer)object));
            }
            else if(object instanceof Double)
            {
                if(object2 == null && (Double)object == QueryCriteria.NULL_DOUBLE_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((Double) object2) >= ((Double)object));
            }
            else if(object.getClass() == int.class)
            {
                return (((int) object2) >= ((int)object));
            }
            else if(object.getClass() == long.class)
            {
                return (((long) object2) >= ((long)object));
            }
            else if(object.getClass() == double.class)
            {
                return (((double) object2) >= ((double)object));
            }
            else if(object.getClass() == Date.class)
            {
                if(object2 == null && object2 == QueryCriteria.NULL_DATE_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                return (((Date) object2).getTime() >= ((Date) object).getTime());
            }
            else if(object.getClass() == Boolean.class)
            {
                if(object2 == null && (Boolean)object == QueryCriteria.NULL_BOOLEAN_VALUE)
                {
                    return true;
                }
                else if(object2 == null)
                {
                    return false;
                }
                else if((Boolean) object2 == false && (Boolean) object == true)
                {
                    return false;
                }
                else if((Boolean) object ==  (Boolean) object2)
                {
                    return true;
                }

                return false;
            }
        }

        // Comparison operator was not found, we should throw an exception because the data types are not supported
        throw new InvalidDataTypeForOperator(InvalidDataTypeForOperator.INVALID_DATA_TYPE_FOR_OPERATOR);
    }

}
