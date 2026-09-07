package database.query;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.interactors.index.IndexInteractor;
import com.onyx.persistence.context.impl.DefaultSchemaContext;
import kotlin.jvm.functions.Function2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/** Observes ordered traversal and can force the original index/collector path for comparison. */
final class OrderedIndexPageSchemaContext extends DefaultSchemaContext {
    boolean orderedRangesSupported = true;
    int unsupportedAfterVisits = 0;
    Runnable onPosting;
    final AtomicInteger orderedLookups = new AtomicInteger();
    final AtomicInteger postingVisits = new AtomicInteger();

    OrderedIndexPageSchemaContext(String location) {
        super(location, location);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IndexInteractor getIndexInteractor(IndexDescriptor descriptor) {
        IndexInteractor delegate = super.getIndexInteractor(descriptor);
        if (!descriptor.getEntityDescriptor().getEntityClass().getName().startsWith("database.query.OrderedIndexPage")) {
            return delegate;
        }
        return (IndexInteractor) Proxy.newProxyInstance(
            IndexInteractor.class.getClassLoader(), new Class<?>[] { IndexInteractor.class },
            (proxy, method, arguments) -> {
                if (method.getName().equals("visitOrderedRange")) {
                    orderedLookups.incrementAndGet();
                    if (!orderedRangesSupported) throw new UnsupportedOperationException("Ordered ranges unavailable");
                    Function2<Object, Long, Boolean> visitor = (Function2<Object, Long, Boolean>) arguments[4];
                    arguments[4] = (Function2<Object, Long, Boolean>) (value, recordId) -> {
                        int visits = postingVisits.incrementAndGet();
                        if (onPosting != null) onPosting.run();
                        if (unsupportedAfterVisits > 0 && visits >= unsupportedAfterVisits) {
                            throw new UnsupportedOperationException("Ordered range interrupted");
                        }
                        return visitor.invoke(value, recordId);
                    };
                }
                try {
                    return method.invoke(delegate, arguments);
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            }
        );
    }

    void resetWork() {
        orderedLookups.set(0);
        postingVisits.set(0);
    }
}
