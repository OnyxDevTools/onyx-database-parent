package com.onyxdevtools.quickstart.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

/**
 * Created by tosborn1 on 3/28/16.
 */
@Entity
public class Skipper extends ManagedEntity implements IManagedEntity
{
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    @Attribute
    protected long id;

    @Attribute
    protected String firstName;

    @Attribute
    protected String lastName;

    @Relationship(type = RelationshipType.ONE_TO_ONE, inverseClass = Sailboat.class, inverse = "skipper", cascadePolicy = CascadePolicy.NONE)
    protected Sailboat sailboat;

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