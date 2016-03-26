package com.onyx.serializers;

import com.onyx.descriptor.EntityDescriptor;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.collections.LazyRelationshipCollection;
import org.nustaq.serialization.FSTBasicObjectSerializer;
import org.nustaq.serialization.FSTClazzInfo;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

import java.io.IOException;
import java.util.List;

/**
 * Created by timothy.osborn on 5/7/15.
 *
 * @exclude
 */
public class LazyRelationshipCollectionSerializer extends FSTBasicObjectSerializer {

    private SchemaContext context;

    public LazyRelationshipCollectionSerializer(SchemaContext context)
    {
        this.context = context;
    }

    @Override
    public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException
    {
        LazyRelationshipCollection collection = (LazyRelationshipCollection)toWrite;
        try
        {
            out.writeObject(collection.getIdentifiers());
            out.writeStringUTF(collection.getEntityDescriptor().getClazz().getCanonicalName());

        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo reference, int streamPosition)
    {
        try
        {
            final List identifiers = (List) in.readObject();
            final String className = (String) in.readStringUTF();

            final EntityDescriptor descriptor = context.getBaseDescriptorForEntity(Class.forName(className));

            LazyRelationshipCollection col = new LazyRelationshipCollection(descriptor, identifiers, context);
            in.registerObject(col,streamPosition,serializationInfo, reference);

            return col;

        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
