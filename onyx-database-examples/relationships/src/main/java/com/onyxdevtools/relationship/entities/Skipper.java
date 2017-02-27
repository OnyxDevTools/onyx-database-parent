package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

@Entity
@SuppressWarnings("unused")
public class Skipper extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    private long id;

    @Attribute
    private String firstName;

    @Attribute
    private String lastName;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = Sailboat.class, inverse = "skipper", cascadePolicy = CascadePolicy.NONE)
    private Sailboat sailboat;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Sailboat getSailboat() {
        return sailboat;
    }

    public void setSailboat(Sailboat sailboat) {
        this.sailboat = sailboat;
    }
}