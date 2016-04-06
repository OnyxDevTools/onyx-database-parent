package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by tosborn1 on 3/28/16.
 */
@Entity
public class CrewMember extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    public long id;

    @Attribute
    public String firstName;

    @Attribute
    public String lastName;

    @Relationship(type = RelationshipType.MANY_TO_ONE,
            inverseClass = Sailboat.class,
            inverse = "crew",
            cascadePolicy = CascadePolicy.NONE)
    public Sailboat sailboat;

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