package com.onyxdevtools.listener.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import com.onyx.persistence.annotations.values.IdentifierGenerator;

/**
 * Simple POJO / Managed Entity for a Football Player
 */
@Entity
public class Player extends ManagedEntity
{

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    private Integer id;

    @Attribute(nullable=false)
    private String firstName;

    @Attribute(nullable=false)
    private String lastName;

    @Attribute(nullable = false)
    private String position;

    @Attribute
    private boolean isHallOfFame;

    @Attribute
    private boolean didNotCheat;

    @SuppressWarnings("unused")
    public Integer getId()
    {
        return id;
    }

    @SuppressWarnings("unused")
    public void setId(Integer id)
    {
        this.id = id;
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

    @SuppressWarnings("unused")
    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    @SuppressWarnings("unused")
    public boolean isHallOfFame() {
        return isHallOfFame;
    }

    public void setHallOfFame(boolean hallOfFame) {
        isHallOfFame = hallOfFame;
    }

    @SuppressWarnings("unused")
    public boolean isDidNotCheat() {
        return didNotCheat;
    }

    public void setDidNotCheat(boolean didNotCheat) {
        this.didNotCheat = didNotCheat;
    }
}