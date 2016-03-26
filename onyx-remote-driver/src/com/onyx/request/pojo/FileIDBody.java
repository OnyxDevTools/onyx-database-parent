package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/15/15.
 */
public class FileIDBody implements Externalizable
{
    public FileIDBody()
    {

    }

    protected String fileSystemPath;
    protected String fileId;
    protected boolean isFile;

    /**
     * Constructor
     *
     * @param fileSystemPath
     * @param fileId
     */
    public FileIDBody(String fileSystemPath, String fileId, boolean isFile)
    {
        this.fileSystemPath = fileSystemPath;
        this.fileId = fileId;
        this.isFile = isFile;
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

    public boolean isFile() {
        return isFile;
    }

    public void setIsFile(boolean isFile) {
        this.isFile = isFile;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(fileSystemPath);
        out.writeUTF(fileId);
        out.writeBoolean(isFile);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileSystemPath = in.readUTF();
        fileId = in.readUTF();
        isFile = in.readBoolean();
    }
}
