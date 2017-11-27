package com.onyxdevtools.partition.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.List;

@Entity
@SuppressWarnings("unused")
public class CellPhone extends ManagedEntity implements IManagedEntity
{
    @Identifier
    @Attribute
    private String cellPhoneNumber;

    @Attribute
    @Partition
    private int areaCode;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            inverseClass = CallLog.class,
            inverse = "callFrom",
            cascadePolicy = CascadePolicy.SAVE,
            fetchPolicy = FetchPolicy.NONE)
    private List<CallLog> callLogs;

    public String getCellPhoneNumber() {
        return cellPhoneNumber;
    }

    public void setCellPhoneNumber(String cellPhoneNumber) {
        this.cellPhoneNumber = cellPhoneNumber;
    }

    public int getAreaCode() {
        return areaCode;
    }

    public void setAreaCode(int areaCode) {
        this.areaCode = areaCode;
    }

    public List<CallLog> getCallLogs() {
        return callLogs;
    }

    public void setCallLogs(List<CallLog> callLogs) {
        this.callLogs = callLogs;
    }
}
