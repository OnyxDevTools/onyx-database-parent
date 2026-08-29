package database.query;

import com.onyx.persistence.context.QueryExecutionEvent;
import com.onyx.persistence.context.QueryExecutionObserver;
import com.onyx.persistence.context.impl.DefaultSchemaContext;

import java.util.concurrent.atomic.AtomicInteger;

/** Test-only bridge to Kotlin-internal physical-query diagnostics. */
final class VectorScannerTrackingSchemaContext extends DefaultSchemaContext implements QueryExecutionObserver {
    private volatile boolean trackingEnabled;
    private final AtomicInteger vectorScannerIndexLookups = new AtomicInteger();
    private final AtomicInteger vectorScannerScans = new AtomicInteger();
    private final AtomicInteger vectorScannerFeatureReads = new AtomicInteger();
    private final AtomicInteger vectorScannerDomainReads = new AtomicInteger();
    private final AtomicInteger vectorFingerprintBranchReads = new AtomicInteger();
    private final AtomicInteger fingerprintMatchAllCalls = new AtomicInteger();
    private final AtomicInteger fullTableReads = new AtomicInteger();

    VectorScannerTrackingSchemaContext(String contextId, String location) {
        super(contextId, location);
    }

    @Override
    public void onQueryExecution(QueryExecutionEvent event) {
        if (!trackingEnabled) {
            return;
        }
        switch (event) {
            case VECTOR_INDEX_INTERACTOR_LOOKUP:
                vectorScannerIndexLookups.incrementAndGet();
                break;
            case VECTOR_INDEX_SCAN:
                vectorScannerScans.incrementAndGet();
                break;
            case VECTOR_FINGERPRINT_SCAN:
                vectorFingerprintBranchReads.incrementAndGet();
                break;
            case FINGERPRINT_FEATURE_LOOKUP:
                vectorScannerFeatureReads.incrementAndGet();
                break;
            case FINGERPRINT_DOMAIN_LOOKUP:
                vectorScannerDomainReads.incrementAndGet();
                break;
            case FINGERPRINT_MATCH_ALL:
                fingerprintMatchAllCalls.incrementAndGet();
                break;
            case FULL_TABLE_SCAN:
                fullTableReads.incrementAndGet();
                break;
        }
    }

    public int getVectorScannerIndexLookups() {
        return vectorScannerIndexLookups.get();
    }

    public int getVectorScannerScans() {
        return vectorScannerScans.get();
    }

    public int getVectorScannerFeatureReads() {
        return vectorScannerFeatureReads.get();
    }

    public int getVectorScannerDomainReads() {
        return vectorScannerDomainReads.get();
    }

    public int getVectorFingerprintBranchReads() {
        return vectorFingerprintBranchReads.get();
    }

    public int getFingerprintMatchAllCalls() {
        return fingerprintMatchAllCalls.get();
    }

    public int getFullTableReads() {
        return fullTableReads.get();
    }

    public void resetScannerUsage() {
        trackingEnabled = false;
        vectorScannerIndexLookups.set(0);
        vectorScannerScans.set(0);
        vectorScannerFeatureReads.set(0);
        vectorScannerDomainReads.set(0);
        vectorFingerprintBranchReads.set(0);
        fingerprintMatchAllCalls.set(0);
        fullTableReads.set(0);
        trackingEnabled = true;
    }

    public void stopTracking() {
        trackingEnabled = false;
    }
}
