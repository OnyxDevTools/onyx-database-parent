package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by tosborn1 on 7/24/15.
 */
public class FileChecksumPojo implements Externalizable
{
    protected String fileSystemPath;
    protected long checksum;
    protected int fileChunk;

    public FileChecksumPojo(String fileSystemPath, long checksum, int fileChunk)
    {
        this.fileSystemPath = fileSystemPath;
        this.checksum = checksum;
        this.fileChunk = fileChunk;
    }

    public String getFileSystemPath() {
        return fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    public long getChecksum() {
        return checksum;
    }

    public void setChecksum(long checksum) {
        this.checksum = checksum;
    }

    public int getFileChunk() {
        return fileChunk;
    }

    public void setFileChunk(int fileChunk) {
        this.fileChunk = fileChunk;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeLong(checksum);
        out.writeInt(fileChunk);
        out.writeUTF(fileSystemPath);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        checksum = in.readLong();
        fileChunk = in.readInt();
        fileSystemPath = in.readUTF();
    }
}
