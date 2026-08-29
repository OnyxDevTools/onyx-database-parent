package database.query;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.interactors.query.impl.DefaultQueryInteractor;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.manager.PersistenceManager;

/** Test-only access to the Kotlin-internal scanner-selection constructor. */
final class DefaultQueryInteractorTestBridge {

    private DefaultQueryInteractorTestBridge() {
    }

    static DefaultQueryInteractor forceFullTable(
            EntityDescriptor descriptor,
            PersistenceManager persistenceManager,
            SchemaContext context
    ) {
        return new DefaultQueryInteractor(
                descriptor,
                persistenceManager,
                context,
                DefaultQueryInteractor.ScannerSelection.FULL_TABLE
        );
    }
}
