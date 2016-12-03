package com.onyxdevtools.modelupdate.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by tosborn1 on 5/6/16.
 */
@Entity
public class Payment extends ManagedEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    protected long paymentId;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            inverse = "payments",
            inverseClass = Invoice.class)
    protected Invoice invoice;

    @Index
    @Attribute
    protected double amount;

    @Attribute
    protected String notes;

    public long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(long paymentId) {
        this.paymentId = paymentId;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
