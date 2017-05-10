package com.onyxdevtools.quickstart.entities;

import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;

/**
 * Created by tosborn1 on 5/3/17.
 */
@Entity
public class Other extends ManagedEntity {

    public Other()
    {

    }

    @Identifier
    @Attribute
    private String otherString;

    @Attribute
    private String hiya;

    public String getOtherString() {
        return otherString;
    }

    public void setOtherString(String otherString) {
        this.otherString = otherString;
    }

    public String getHiya() {
        return hiya;
    }

    public void setHiya(String hiya) {
        this.hiya = hiya;
    }
}
