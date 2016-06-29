package com.onyxdevtools.modelupdate.after;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.exception.EntityException;
import com.onyx.index.IndexController;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyx.persistence.query.Query;
import com.onyx.persistence.query.QueryCriteria;
import com.onyx.persistence.query.QueryCriteriaOperator;
import com.onyxdevtools.modelupdate.entities.Account;
import com.onyxdevtools.modelupdate.entities.Invoice;
import com.onyxdevtools.modelupdate.entities.Payment;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Created by tosborn1 on 6/28/16.
 *
 */
public class UpdateIndexDemo {


    // Date formatter used to convert strings to dates
    protected static SimpleDateFormat formatter = new SimpleDateFormat("MM-dd-yyyy");

    /**
     * Main Method to demo the functionality
     * @param persistenceManager Open and valid persistence manager
     */
    public static void demo(PersistenceManager persistenceManager)
    {

        try {


            final Query nonIndexedQuery = new Query(Invoice.class, new QueryCriteria("amount", QueryCriteriaOperator.EQUAL, 44.32));
            List<Payment> payments = persistenceManager.executeQuery(nonIndexedQuery);

            assert payments.size() == 2;

            /*
            final EntityDescriptor entityDescriptor = persistenceManager.getContext().getDescriptorForEntity(Account.class, "");
            final IndexDescriptor indexDescriptor = entityDescriptor.getIndexes().get("dueDate");
            final IndexController indexController = persistenceManager.getContext().getIndexController(indexDescriptor);

            indexController.rebuild();
            */

            final Query indexedQuery = new Query(Payment.class, new QueryCriteria("dueDate", QueryCriteriaOperator.EQUAL, parseDate("03-01-2016")));
            payments = persistenceManager.executeQuery(indexedQuery);

            assert payments.size() == 2;

        } catch (EntityException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Helper method used to parse a date in string format.  Meant to encapsulate the error handling.
     *
     * @param stringDate  String in format of MM-dd-yyyy
     * @return Date value
     */
    protected static Date parseDate(String stringDate)
    {
        try {
            return formatter.parse(stringDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

}
