package database.query;

import com.onyx.persistence.context.HnswSearchWork;
import com.onyx.persistence.context.HnswSearchWorkObserver;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only bridge for native HNSW hard-work diagnostics. */
final class HnswWorkTrackingSchemaContext extends DefaultSchemaContext
        implements HnswSearchWorkObserver {
    private volatile boolean captured;
    private volatile int efSearch;
    private volatile int maxCandidates;
    private volatile int distanceEvaluations;
    private volatile int upperLayerDistanceEvaluations;
    private volatile int resultCount;
    private volatile boolean exactFilteredScan;
    private final AtomicInteger maxConcurrentSearchesObserved = new AtomicInteger();

    HnswWorkTrackingSchemaContext(String contextId, String location) {
        super(contextId, location);
    }

    @Override
    public void onHnswSearchWork(HnswSearchWork work) {
        efSearch = work.getEfSearch();
        maxCandidates = work.getMaxCandidates();
        distanceEvaluations = work.getDistanceEvaluations();
        upperLayerDistanceEvaluations = work.getUpperLayerDistanceEvaluations();
        resultCount = work.getResultCount();
        exactFilteredScan = work.getExactFilteredScan();
        maxConcurrentSearchesObserved.accumulateAndGet(
                work.getConcurrentSearchesObserved(), Math::max);
        captured = true;
    }

    boolean isCaptured() { return captured; }
    int getEfSearch() { return efSearch; }
    int getMaxCandidates() { return maxCandidates; }
    int getDistanceEvaluations() { return distanceEvaluations; }
    int getUpperLayerDistanceEvaluations() { return upperLayerDistanceEvaluations; }
    int getResultCount() { return resultCount; }
    boolean isExactFilteredScan() { return exactFilteredScan; }
    int getMaxConcurrentSearchesObserved() { return maxConcurrentSearchesObserved.get(); }
    void resetHnswWork() {
        captured = false;
        maxConcurrentSearchesObserved.set(0);
    }
}
