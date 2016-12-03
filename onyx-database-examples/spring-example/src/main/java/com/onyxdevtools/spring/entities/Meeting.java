package com.onyxdevtools.spring.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.IdentifierGenerator;

import java.util.Date;

/**
 * Created by tosborn1 on 4/12/16.
 */
@Entity
public class Meeting extends ManagedEntity implements IManagedEntity
{

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected long meetingId;

    @Attribute
    protected Date startTime;

    @Attribute
    protected Date endTime;

    @Attribute
    protected String description;

    @Attribute
    protected String location;

    @Attribute
    protected String notes;

    public long getMeetingId() {
        return meetingId;
    }

    public void setMeetingId(long meetingId) {
        this.meetingId = meetingId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
