package com.onyxdevtools.relationship;

import java.io.File;

abstract class AbstractDemo {
    /**
     * Helper method to verify an the object is not null.
     *
     * @param message Message displayed if the object is null
     * @param nonNullObject Object to assert
     */
    static void assertNotNull(String message, Object nonNullObject)
    {
        if(nonNullObject == null)
        {
            System.err.println(message);
        }
    }

    /**
     * Helper method to verify 2 objects have the same key
     *
     * @param message Message to display if the assert fails
     * @param comparison1 First object to compare
     * @param comparison2 Second object to compare against
     */
    static void assertEquals(String message, Object comparison1, Object comparison2)
    {
        if(comparison1 == comparison2)
        {
            return;
        }
        if(comparison1.equals(comparison2))
        {
            return;
        }

        System.err.println(message);
    }

    /**
     * Helper method to assert a boolean
     * @param message Message to show if assert fails
     * @param operator boolean to assert
     */
    static void assertTrue(String message, boolean operator)
    {
        if(!operator)
        {
            System.err.println(message);
        }
    }

    /**
     * Delete a database so you have a clean slate prior to testing
     *
     * @param pathToDb Path to onyx database
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    static void deleteDatabase(String pathToDb)
    {
        File database = new File(pathToDb);
        if (database != null && database.exists()) {
            delete(database);
        }
        database.delete();
    }

    /**
     * Delete files within a directory
     * @param f directory to delete
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void delete(File f) {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        f.delete();
    }

}
