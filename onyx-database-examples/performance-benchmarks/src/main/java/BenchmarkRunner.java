import com.onyxdevtools.benchmark.base.BenchmarkTest;
import com.onyxdevtools.provider.DatabaseProvider;
import com.onyxdevtools.provider.PersistenceProviderFactory;
import com.onyxdevtools.provider.manager.ProviderPersistenceManager;

import java.io.File;
import java.lang.reflect.Constructor;


/**
 * Created by tosborn1 on 8/26/16.
 *
 * This class is the main class for executing a benchmark test.  It requires 2 parameters.  First is the number of the database provider.
 *
 * Values are in DatabaseProvider:
 *
 *     0 -- H2("h2") ,
 *     1 -- ONYX("onyx"),
 *     2 -- HSQL("hsqldb"),
 *     3 -- DERBY("derby");
 *
 * The second parameter is the benchmark class:
 *
 *     DeleteBenchmarkTest
 *     InsertionBenchmarkTest
 *     InsertionSingleThreadBenchmarkTest
 *     RandomTransactionBenchmarkTest
 *     UpdateBenchmarkTest
 *
 *  Example Usage is:
 *
 *  java -jar performance-benchmark-1.0.jar 0 RandomTransactionBenchmarkTest
 *
 */
public class BenchmarkRunner {

    /**
     * Main Class
     * @param args Main arguments
     * @throws Exception Generic Exception
     */
    @SuppressWarnings("unchecked")
    public static void main(String args[]) throws Exception {

        //Default values to run via the IDE
        /*args = new String[2];
        args[0] = "1";
        args[1] = "RandomTransactionBenchmarkTest";*/

        // Delete the existing database so we start with a clean slate
        deleteDirectory(new File(DatabaseProvider.DATABASE_LOCATION));

        // Default Provider properties
        DatabaseProvider databaseProvider;
        BenchmarkTest benchmarkBenchmarkTest = null;

        // If the arguments exist through command line use them
        if (args.length > 1) {
            int databaseProviderIndex = Integer.valueOf(args[0]);
            String test = args[1];
            Class testClass = Class.forName("com.onyxdevtools.benchmark." + test);
            Constructor<?> constructor = testClass.getConstructor(ProviderPersistenceManager.class);
            databaseProvider = DatabaseProvider.values()[databaseProviderIndex];
            benchmarkBenchmarkTest = (BenchmarkTest) constructor.newInstance(PersistenceProviderFactory.getPersistenceManager(databaseProvider));
        }

        runTest(benchmarkBenchmarkTest);

        System.exit(0);
    }

    /**
     * Helper method for deleting the database directory
     * @param path Directory path of database
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    static private void deleteDirectory(File path) {
        if (path.exists()) {
            for (File f : path.listFiles()) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                }
                f.delete();
            }
            path.delete();
        }
    }

    /**
     * Execute a test and go through the benchmark test workflow
     * @param benchmarkTest instance of benchmark test
     */
    private static void runTest(BenchmarkTest benchmarkTest) {
        benchmarkTest.before();
        benchmarkTest.markBeginingOfTest();
        benchmarkTest.execute(benchmarkTest.getNumberOfExecutions());
        benchmarkTest.markEndOfTest();
        benchmarkTest.after();
        benchmarkTest.cleanup();
    }

}
