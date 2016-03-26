package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/18/15.
 */
public class FileReadPayload implements Externalizable
{
    protected String fileSystemId;
    protected String fileId;
    protected int chunkId;
    protected int length;
    protected int position;

    /**
     * Constructor
     *
     * @param fileSystemId
     * @param fileId
     * @param chunkId
     * @param bytes
     * @param position
     */
    public FileReadPayload(String fileSystemId, String fileId, int chunkId, int length, int position)
    {
        this.fileSystemId = fileSystemId;
        this.fileId = fileId;
        this.chunkId = chunkId;
        this.length = length;
        this.position = position;
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

    public int getChunkId() {
        return chunkId;
    }

    public void setChunkId(int chunkId) {
        this.chunkId = chunkId;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    ////////////////////////////////////////////////////////////////////
    //
    // Externalizable
    //
    ///////////////////////////////////////////////////////////////////
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(fileSystemId);
        out.writeUTF(fileId);
        out.writeInt(chunkId);
        out.writeInt(position);
        out.writeInt(length);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileSystemId = in.readUTF();
        fileId = in.readUTF();
        chunkId = in.readInt();
        position = in.readInt();
        length = in.readInt();
    }
}
