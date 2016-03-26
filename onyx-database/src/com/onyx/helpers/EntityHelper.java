package com.onyx.helpers;

/**
 * Created by timothy.osborn on 12/23/14.
 */
public class EntityHelper
{

    /*public static Index saveEntityRecord(SchemaContext context, EntityDescriptor descriptor, IManagedEntity entity, EntityDataFilePool filePool, AsynchronousDatabaseFile attributeFile, IndexController indexController) throws EntityException
    {
        Index index = null;

        // This will throw an exception if not valid
        ValidationHelper.validateEntity(descriptor, entity);

        index = indexController.findForInsert(entity);
        filePool.getAttributeFile().write(entity, index.getReference());
        indexController.removePendingSave(entity);

        // Invoke Callbacks
        indexController.invokePostPersistCallback(entity);

        if(index.isNew())
        {
            indexController.invokePostInsertCallback(entity);
        }
        else
        {
            indexController.invokePostUpdateCallback(entity);
        }

        return index;
    }

    public static Index saveEntityRecord(SchemaContext context, EntityDescriptor descriptor, IManagedEntity entity, EntityDataFilePool filePool) throws EntityException
    {
        final IndexController indexController = IndexFactory.getInstance(context).getIndexController(descriptor.getIdentifier(), filePool, descriptor);
        final AsynchronousDatabaseFile attributeFile = filePool.getAttributeFile();

        return saveEntityRecord(context, descriptor, entity, filePool, attributeFile, indexController);

    }


    public static Index deleteEntityRecord(SchemaContext context,IManagedEntity entity, EntityDescriptor descriptor, EntityDataFilePool files) throws EntityException
    {
        final AsynchronousDatabaseFile attributeFile = files.getAttributeFile();

        // Get existing index block
        final IndexController indexController = IndexFactory.getInstance().getIndexController(descriptor.getIdentifier(), files, descriptor);
        final Index index = indexController.find(entity);

        indexController.invokePreRemoveCallback(entity);

        indexController.lock();
        try
        {
            indexController.delete(entity, index.getBlock());
            final DefragmentRecordController defragmentRecordController = new DefragmentRecordController(context, descriptor, attributeFile, files);
            defragmentRecordController.defragmentRecord(index.getReference());
        }
        finally
        {
            indexController.unlock();
        }

        return index;
    }


    public static Index deleteEntityRecord(SchemaContext context,IManagedEntity entity) throws EntityException
    {
        final EntityDescriptor descriptor = context.getDescriptorForEntity(entity);
        final EntityDataFilePool files = context.getAvailableFileForDescriptor(descriptor, entity);

        return deleteEntityRecord(context, entity, descriptor, files);
    }
    */

}
