package com.onyxdevtools.modelupdate.after;

import com.onyx.exception.EntityException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyx.persistence.query.QueryOrder;
import com.onyx.stream.QueryMapStream;
import com.onyx.stream.QueryStream;
import com.onyxdevtools.modelupdate.entities.Account;
import com.onyxdevtools.modelupdate.entities.Invoice;
import com.onyxdevtools.modelupdate.entities.Payment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by tosborn1 on 6/28/16.
 *
 * Manual Migrations utilizes an API within the Persistence Manager that has many uses.  It can be used to Migrate data,
 * perform analytics, or run complex scripts.
 *
 * This example will use it to convert data from an old format to a new format.  Also, there is an example where it is used to
 * update data based on very specific criteria.
 */
class ManualMigrationDemo {

    /**
     * Main Method to demo the functionality
     * @param persistenceManager Open and valid persistence manager
     */
    @SuppressWarnings("unchecked")
    static void demo(PersistenceManager persistenceManager) throws EntityException
    {

        // This is a simple example of how to use the stream() api to iterate through payment records and dynamically update the payment record
        // with very specific data driven format.
        final Query paymentQuery = new Query(Payment.class, new QueryCriteria("amount", QueryCriteriaOperator.LESS_THAN_EQUAL, 1.0));

        // Basically, this code will find all the payments under a specific amount and add a kind note to that payment.
        //
        // Notice the stream interface we are using is QueryStream.  This is specifically for iterating through the data in its entity format as opposed to
        // a generic structure.
        //noinspection RedundantCast
        persistenceManager.stream(paymentQuery, (QueryStream<Payment>) (payment, internalPersistenceManager) -> {
            payment.setNotes("The Deadbeat with account number: " + payment.getInvoice().getAccount().getAccountId() + " didn't pay anything cause he/she is broke!!!");
            try {
                internalPersistenceManager.saveEntity(payment);
            } catch (EntityException e) {
                e.printStackTrace();
            }
        });

        // In this example we need to do some more complex actions due to the scale of the model updates.  We happened to orphan a field that
        // we want to use to populate other data.  In this case, just because we have removed the field does not mean the data has been lost.
        // We specifically use the QueryMapStream so that the entity is in format of a Map and we can see the hidden fields that no longer exist.
        //
        // This example moves the balanceDue from the account and puts it on the invoice where the property belongs.
        //
        // Note: If the account was saved after model updates, the attribute will no longer be available for us to read.
        final Query accountQuery = new Query(Account.class, new QueryCriteria("accountId", QueryCriteriaOperator.NOT_NULL));

        persistenceManager.stream(accountQuery, (QueryMapStream) (o, internalPersistenceManager) -> {
            Map accountMap = (Map)o;
            try {

                // Ensure the property still exists within the data.
                if(accountMap.containsKey("balanceDue"))
                {
                    double balanceDue = (double) accountMap.get("balanceDue");

                    // This field has been updated to long in the new data model but, the old data model persisted it as an int.  So, that is the reason why we cast it as an integer.
                    int accountId = (int) accountMap.get("accountId");

                    // Get the latest invoice
                    final QueryCriteria fetchInvoiceCriteria = new QueryCriteria("invoiceId", QueryCriteriaOperator.NOT_NULL).and("account.accountId", QueryCriteriaOperator.EQUAL, accountId);
                    final Query invoiceQuery = new Query(Invoice.class, fetchInvoiceCriteria);
                    invoiceQuery.setQueryOrders(Collections.singletonList(new QueryOrder("invoiceDate", false)));
                    invoiceQuery.setMaxResults(1);

                    final List<Invoice> invoices = internalPersistenceManager.executeQuery(invoiceQuery);

                    Invoice latestInvoice = null;

                    // Ensure there is an invoice associated to the account.
                    if (invoices.size() > 0) {
                        latestInvoice = invoices.get(0);
                    }

                    // Update the invoice to the balance due on the Account.
                    if (latestInvoice != null) {
                        latestInvoice.setAmount(balanceDue);
                        internalPersistenceManager.saveEntity(latestInvoice);
                    }
                }

            }catch (EntityException e)
            {
                e.printStackTrace();
            }

        });




    }

}
