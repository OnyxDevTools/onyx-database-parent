package ${packageName};

import com.onyx.persistence.annotations.*;
import com.onyx.persistence.*;

@Entity
public class ${className} extends ManagedEntity implements IManagedEntity
{
    public ${className}()
    {

    }

    @Attribute
    @Identifier(generator = ${generatorType})
    public ${idType} ${idName};

<#list attributes as attribute>
    <#if attribute.isPartition == true>
    @Partition
    </#if>
    <#if attribute.isIndex == true>
    @Partition
    </#if>
    @Attribute
    public ${attribute.type} ${attribute.name};

</#list>

<#list relationships as relationship>

    @Relationship(type = ${relationship.relationshipType}
                  ,inverseClass = ${relationship.inverseClass}.class
                  ,inverse = "${relationship.inverse}"
                  ,fetchPolicy = ${relationship.fetchPolicy}
                  ,cascadePolicy = ${relationship.cascadePolicy})
    public ${relationship.type} ${relationship.name};

</#list>

}
