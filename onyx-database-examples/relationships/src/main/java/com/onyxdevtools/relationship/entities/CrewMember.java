package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;

@Entity
@SuppressWarnings("unused")
public class CrewMember extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    private long id;

    @Attribute
    private String firstName;

    @Attribute
    private String lastName;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            inverseClass = Sailboat.class,
            inverse = "crew",
            cascadePolicy = CascadePolicy.NONE)
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