package com.onyxdevtools.modelupdate.after.entities;


import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by tosborn1 on 5/6/16.
 */
@Entity
public class Account extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected int accountId;

    @Attribute
    protected String accountName;

    @Attribute
    protected float balanceDue;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            inverse = "account",
            inverseClass = Invoice.class,
            cascadePolicy = CascadePolicy.NONE,
            fetchPolicy = FetchPolicy.LAZY)
    protected List<Invoice> invoices;

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public float getBalanceDue() {
        return balanceDue;
    }

    public void setBalanceDue(float balanceDue) {
        this.balanceDue = balanceDue;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public void setInvoices(List<Invoice> invoices) {
        this.invoices = invoices;
    }
}
