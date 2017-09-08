package entities.recreate;

import com.onyx.persistence.IManagedEntity;
import com.onyx.persistence.annotations.Attribute;
import com.onyx.persistence.annotations.Entity;
import com.onyx.persistence.annotations.Identifier;
import entities.AbstractEntity;

/**
 * Created by tosborn on 8/27/14.
 */
@Entity
public class WorkspaceFileTmp extends AbstractEntity implements IManagedEntity
{

    @Identifier
    @Attribute(size = 260)
    protected String path;

    @Attribute
    protected boolean isDirectory;

    @Attribute
    protected Long customId;

    @Attribute
    protected boolean hidden;

    @Attribute(size = 100)
    protected String name;

    @Attribute(size = 260)
    protected String parentPath;

    @Attribute
    protected Boolean ignored;

    @Attribute
    protected Long latestRemoteVersion;

    @Attribute
    protected Long currentVersion;

    @Attribute
    protected Long modifiedBy;

    @Attribute
    protected Long workspace;

    @Attribute
    protected Long changeList;

    @Attribute
    protected int statusOrdinal = -1;

    public Long getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(Long modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Long getCustomId() {
        return customId;
    }

    public void setCustomId(Long customId) {
        this.customId = customId;
    }

    public String getParentPath() {
        return parentPath;
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public Boolean getIgnored() {
        return ignored;
    }

    public void setIgnored(Boolean ignored) {
        this.ignored = ignored;
    }

    public Long getLatestRemoteVersion() {
        return latestRemoteVersion;
    }

    public void setLatestRemoteVersion(Long latestRemoteVersion) {
        this.latestRemoteVersion = latestRemoteVersion;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Long currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getId()
    {
        return path;
    }

    public void setId(String id)
    {
        path = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public boolean isDirectory()
    {
        return isDirectory;
    }

    public void setDirectory(boolean isDirectory)
    {
        this.isDirectory = isDirectory;
    }

    public boolean isHidden()
    {
        return hidden;
    }

    public void setHidden(boolean hidden)
    {
        this.hidden = hidden;
    }

    public Long getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Long workspace) {
        this.workspace = workspace;
    }

    public Long getChangeList() {
        return changeList;
    }

    public void setChangeList(Long changeList) {
        this.changeList = changeList;
    }

}

