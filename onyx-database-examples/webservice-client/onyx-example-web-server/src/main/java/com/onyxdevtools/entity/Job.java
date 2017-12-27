package com.onyxdevtools.entity;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Relationship;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

@Entity
@SuppressWarnings("unused")
public class Job extends ManagedEntity
{

    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    private long jobId;

    @Attribute
    private String description;

    @Relationship(type = RelationshipType.MANY_TO_ONE, inverse = "jobs", inverseClass = Person.class)
    private Person employee;

    public long getJobId() {
        return jobId;
    }

    public void setJobId(long jobId) {
        this.jobId = jobId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Person getEmployee() {
        return employee;
    }

    public void setEmployee(Person employee) {
        this.employee = employee;
    }
}