package entities;

/**
 * Created by tosborn1 on 4/22/17.
 */
import com.onyx.persistence.ManagedEntity;
import com.onyx.persistence.annotations.*;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by tosborn1 on 4/19/17.
 *
 * This object denotes a page request
 */
@Entity
public class PageAnalytic extends ManagedEntity {

    public PageAnalytic()
    {
        final Calendar rightNow = Calendar.getInstance();
        this.requestDate = rightNow.getTime();
        this.monthYear = String.valueOf(rightNow.get(Calendar.YEAR)) + String.valueOf(rightNow.get(Calendar.MONTH));
    }

    @Attribute
    @Identifier(generator = IdentifierGenerator.SEQUENCE)
    private long pageLoadId;

    @Attribute
    private String path;

    @Attribute
    private Date requestDate;

    @Attribute
    private long loadTime;

    @Partition
    @Attribute
    private String monthYear;

    @Attribute
    private String ipAddress;

    @Attribute
    private int httpStatus;

    @Attribute
    private String agent;

    @SuppressWarnings("unused")
    public long getPageLoadId() {
        return pageLoadId;
    }

    @SuppressWarnings("unused")
    public void setPageLoadId(long pageLoadId) {
        this.pageLoadId = pageLoadId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    @SuppressWarnings("unused")
    public String getAgent()
    {
        return agent;
    }

    @SuppressWarnings("unused")
    public Date getRequestDate() {
        return requestDate;
    }

    @SuppressWarnings("unused")
    public void setRequestDate(Date requestDate) {
        this.requestDate = requestDate;
    }

    @SuppressWarnings("unused")
    public long getLoadTime() {
        return loadTime;
    }

    public void setLoadTime(long loadTime) {
        this.loadTime = loadTime;
    }

    @SuppressWarnings("unused")
    public String getMonthYear() {
        return monthYear;
    }

    @SuppressWarnings("unused")
    public void setMonthYear(String monthYear) {
        this.monthYear = monthYear;
    }

    @SuppressWarnings("unused")
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    @SuppressWarnings("unused")
    public int getHttpStatus() {
        return httpStatus;
    }

    @SuppressWarnings("unused")
    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }
}
