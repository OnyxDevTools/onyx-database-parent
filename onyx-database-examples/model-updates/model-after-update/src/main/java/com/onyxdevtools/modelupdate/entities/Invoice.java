package com.onyxdevtools.modelupdate.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.Date;
import java.util.List;

/**
 * Created by tosborn1 on 5/6/16.
 */
@Entity
public class Invoice extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier
    protected Long invoiceId;

    @Attribute
    protected Date invoiceDate;

    // Note for Example 4: I have added an index the the invoice due date
    @Index
    @Attribute
    protected Date dueDate;

    @Attribute
    protected double amount;

    @Attribute
    protected String notes;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            inverse = "invoice",
            inverseClass = Payment.class,
            cascadePolicy = CascadePolicy.SAVE,
            fetchPolicy = FetchPolicy.EAGER)
    protected List<Payment> payments;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            inverseClass = Account.class,
            inverse = "invoices")
    protected Account account;

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public Date getInvoiceDate() {
        return invoiceDate;
    }

    public void setInvoiceDate(Date invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public void setDueDate(Date dueDate) {
        this.dueDate = dueDate;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public void setPayments(List<Payment> payments) {
        this.payments = payments;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
