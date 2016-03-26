package com.onyx.view.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by timothy.osborn on 10/8/14.
 */
public class ProviderUtil {
    protected static SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");

    public static String getFormattedString(Date date)
    {
        if(date == null)
        {
            return "";
        }
        synchronized (formatter)
        {
            return formatter.format(date);
        }
    }
}
