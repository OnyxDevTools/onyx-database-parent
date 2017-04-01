package com.onyxdevtools.modelupdate.after;

import com.onyx.exception.EntityException;
import com.onyx.persistence.manager.PersistenceManager;
import com.onyxdevtools.modelupdate.entities.Invoice;
import com.onyxdevtools.modelupdate.entities.Payment;

import java.util.List;

/**
 * Created by tosborn1 on 6/28/16.
 *
 * This demonstrates how a To One relationship can be changed to a To Many Relationship to support
 * easy model updates.  One thing to note is that it may be easy for Onyx to transpose a relationship
 * going from one to many but, going back to a To One Relationship is not handled by the lightweight migration.
 */
class UpdateRelationshipDemo {

    static void demo(PersistenceManager persistenceManager) throws EntityException
    {
        final Invoice myLatestInvoice = persistenceManager.findById(Invoice.class, 1L);

        // This used to be a single entity and now it is a list of payments since the relationship has changed from a One To One
        // to a One To Many.  This is also handled by the light weight migration.
        List<Payment> paymentList = myLatestInvoice.getPayments();

        // What previously was a one to one relationship should have the existing record in the set.
        assert paymentList.size() == 1;
        assert paymentList.get(0).getPaymentId() == 1;

        // Lets add another payment to ensure we can have a ToMany Relationship
        final Payment mySecondPayment = new Payment();
        mySecondPayment.setAmount(10.01);
        mySecondPayment.setNotes("Getting a start on next months bill since I don't have enough money because I am too busy writing open source software and not focusing on my day job.");
        mySecondPayment.setInvoice(myLatestInvoice);

        // Persist the Payment
        persistenceManager.saveEntity(mySecondPayment);

        // Refresh the collection and ensure there are 2 items
        persistenceManager.initialize(myLatestInvoice, "payments");

        assert paymentList.size() == 2;
    }
}
