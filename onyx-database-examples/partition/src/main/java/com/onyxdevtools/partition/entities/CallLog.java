package com.onyxdevtools.partition.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.Date;

/**
 * Created by tosborn1 on 3/31/16.
 */
@Entity
public class CallLog extends ManagedEntity implements IManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    protected long callLogId;

    @Attribute
    protected String destinationNumber;

    @Attribute
    protected Date timeDateStarted;

    @Index
    @Attribute
    protected boolean isNSAListening;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            inverseClass = CellPhone.class,
            inverse = "callLogs",
            cascadePolicy = CascadePolicy.NONE,
            fetchPolicy = FetchPolicy.NONE)
    protected CellPhone callFrom;

    @Attribute
    @Partition
    protected int callFromAreaCode;

    @PreInsert
    protected void onInsertCallLog() throws Exception
    {
        this.timeDateStarted = new Date();
    }

    public int getCallFromAreaCode() {
        return callFromAreaCode;
    }

    public void setCallFromAreaCode(int callFromAreaCode) {
        this.callFromAreaCode = callFromAreaCode;
    }

    public long getCallLogId() {
        return callLogId;
    }

    public void setCallLogId(long callLogId) {
        this.callLogId = callLogId;
    }

    public String getDestinationNumber() {
        return destinationNumber;
    }

    public void setDestinationNumber(String destinationNumber) {
        this.destinationNumber = destinationNumber;
    }

    public Date getTimeDateStarted() {
        return timeDateStarted;
    }

    public void setTimeDateStarted(Date timeDateStarted) {
        this.timeDateStarted = timeDateStarted;
    }

    public boolean isNSAListening() {
        return isNSAListening;
    }

    public void setNSAListening(boolean NSAListening) {
        isNSAListening = NSAListening;
    }

    public CellPhone getCallFrom() {
        return callFrom;
    }

    public void setCallFrom(CellPhone callFrom) {
        this.callFrom = callFrom;
    }
}
