package com.onyxdevtools.partition;

import java.io.File;

abstract class AbstractDemo {

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
        if (database.exists()) {
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
            //noinspection ConstantConditions
            for (File c : f.listFiles())
                delete(c);
        }
        f.delete();
    }

}
