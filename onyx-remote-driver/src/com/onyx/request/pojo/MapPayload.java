package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 5/10/15.
 */
public class MapPayload implements Externalizable
{
    protected Object payload;

    protected String mapName;

    protected boolean isVolatile = true;

    protected boolean fromParent = false;

    protected boolean fromChild = false;

    public Object getPayload()
    {
        return payload;
    }

    public void setPayload(Object payload)
    {
        this.payload = payload;
    }

    public String getMapName()
    {
        return mapName;
    }

    public void setMapName(String mapName)
    {
        this.mapName = mapName;
    }

    public boolean isVolatile() {
        return isVolatile;
    }

    public void setIsVolatile(boolean isVolatile) {
        this.isVolatile = isVolatile;
    }

    public boolean isFromParent() {
        return fromParent;
    }

    public void setFromParent(boolean fromParent) {
        this.fromParent = fromParent;
    }


    public boolean isFromChild() {
        return fromChild;
    }

    public void setFromChild(boolean fromChild) {
        this.fromChild = fromChild;
    }

    /**
     * Constructor
     *
     * @param name
     * @param payload
     */
    public MapPayload(String name, Object payload, boolean isVolatile, boolean fromParent, boolean fromChild)
    {
        this.mapName = name;
        this.payload = payload;
        this.isVolatile = isVolatile;
        this.fromParent = fromParent;
        this.fromChild = fromChild;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException
    {
        out.writeUTF(mapName);
        out.writeObject(payload);
        out.writeBoolean(isVolatile);
        out.writeBoolean(fromParent);
        out.writeBoolean(fromChild);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
    {
        mapName = in.readUTF();
        payload = in.readObject();
        isVolatile = in.readBoolean();
        fromParent = in.readBoolean();
        fromChild = in.readBoolean();
    }
}
