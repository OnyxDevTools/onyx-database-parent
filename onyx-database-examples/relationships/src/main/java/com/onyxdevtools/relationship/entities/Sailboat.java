package com.onyxdevtools.relationship.entities;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.List;

/**
 * Created by tosborn1 on 3/28/16.
 */
@Entity
public class Sailboat extends ManagedEntity implements IManagedEntity
{
    @Identifier
    @Attribute
    protected String registrationCode;

    @Attribute
    protected String name;

    @Relationship(type = RelationshipType.ONE_TO_MANY,
            inverseClass = CrewMember.class,
            inverse = "sailboat",
            cascadePolicy = CascadePolicy.ALL,
            fetchPolicy = FetchPolicy.LAZY)
    protected List<CrewMember> crew;

    @Relationship(type = RelationshipType.ONE_TO_ONE,
            inverseClass = Skipper.class,
            inverse = "sailboat",
            cascadePolicy = CascadePolicy.ALL)
    protected Skipper skipper;

    public String getRegistrationCode() {
        return registrationCode;
    }

    public void setRegistrationCode(String registrationCode) {
        this.registrationCode = registrationCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CrewMember> getCrew() {
        return crew;
    }

    public void setCrew(List<CrewMember> crew) {
        this.crew = crew;
    }

    public Skipper getSkipper() {
        return skipper;
    }

    public void setSkipper(Skipper skipper) {
        this.skipper = skipper;
    }
}