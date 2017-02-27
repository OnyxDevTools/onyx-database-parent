package com.onyxdevtools.example.querying;

import java.io.File;

abstract class AbstractDemo {

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
