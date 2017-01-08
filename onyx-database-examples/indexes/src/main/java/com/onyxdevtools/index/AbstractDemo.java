package com.onyxdevtools.index;

import java.io.File;


/**
 @author  cosborn
 */
public abstract class AbstractDemo
{
    public AbstractDemo()
    {
    }

    /**
     * Helper method to verify 2 objects have the same key.
     *
     * @param  message      Message to display if the assert fails
     * @param  comparison1  First object to compare
     * @param  comparison2  Second object to compare against
     */
    public static void assertEquals(final String message, final Object comparison1, final Object comparison2)
    {
        if (comparison1 == comparison2)
        {
            return;
        }

        if (comparison1.equals(comparison2))
        {
            return;
        }

        System.err.println(message);
    }

    /**
     * Helper method to verify an the object is not null.
     *
     * @param  message        Message displayed if the object is null
     * @param  nonNullObject  Object to assert
     */
    public static void assertNotNull(final String message, final Object nonNullObject)
    {
        if (nonNullObject == null)
        {
            System.err.println(message);
        }
    }

    /**
     * Helper method to verify an the object is null.
     *
     * @param  message        Message displayed if the object is null
     * @param  nonNullObject  Object to assert
     */
    public static void assertNull(final String message, final Object nonNullObject)
    {
        if (nonNullObject != null)
        {
            System.err.println(message);
        }
    }

    /**
     * Helper method to assert a boolean.
     *
     * @param  message   Message to show if assert fails
     * @param  operator  boolean to assert
     */
    public static void assertTrue(final String message, final boolean operator)
    {
        if (!operator)
        {
            System.err.println(message);
        }
    }

    /**
     * Delete a database so you have a clean slate prior to testing.
     *
     * @param  pathToDb  Path to onyx database
     */
    public static void deleteDatabase(final String pathToDb)
    {
        final File database = new File(pathToDb);

        if (database.exists())
        {
            delete(database);
        }

        database.delete();
    }

    /**
     * Delete files within a directory.
     *
     * @param  f  directory to delete
     */
    private static void delete(final File f)
    {
        if (f.isDirectory())
        {

            for (final File c : f.listFiles())
            {
                delete(c);
            }
        }

        f.delete();
    }

}
