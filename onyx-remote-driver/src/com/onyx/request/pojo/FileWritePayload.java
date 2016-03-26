package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/18/15.
 */
public class FileWritePayload implements Externalizable
{
    protected String fileSystemId;
    protected String fileId;
    protected int chunkId;
    protected int byteSize;
    protected byte[] bytes;
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
    public FileWritePayload(String fileSystemId, String fileId, int chunkId, byte[] bytes, int position)
    {
        this.fileSystemId = fileSystemId;
        this.fileId = fileId;
        this.chunkId = chunkId;
        this.bytes = bytes;
        this.byteSize = bytes.length;
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

    public int getByteSize() {
        return byteSize;
    }

    public void setByteSize(int byteSize) {
        this.byteSize = byteSize;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
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
        out.writeInt(byteSize);

        if(bytes == null)
            bytes = new byte[0];
        out.write(bytes);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileSystemId = in.readUTF();
        fileId = in.readUTF();
        chunkId = in.readInt();
        position = in.readInt();
        byteSize = in.readInt();

        bytes = new byte[byteSize];
        in.read(bytes);
    }
}
