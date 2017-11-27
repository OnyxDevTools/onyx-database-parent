package com.onyxdevtools.modelUpdate.after;

import com.onyx.exception.OnyxException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.modelUpdate.entities.Invoice;
import com.onyxdevtools.modelUpdate.entities.Payment;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by Tim Osborn on 6/28/16.
 *
 * The purpose of this Demo class is to outline how an index can be removed and added and the queries that
 * would be impacted by the model changes.
 *
 * This will also display how new indexes also need to rebuild in order to become useful.  The rebuilding of an index
 * is done automatically at startup and should not need to be triggered manually.
 */
class UpdateIndexDemo {


    // Date formatter used to convert strings to dates
    private static final SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy");

    /**
     * Main Method to demo the functionality
     * @param persistenceManager Open and valid persistence manager
     */
    @SuppressWarnings("unchecked")
    static void demo(PersistenceManager persistenceManager)
    {

        try {

            // This query predicates on a field that is no longer indexed.  This will trigger a full table scan which is sub-optimal.
            // The old index no longer applies.
            final Query nonIndexedQuery = new Query(Invoice.class, new QueryCriteria("amount", QueryCriteriaOperator.EQUAL, 44.32));
            List<Payment> payments = persistenceManager.executeQuery(nonIndexedQuery);

            // See that we still get results by doing the full table scan.
            assert payments.size() == 2;

            // The Code snippet below outlines how to perform an index rebuild manually.  This will go through and index all of the records by the specified field.
            // When doing a lightweight migration this should not be needed since the database will detect the change and automatically trigger the build asynchronously.
            /*
            final EntityDescriptor entityDescriptor = persistenceManager.getContext().getDescriptorForEntity(Account.class, "");
            final IndexDescriptor indexDescriptor = entityDescriptor.getIndexes().get("dueDate");
            final IndexInteractor indexInteractor = persistenceManager.getContext().getIndexInteractor(indexDescriptor);

            indexInteractor.rebuild();
            */

            // This query uses the new index to execute the query.
            // NOTE: It may take some time for the index to be rebuilt if you have large data sets.
            // In the meantime you may not get the full expected results.
            final Query indexedQuery = new Query(Invoice.class, new QueryCriteria("dueDate", QueryCriteriaOperator.EQUAL, parseDate("04-01-2016")));
            final List<Invoice> invoices = persistenceManager.executeQuery(indexedQuery);

            // First pass may not give you 2 results since the index could still be rebuilding.  After it is done re-indexing you should have 2 results.
            assert invoices.size() == 2;

        } catch (OnyxException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Helper method used to parse a date in string format.  Meant to encapsulate the error handling.
     *
     * @param stringDate  String in format of MM-dd-yyyy
     * @return Date key
     */
    private static Date parseDate(@SuppressWarnings("SameParameterValue") String stringDate)
    {
        try {
            return formatter.parse(stringDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

}
