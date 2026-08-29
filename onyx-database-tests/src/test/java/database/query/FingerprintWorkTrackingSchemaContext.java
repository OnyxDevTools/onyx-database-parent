package database.query;

import com.onyx.persistence.context.FingerprintSearchWork;
import com.onyx.persistence.context.FingerprintSearchWorkObserver;
import com.onyx.persistence.context.impl.DefaultSchemaContext;

/** Test-only bridge for aggregate bounded-search work diagnostics. */
final class FingerprintWorkTrackingSchemaContext extends DefaultSchemaContext
        implements FingerprintSearchWorkObserver {
    private volatile boolean captured;
    private volatile int candidateLimit;
    private volatile int postingVisitLimit;
    private volatile int routeLookupLimit;
    private volatile int postingVisits;
    private volatile int routeLookups;
    private volatile int candidateCount;
    private volatile int evaluatedCandidateCount;

    FingerprintWorkTrackingSchemaContext(String contextId, String location) {
        super(contextId, location);
    }

    @Override
    public void onFingerprintSearchWork(FingerprintSearchWork work) {
        candidateLimit = work.getCandidateLimit();
        postingVisitLimit = work.getPostingVisitLimit();
        routeLookupLimit = work.getRouteLookupLimit();
        postingVisits = work.getPostingVisits();
        routeLookups = work.getRouteLookups();
        candidateCount = work.getCandidateCount();
        evaluatedCandidateCount = work.getEvaluatedCandidateCount();
        captured = true;
    }

    boolean isFingerprintWorkCaptured() {
        return captured;
    }

    int getCandidateLimit() {
        return candidateLimit;
    }

    int getPostingVisitLimit() {
        return postingVisitLimit;
    }

    int getRouteLookupLimit() {
        return routeLookupLimit;
    }

    int getPostingVisits() {
        return postingVisits;
    }

    int getRouteLookups() {
        return routeLookups;
    }

    int getCandidateCount() {
        return candidateCount;
    }

    int getEvaluatedCandidateCount() {
        return evaluatedCandidateCount;
    }

    void resetFingerprintWork() {
        captured = false;
    }
}
