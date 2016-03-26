package com.onyx.request.pojo;

/**
 * Created by timothy.osborn on 5/27/15.
 */
public class AtomicPayload
{
    protected String keyName;
    protected Object value = null;

    public AtomicPayload(String keyName, Object value)
    {
        this.keyName = keyName;
        this.value = value;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
