package database.query;

import com.onyx.descriptor.IndexDescriptor;
import com.onyx.interactors.index.IndexInteractor;
import com.onyx.persistence.context.impl.DefaultSchemaContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

/** Observes native index access and can emulate an interactor without streaming support. */
final class IndexedCountSchemaContext extends DefaultSchemaContext {
    boolean streamingSupported = true;
    final AtomicInteger streamingLookups = new AtomicInteger();
    final AtomicInteger materializedLookups = new AtomicInteger();
    final AtomicInteger postingVisits = new AtomicInteger();

    IndexedCountSchemaContext(String location) {
        super(location, location);
    }

    @Override
    public IndexInteractor getIndexInteractor(IndexDescriptor descriptor) {
        IndexInteractor delegate = super.getIndexInteractor(descriptor);
        if (!descriptor.getEntityDescriptor().getEntityClass().getName().startsWith("database.query.IndexedCount")) {
            return delegate;
        }
        return (IndexInteractor) Proxy.newProxyInstance(
            IndexInteractor.class.getClassLoader(), new Class<?>[] { IndexInteractor.class },
            (proxy, method, arguments) -> {
                if (method.getName().equals("visitExactPostings")) {
                    streamingLookups.incrementAndGet();
                    if (!streamingSupported) throw new UnsupportedOperationException("Streaming is unavailable");
                }
                if (method.getName().equals("findAll")) materializedLookups.incrementAndGet();
                try {
                    Object result = method.invoke(delegate, arguments);
                    if (method.getName().equals("visitExactPostings")) postingVisits.addAndGet((Integer) result);
                    return result;
                } catch (InvocationTargetException exception) {
                    throw exception.getCause();
                }
            }
        );
    }

    void resetWork() {
        streamingLookups.set(0);
        materializedLookups.set(0);
        postingVisits.set(0);
    }
}
