package com.onyxdevtools.modelupdate.entities;


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

    // Note for Example 2: I have changed the type of the accountId from an int to a long.  Also the generator was removed.
    @Attribute
    @Identifier
    protected long accountId;

    // Note for Example 1: I have added the Account Holder's Name
    @Attribute
    protected String accountHolderName;

    @Attribute
    protected String accountName;

    // Note for Example 1: I have removed the balance Due since it should not be reflected on the Account
    // and should be reflected on the Invoice
    //@Attribute
    //protected double balanceDue;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            inverse = "account",
            inverseClass = Invoice.class,
            cascadePolicy = CascadePolicy.NONE,
            fetchPolicy = FetchPolicy.LAZY)
    protected List<Invoice> invoices;

    public long getAccountId() {
        return accountId;
    }

    public void setAccountId(long accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public List<Invoice> getInvoices() {
        return invoices;
    }

    public void setInvoices(List<Invoice> invoices) {
        this.invoices = invoices;
    }
}
