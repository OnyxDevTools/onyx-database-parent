package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/15/15.
 */
public class FileMoveBody implements Externalizable
{
    protected String fileSystemPath;
    protected String toPath;
    protected String fromPath;

    /**
     * Constructor
     *
     * @param fileSystemPath
     * @param toPath
     * @param fromPath
     */
    public FileMoveBody(String fileSystemPath, String toPath, String fromPath)
    {
        this.fileSystemPath = fileSystemPath;
        this.toPath = toPath;
        this.fromPath = fromPath;
    }

    public String getFileSystemPath() {
        return fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    public String getToPath() {
        return toPath;
    }

    public void setToPath(String toPath) {
        this.toPath = toPath;
    }

    public String getFromPath() {
        return fromPath;
    }

    public void setFromPath(String fromPath) {
        this.fromPath = fromPath;
    }

    /**
     * Externalizable methods
     *
     * @param out
     * @throws IOException
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(fileSystemPath);
        out.writeUTF(toPath);
        out.writeUTF(fromPath);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileSystemPath = in.readUTF();
        toPath = in.readUTF();
        fromPath = in.readUTF();
    }
}
