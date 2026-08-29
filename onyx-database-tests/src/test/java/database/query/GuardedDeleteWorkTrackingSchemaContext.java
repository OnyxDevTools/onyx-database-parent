package database.query;

import com.onyx.persistence.context.GuardedDeleteWork;
import com.onyx.persistence.context.GuardedDeleteWorkObserver;
import com.onyx.persistence.context.QueryExecutionEvent;
import com.onyx.persistence.context.QueryExecutionObserver;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only bridge for exact posting-driven guarded-delete work diagnostics. */
final class GuardedDeleteWorkTrackingSchemaContext extends DefaultSchemaContext
        implements GuardedDeleteWorkObserver, QueryExecutionObserver {
    private final List<Snapshot> work = new ArrayList<>();
    private final AtomicInteger fullTableScans = new AtomicInteger();
    private volatile boolean trackingEnabled;

    GuardedDeleteWorkTrackingSchemaContext(String contextId, String location) {
        super(contextId, location);
    }

    @Override
    public synchronized void onGuardedDeleteWork(GuardedDeleteWork value) {
        if (trackingEnabled) {
            work.add(new Snapshot(value));
        }
    }

    @Override
    public void onQueryExecution(QueryExecutionEvent event) {
        if (trackingEnabled && event == QueryExecutionEvent.FULL_TABLE_SCAN) {
            fullTableScans.incrementAndGet();
        }
    }

    synchronized List<Snapshot> getGuardedDeleteWork() {
        return new ArrayList<>(work);
    }

    int getFullTableScans() {
        return fullTableScans.get();
    }

    synchronized void resetGuardedDeleteWork() {
        work.clear();
        fullTableScans.set(0);
        trackingEnabled = true;
    }

    static final class Snapshot {
        private final int pageLimit;
        private final int eligibleIndexCount;
        private final int cardinalityProbePostingVisits;
        private final int postingVisits;
        private final int recordLookups;
        private final int matchedReferenceCount;
        private final int deletedCount;
        private final String drivingAttribute;

        private Snapshot(GuardedDeleteWork work) {
            pageLimit = work.getPageLimit();
            eligibleIndexCount = work.getEligibleIndexCount();
            cardinalityProbePostingVisits = work.getCardinalityProbePostingVisits();
            postingVisits = work.getPostingVisits();
            recordLookups = work.getRecordLookups();
            matchedReferenceCount = work.getMatchedReferenceCount();
            deletedCount = work.getDeletedCount();
            drivingAttribute = work.getDrivingAttribute();
        }

        int getPageLimit() { return pageLimit; }
        int getEligibleIndexCount() { return eligibleIndexCount; }
        int getCardinalityProbePostingVisits() { return cardinalityProbePostingVisits; }
        int getPostingVisits() { return postingVisits; }
        int getRecordLookups() { return recordLookups; }
        int getMatchedReferenceCount() { return matchedReferenceCount; }
        int getDeletedCount() { return deletedCount; }
        String getDrivingAttribute() { return drivingAttribute; }
    }
}
