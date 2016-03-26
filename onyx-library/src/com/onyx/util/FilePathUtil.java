package com.onyx.util;

/**
 * Created by timothy.osborn on 10/7/14.
 */
public class FilePathUtil
{
    public static String getRelativePath(String filePath, String workspacePath)
    {
        return filePath.replace(workspacePath, "");
    }
}
