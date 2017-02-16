package com.onyx.serialization;

import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.ObjectIdInfo;
import com.onyx.structure.serializer.ObjectSerializable;

/**
 * Created by tosborn1 on 2/14/17.
 *
 * The purpose of this is because Jackson JSon serialization is a piece of shit.  I need to account for
 * nested objects.  Luckily they have an obscure hack for this.  That hack is below:
 */
public class CustomAnnotationInspector extends JacksonAnnotationIntrospector {

    /**
     * If the object is an ObjectSerializable, than apply the hack.
     * @param annotated Object to see if hack applies
     * @return Object ID Info with annotation including @ID
     */
    @Override
    public ObjectIdInfo findObjectIdInfo(final Annotated annotated) {
        if (ObjectSerializable.class.isAssignableFrom(annotated.getRawType())) {
            return new ObjectIdInfo(
                    PropertyName.construct("@id", null),
                    null,
                    ObjectIdGenerators.IntSequenceGenerator.class,
                    null);
        }
        return super.findObjectIdInfo(annotated);
    }
}
