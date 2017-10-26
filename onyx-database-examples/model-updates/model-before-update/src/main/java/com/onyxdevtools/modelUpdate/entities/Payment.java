package com.onyxdevtools.modelUpdate.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.RelationshipType;

@Entity
@SuppressWarnings("unused")
public class Payment extends ManagedEntity implements IManagedEntity
{
    @Attribute
    @Identifier
    private long paymentId;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            inverse = "payments",
            inverseClass = Invoice.class)
    private Invoice invoice;

    @Index
    @Attribute
    private double amount;

    @Attribute
    private String notes;

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
