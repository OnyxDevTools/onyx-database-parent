package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/15/15.
 */
public class ReplicationPayload implements Externalizable
{
    protected Object payload;
    protected boolean fromChild;
    protected boolean fromParent;

    public ReplicationPayload(Object payload, boolean fromChild, boolean fromParent)
    {
        this.payload = payload;
        this.fromChild = fromChild;
        this.fromParent = fromParent;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public boolean isFromChild() {
        return fromChild;
    }

    public void setFromChild(boolean fromChild) {
        this.fromChild = fromChild;
    }

    public boolean isFromParent() {
        return fromParent;
    }

    public void setFromParent(boolean fromParent) {
        this.fromParent = fromParent;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(payload);
        out.writeBoolean(fromChild);
        out.writeBoolean(fromParent);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        payload = in.readObject();
        fromChild = in.readBoolean();
        fromParent = in.readBoolean();
    }
}
