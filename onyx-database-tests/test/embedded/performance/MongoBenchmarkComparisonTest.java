package embedded.performance;

import category.EmbeddedDatabaseTests;
import embedded.performance.adapters.MongoBenchmarkAdapter;
import embedded.performance.framework.BenchmarkComparisonTest;
import embedded.performance.framework.IBenchmarkAdapter;
import org.junit.experimental.categories.Category;

/**
 * Created by cosbor11 on 1/6/2015.
 */
@Category({ EmbeddedDatabaseTests.class })
public class MongoBenchmarkComparisonTest extends BenchmarkComparisonTest
{

    public IBenchmarkAdapter adapter = new MongoBenchmarkAdapter();

    @Override
    public IBenchmarkAdapter getAdapter() {
        return adapter;
    }

}

