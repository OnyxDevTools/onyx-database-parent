package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

/**
 * Created by tosborn1 on 7/8/15.
 */
public class FileInvalidChunksBody implements Externalizable
{
    protected String fileSystemId;
    protected Set invalidChunks;
    protected String fileId;
    protected int entryId;

    public FileInvalidChunksBody()
    {

    }
    public FileInvalidChunksBody(String fileSystemId, String fileId, Set invalidChunks, int entryId)
    {
        this.fileSystemId = fileSystemId;
        this.fileId = fileId;
        this.invalidChunks = invalidChunks;
        this.entryId = entryId;
    }

    public String getFileSystemId() {
        return fileSystemId;
    }

    public void setFileSystemId(String fileSystemId) {
        this.fileSystemId = fileSystemId;
    }

    public Set getInvalidChunks() {
        return invalidChunks;
    }

    public void setInvalidChunks(Set invalidChunks) {
        this.invalidChunks = invalidChunks;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public int getEntryId() {
        return entryId;
    }

    public void setEntryId(int entryId) {
        this.entryId = entryId;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(fileId);
        out.writeInt(entryId);
        out.writeUTF(fileSystemId);
        out.writeObject(invalidChunks);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileId = in.readUTF();
        entryId = in.readInt();
        fileSystemId = in.readUTF();
        invalidChunks = (Set)in.readObject();
    }
}
