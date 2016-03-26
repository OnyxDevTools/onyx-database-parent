package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by tosborn1 on 7/7/15.
 */
public class FileChunkInvalidateBody implements Externalizable
{
    protected int entryId;
    protected int chunkId;
    protected String fileSystemId;
    protected String fileId;

    public FileChunkInvalidateBody()
    {

    }

    /**
     * Constructor
     *
     * @param entryId
     * @param chunkId
     */
    public FileChunkInvalidateBody(int entryId, int chunkId, String fileSystemId, String fileId)
    {
        this.entryId = entryId;
        this.chunkId = chunkId;
        this.fileSystemId = fileSystemId;
        this.fileId = fileId;
    }

    public int getEntryId() {
        return entryId;
    }

    public void setEntryId(int entryId) {
        this.entryId = entryId;
    }

    public int getChunkId() {
        return chunkId;
    }

    public void setChunkId(int chunkId) {
        this.chunkId = chunkId;
    }

    public String getFileSystemId() {
        return fileSystemId;
    }

    public void setFileSystemId(String fileSystemId) {
        this.fileSystemId = fileSystemId;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    /////////////////////////////////////////////////
    //
    // Externalizable
    //
    /////////////////////////////////////////////////
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(entryId);
        out.writeInt(chunkId);
        out.writeUTF(fileId);
        out.writeUTF(fileSystemId);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        entryId = in.readInt();
        chunkId = in.readInt();
        fileId = in.readUTF();
        fileSystemId = in.readUTF();
    }

    /**
     * Override hash code
     * @return
     */
    @Override
    public int hashCode()
    {
        return chunkId;
    }

    /**
     * Override equals
     * @return
     */
    @Override
    public boolean equals(Object val)
    {
        if(val instanceof FileChunkInvalidateBody)
        {
            if(((FileChunkInvalidateBody) val).chunkId == chunkId
                    && ((FileChunkInvalidateBody) val).entryId == entryId)
            {
                return true;
            }
        }
        return false;
    }
}
