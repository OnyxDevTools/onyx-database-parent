package database.query;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.interactors.index.IndexInteractor;
import com.onyx.persistence.context.QueryExecutionEvent;
import com.onyx.persistence.context.QueryExecutionObserver;
import com.onyx.persistence.context.impl.DefaultSchemaContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Records native index work without replacing the index implementation or its results. */
final class IndexedAndTrackingSchemaContext extends DefaultSchemaContext implements QueryExecutionObserver {
    private volatile boolean tracking;
    private final AtomicInteger indexLookups = new AtomicInteger();
    private final AtomicLong materializedPostings = new AtomicLong();
    private final AtomicInteger fullTableScans = new AtomicInteger();
    private final AtomicInteger referenceFilterScans = new AtomicInteger();
    private final AtomicInteger streamingLookups = new AtomicInteger();
    private final AtomicLong postingVisits = new AtomicLong();
    private final AtomicInteger pointProbes = new AtomicInteger();
    boolean streamingSupported = true;
    boolean pointProbesSupported = true;
    Runnable afterStreaming;

    IndexedAndTrackingSchemaContext(String contextId, String location) {
        super(contextId, location);
    }

    @Override
    public IndexInteractor getIndexInteractor(IndexDescriptor descriptor) {
        IndexInteractor delegate = super.getIndexInteractor(descriptor);
        String entityName = descriptor.getEntityDescriptor().getEntityClass().getName();
        if (!entityName.startsWith("entities.") && !entityName.startsWith("database.query.")) {
            return delegate;
        }
        return (IndexInteractor) Proxy.newProxyInstance(
            IndexInteractor.class.getClassLoader(), new Class<?>[] { IndexInteractor.class },
            (proxy, method, arguments) -> {
                if (tracking && method.getName().equals("visitExactPostings")) {
                    streamingLookups.incrementAndGet();
                    if (!streamingSupported) throw new UnsupportedOperationException("Streaming is unavailable");
                }
                if (tracking && method.getName().equals("containsExactPosting")) {
                    pointProbes.incrementAndGet();
                    if (!pointProbesSupported) throw new UnsupportedOperationException("Posting probes are unavailable");
                }
                Object result;
                try {
                    result = method.invoke(delegate, arguments);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
                if (tracking && method.getName().equals("visitExactPostings")) {
                    postingVisits.addAndGet((Integer) result);
                    if (afterStreaming != null) afterStreaming.run();
                }
                if (tracking && (method.getName().equals("findAll") ||
                        method.getName().equals("findAllAbove") ||
                        method.getName().equals("findAllBelow") ||
                        method.getName().equals("findAllBetween"))) {
                    indexLookups.incrementAndGet();
                    materializedPostings.addAndGet(result instanceof Map
                        ? ((Map<?, ?>) result).size() : ((Collection<?>) result).size());
                }
                return result;
            }
        );
    }

    @Override
    public void onQueryExecution(QueryExecutionEvent event) {
        if (!tracking) return;
        if (event == QueryExecutionEvent.FULL_TABLE_SCAN) fullTableScans.incrementAndGet();
        if (event == QueryExecutionEvent.REFERENCE_FILTER_SCAN) referenceFilterScans.incrementAndGet();
    }

    void resetWork() {
        tracking = false;
        indexLookups.set(0);
        materializedPostings.set(0);
        fullTableScans.set(0);
        referenceFilterScans.set(0);
        streamingLookups.set(0);
        postingVisits.set(0);
        pointProbes.set(0);
        tracking = true;
    }

    int getIndexLookups() { return indexLookups.get(); }
    long getMaterializedPostings() { return materializedPostings.get(); }
    int getFullTableScans() { return fullTableScans.get(); }
    int getReferenceFilterScans() { return referenceFilterScans.get(); }
    int getStreamingLookups() { return streamingLookups.get(); }
    long getPostingVisits() { return postingVisits.get(); }
    int getPointProbes() { return pointProbes.get(); }
}
