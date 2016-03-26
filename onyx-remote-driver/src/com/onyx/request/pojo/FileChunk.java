package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/18/15.
 */
public class FileChunk implements Externalizable {
    protected int clusterServerId;
    protected int fileChunkId;

    public FileChunk()
    {

    }

    public FileChunk(int clusterServerId, int fileChunkId)
    {
        this.clusterServerId = clusterServerId;
        this.fileChunkId = fileChunkId;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(fileChunkId);
        out.writeInt(clusterServerId);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileChunkId = in.readInt();
        clusterServerId = in.readInt();
    }

    public int getClusterServerId() {
        return clusterServerId;
    }

    public void setClusterServerId(int clusterServerId) {
        this.clusterServerId = clusterServerId;
    }

    public int getFileChunkId() {
        return fileChunkId;
    }

    public void setFileChunkId(int fileChunkId) {
        this.fileChunkId = fileChunkId;
    }

    @Override
    public boolean equals(Object val)
    {
        if(val instanceof FileChunk)
            return ((FileChunk) val).fileChunkId == this.fileChunkId;

        return false;
    }
}