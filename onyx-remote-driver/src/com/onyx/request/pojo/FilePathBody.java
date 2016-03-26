package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/15/15.
 */
public class FilePathBody implements Externalizable
{

    public FilePathBody()
    {

    }

    protected String fileSystemPath;
    protected String filePath;
    protected boolean isFile;

    /**
     * Constructor
     *
     * @param fileSystemPath
     * @param filePath
     */
    public FilePathBody(String fileSystemPath, String filePath, boolean isFile)
    {
        this.fileSystemPath = fileSystemPath;
        this.filePath = filePath;
        this.isFile = isFile;
    }

    public String getFileSystemPath() {
        return fileSystemPath;
    }

    public void setFileSystemPath(String fileSystemPath) {
        this.fileSystemPath = fileSystemPath;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
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
        out.writeUTF(filePath);
        out.writeBoolean(isFile);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileSystemPath = in.readUTF();
        filePath = in.readUTF();
        isFile = in.readBoolean();
    }
}
