package com.onyxdevtools.entity;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.Relationship;
import com.onyx.persistence.annotations.values.RelationshipType;

import java.util.Date;
import java.util.List;

@Entity
@SuppressWarnings("unused")
public class Person extends ManagedEntity
{

    @Identifier
    @Attribute
    private String personId;

    @Attribute
    private Date dateCreated;

    @Attribute
    private Date dateUpdated;

    @Attribute
    private String firstName;

    @Attribute
    private String lastName;

    @Relationship(type = RelationshipType.ONE_TO_MANY, inverse = "employee", inverseClass = com.onyxdevtools.entity.Job.class)
    private List<com.onyxdevtools.entity.Job> jobs;

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(List<Job> jobs) {
        this.jobs = jobs;
    }

    @SuppressWarnings("unused")
    public Date getDateCreated()
    {
        return dateCreated;
    }

    @SuppressWarnings("unused")
    public void setDateCreated(Date dateCreated)
    {
        this.dateCreated = dateCreated;
    }

    @SuppressWarnings("unused")
    public Date getDateUpdated()
    {
        return dateUpdated;
    }

    @SuppressWarnings("unused")
    public void setDateUpdated(Date dateUpdated)
    {
        this.dateUpdated = dateUpdated;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

}