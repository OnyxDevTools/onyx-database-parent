package com.onyx.request.pojo;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Created by timothy.osborn on 6/26/15.
 */
public class FileInvalidateBody extends FilePathBody implements Externalizable {

    public FileInvalidateBody()
    {

    }

    protected String location;

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Constructor
     *
     * @param fileSystemPath
     * @param filePath
     * @param isFile
     */
    public FileInvalidateBody(String fileSystemPath, String filePath, boolean isFile, String location) {
        super(fileSystemPath, filePath, isFile);
        this.location = location;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(fileSystemPath);
        out.writeUTF(filePath);
        out.writeBoolean(isFile);
        out.writeUTF(location);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        fileSystemPath = in.readUTF();
        filePath = in.readUTF();
        isFile = in.readBoolean();
        location = in.readUTF();
    }
}
