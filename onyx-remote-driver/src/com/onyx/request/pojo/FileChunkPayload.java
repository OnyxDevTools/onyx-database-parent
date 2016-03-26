package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/18/15.
 */
public class FileChunkPayload implements Externalizable
{
    protected String fileSystemPath;
    protected String fileId;
    protected FileChunk chunk;

    public FileChunkPayload()
    {

    }
    /**
     * Constructor
     *
     * @param fileSystemPath
     * @param fileId
     * @param chunk
     */
    public FileChunkPayload(String fileSystemPath, String fileId, FileChunk chunk)
    {
        this.fileSystemPath = fileSystemPath;
        this.fileId = fileId;
        this.chunk = chunk;
    }

    public String getFileSystemPath() {
        return fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public FileChunk getChunk() {
        return chunk;
    }

    public void setChunk(FileChunk chunk) {
        this.chunk = chunk;
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Externalizable
    //
    //////////////////////////////////////////////////////////////////////
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(fileSystemPath);
        out.writeUTF(fileId);
        out.writeObject(chunk);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileSystemPath = in.readUTF();
        fileId = in.readUTF();
        chunk = (FileChunk)in.readObject();
    }
}
