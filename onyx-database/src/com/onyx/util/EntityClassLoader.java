package com.onyx.util;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemIndex;
import com.onyx.entity.SystemRelationship;
import com.onyx.exception.EntityException;
import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.annotations.IdentifierGenerator;
import com.onyx.persistence.annotations.RelationshipType;
import com.onyx.persistence.context.SchemaContext;
import com.onyx.persistence.context.impl.CacheSchemaContext;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 Created by timothy.osborn on 3/6/15.

 This class saves the entity information and formats the source on disk
 */
@SuppressWarnings("WeakerAccess")
public class EntityClassLoader
{

    public static final Set<String> LOADED_CLASSES = new HashSet<>();

    public static final String GENERATED_DIRECTORY = "generated";
    public static final String GENERATED_ENTITIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "entities";
    public static final String GENERATED_QUERIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "queries";

    public static final String SOURCE_DIRECTORY = "source";
    public static final String SOURCE_ENTITIES_DIRECTORY = SOURCE_DIRECTORY + File.separator + "entities";

    /**
     * Generate Write a class to disk.
     *
     * @param  descriptor Entity descriptor
     * @param  databaseLocation Database location
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public synchronized static void writeClass(final EntityDescriptor descriptor, final String databaseLocation, SchemaContext context)
    {
        if(context instanceof CacheSchemaContext)
            return;

        final String outputDirectory = databaseLocation + File.separator + SOURCE_ENTITIES_DIRECTORY;

        //noinspection ResultOfMethodCallIgnored
        new File(outputDirectory).mkdirs();

        String packageName = descriptor.getClazz().getPackage().getName();
        String fileName = descriptor.getFileName();
        String className = descriptor.getClazz().getName().replace(descriptor.getClazz().getPackage().getName() + ".", "");
        String generatorType = descriptor.getIdentifier().getGenerator().getDeclaringClass().getName() + "." +
                        descriptor.getIdentifier().getGenerator().name();
        String idType = descriptor.getIdentifier().getType().getName();
        String idName = descriptor.getIdentifier().getName();
        String idLoadFactor = String.valueOf(descriptor.getIdentifier().getLoadFactor());


        StringBuilder builder = new StringBuilder();
        builder.append("package ");
        builder.append(packageName);
        builder.append(";\n" +
                "\n" +
                "import com.onyx.persistence.annotations.*;\n" +
                "import com.onyx.persistence.*;\n" +
                "\n" +
                "@Entity(fileName = \"");
        builder.append(fileName).append("\")\n").append( "public class ");
        builder.append(className);
        builder.append(" extends ManagedEntity implements IManagedEntity\n" +
                "{\n" +
                "    public ");
        builder.append(className);
        builder.append("()\n" +
                "    {\n" +
                "\n" +
                "    }\n" +
                "\n" +
                "    @Attribute\n" +
                "    @Identifier(generator = ");
        builder.append(generatorType);
        builder.append(", loadFactor = ");
        builder.append(idLoadFactor);
        builder.append(")\n" +
                "    public ");
        builder.append(idType);
        builder.append(" ");
        builder.append(idName);
        builder.append(";\n\n");

        for (final AttributeDescriptor attribute : descriptor.getAttributes().values())
        {
            if (attribute.getName().equals(descriptor.getIdentifier().getName()))
            {
                continue;
            }

            builder.append("\n     @Attribute\n");

            final IndexDescriptor indexDescriptor = descriptor.getIndexes().get(attribute.getName());
            if(indexDescriptor != null)
            {
                builder.append("     @Index\n");
            }
            if((descriptor.getPartition() != null) && descriptor.getPartition().getName().equals(attribute.getName()))
            {
                builder.append("     @Partition\n");
            }
            builder.append("     public ");
            if(attribute.getType().isArray())
            {
                builder.append(attribute.getType().getSimpleName());
            }
            else {
                builder.append(attribute.getType().getName());
            }
            builder.append(" ").append(attribute.getName()).append(";");
        }


        for (final RelationshipDescriptor relationship : descriptor.getRelationships().values()) {

            // Get the base Descriptor so that we ensure they get generated also
            try {
                context.getBaseDescriptorForEntity(relationship.getInverseClass());
            } catch (EntityException ignore) {}

            builder.append("\n     @Relationship(type = ");
            builder.append(relationship.getRelationshipType().getDeclaringClass().getName()).append(".").append(relationship.getRelationshipType().name());
            builder.append(", inverseClass = ");
            builder.append(relationship.getInverseClass().getName());
            builder.append(".class, inverse = \"");
            builder.append(relationship.getInverse());
            builder.append("\", fetchPolicy = ");
            builder.append(relationship.getFetchPolicy().getDeclaringClass().getName()).append(".").append(relationship.getFetchPolicy().name());
            builder.append(", cascadePolicy = ");
            builder.append(relationship.getCascadePolicy().getDeclaringClass().getName()).append(".").append(relationship.getCascadePolicy().name());
            builder.append(", loadFactor = ");
            builder.append(String.valueOf(relationship.getLoadFactor()));
            builder.append(")\n" +
                    "            public ");
            builder.append(relationship.getType().getName()).append(" ").append(relationship.getName()).append(";");
        }

        builder.append("\n}\n");

        try
        {

            // File output
            final File classFile = new File(outputDirectory + File.separator +
                    descriptor.getClazz().getName().replaceAll("\\.", "/") + ".java");
            classFile.getParentFile().mkdirs();
            classFile.createNewFile();

            final Writer file = new FileWriter(outputDirectory + File.separator +
                    descriptor.getClazz().getName().replaceAll("\\.", "/") + ".java");

            file.append(builder.toString());
            file.flush();

            file.flush();
            file.close();

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Generate Write a class to disk.  This is used for remote purposes.  Since we do not have a handle on the entity descriptors, we use the system entity to load
     *
     * @param  systemEntity System entity
     * @param  databaseLocation Database location
     */
    @SuppressWarnings({"unused", "ResultOfMethodCallIgnored"})
    public synchronized static void writeClass(final SystemEntity systemEntity, final String databaseLocation, SchemaContext context)
    {
        final String outputDirectory = databaseLocation + File.separator + SOURCE_ENTITIES_DIRECTORY;

        new File(outputDirectory).mkdirs();

        String packageName = systemEntity.getName().replace("."+systemEntity.getClassName(), "");
        String className = systemEntity.getClassName();
        String generatorType = IdentifierGenerator.values()[systemEntity.getIdentifier().getGenerator()].getDeclaringClass().getName() + "." + IdentifierGenerator.values()[systemEntity.getIdentifier().getGenerator()].toString();
        String fileName = systemEntity.getFileName();
        AtomicReference<String> idType = new AtomicReference<>();
        AtomicReference<String> idName = new AtomicReference<>();
        systemEntity.getAttributes().stream().filter(attribute -> attribute.getName().equals(systemEntity.getIdentifier().getName())).forEach(attribute ->
        {
            idType.set(attribute.getDataType());
            idName.set(attribute.getName());
        });
        String idLoadFactor = String.valueOf(systemEntity.getIdentifier().getLoadFactor());


        StringBuilder builder = new StringBuilder();
        builder.append("package ");
        builder.append(packageName);
        builder.append(";\n" +
                "\n" +
                "import com.onyx.persistence.annotations.*;\n" +
                "import com.onyx.persistence.*;\n" +
                "\n" +
                "@Entity(fileName = \"");
        builder.append(fileName).append("\")\n").append( "public class ");
        builder.append(" extends ManagedEntity implements IManagedEntity\n" +
                "{\n" +
                "    public ");
        builder.append(className);
        builder.append("()\n" +
                "    {\n" +
                "\n" +
                "    }\n" +
                "\n" +
                "    @Attribute\n" +
                "    @Identifier(generator = ");
        builder.append(generatorType);
        builder.append(", loadFactor = ");
        builder.append(idLoadFactor);
        builder.append(")\n" +
                "    public ");
        builder.append(idType.get());
        builder.append(" ");
        builder.append(idName.get());
        builder.append(";\n\n");

        for (final SystemAttribute attribute : systemEntity.getAttributes())
        {
            if (attribute.getName().equals(systemEntity.getIdentifier().getName()))
            {
                continue;
            }

            boolean isIndex = false;
            for(SystemIndex indexDescriptor : systemEntity.getIndexes())
            {
                if(indexDescriptor.getName().equals(attribute.getName())) {
                    isIndex = true;
                    break;
                }
            }
            builder.append("\n     @Attribute\n");
            if(isIndex)
            {
                builder.append("     @Index\n");
            }
            if((systemEntity.getPartition() != null) && systemEntity.getPartition().getName().equals(attribute.getName()))
            {
                builder.append("     @Partition\n");
            }
            builder.append("     public ");
            builder.append(attribute.getDataType());
            builder.append(" ").append(attribute.getName()).append(";");
        }

        builder.append("\n\n");

        for (final SystemRelationship relationship : systemEntity.getRelationships()) {

            String type;

            if ((relationship.getRelationshipType() == RelationshipType.ONE_TO_MANY.ordinal()) ||
                    (relationship.getRelationshipType() == RelationshipType.MANY_TO_MANY.ordinal()))
            {
                final String genericType = relationship.getInverseClass();
                final String collectionClass = List.class.getName();
                type = collectionClass + "<" + genericType + ">";
            }
            else
            {
                type = relationship.getInverseClass();
            }


            builder.append("@Relationship(type = ");
            builder.append(type);
            builder.append(", inverseClass = ");
            builder.append(relationship.getInverseClass());
            builder.append(".class, inverse = \"");
            builder.append(relationship.getInverse());
            builder.append("\", fetchPolicy = ");
            builder.append(FetchPolicy.values()[relationship.getFetchPolicy()].getDeclaringClass().getName()).append(".").append(FetchPolicy.values()[relationship.getFetchPolicy()].name());
            builder.append(", cascadePolicy = ");
            builder.append(CascadePolicy.values()[relationship.getCascadePolicy()].getDeclaringClass().getName()).append(".").append(CascadePolicy.values()[relationship.getCascadePolicy()].name());
            builder.append(", loadFactor = ");
            builder.append(String.valueOf(relationship.getLoadFactor()));
            builder.append(")\n" +
                    "            public ");
            builder.append(type).append(" ").append(relationship.getName()).append(";");

            // Get the base Descriptor so that we ensure they get generated also
            try {
                context.getBaseDescriptorForEntity(Class.forName(relationship.getInverseClass()));
            } catch (EntityException | ClassNotFoundException ignore) {}


        }

        builder.append("\n}\n");

        try
        {

            // File output
            final File classFile = new File(outputDirectory + File.separator +
                    systemEntity.getClassName().replaceAll("\\.", "/") + ".java");
            classFile.getParentFile().mkdirs();
            classFile.createNewFile();

            final Writer file = new FileWriter(outputDirectory + File.separator +
                    systemEntity.getClassName().replaceAll("\\.", "/") + ".java");

            file.write(builder.toString());
            file.flush();

            file.flush();
            file.close();

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public EntityClassLoader()
    {
    }

    /**
     * Load the class source from file and load it into class loader.
     *
     * @param  context Schema Context
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void loadClasses(final SchemaContext context, String location)
    {

        final File entitiesSourceDirectory = new File(location + File.separator + SOURCE_ENTITIES_DIRECTORY);
        final File entitiesGeneratedDirectory = new File(location + File.separator + GENERATED_ENTITIES_DIRECTORY);
        entitiesGeneratedDirectory.mkdirs();
        entitiesSourceDirectory.mkdirs();

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        final List<File> classes = new ArrayList<>();

        try
        {
            forEachClass(new File(entitiesSourceDirectory.getPath()), classes::add);

            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(entitiesGeneratedDirectory));

            // Compile the file
            compiler.getTask(null, fileManager, null, null, null, fileManager.getJavaFileObjectsFromFiles(classes)).call();

            fileManager.close();

            final URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();

            addClassPaths(context);

            forEachClass(new File(entitiesSourceDirectory.getPath()), o -> {
                String path = o.getPath().replace(entitiesSourceDirectory.getPath() + File.separator, "");
                path = path.replaceAll("\\.java", "");
                path = path.replaceAll("\\\\", ".");
                path = path.replaceAll("/", ".");
                try {
                    LOADED_CLASSES.add(systemClassLoader.loadClass(path).getName());
                } catch (ClassNotFoundException e1) {
                    e1.printStackTrace();
                }
            });

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void forEachClass(File root, Consumer<File> consumer) {

        File[] list = root.listFiles();

        for (File f : list) {
            if (!f.isDirectory() && !f.isHidden() && f.getPath().endsWith(".java")) {
                consumer.accept(f);
            }
            if (f.isDirectory()) {
                forEachClass(f, consumer);
            }
        }
    }
    /**
     * Load the class source from file and load it into class loader.
     *
     * @param  context Schema Context
     */
    public static void loadClasses(final SchemaContext context)
    {
       loadClasses(context, context.getLocation());
    }

    @SuppressWarnings("unchecked")
    public static void addClassPaths(SchemaContext schemaContext)
    {
        final URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();

        // Add URL to class path
        final Class systemClass = URLClassLoader.class;

        try
        {
            final Method method = systemClass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(systemClassLoader, new File(schemaContext.getLocation() + File.separator + GENERATED_ENTITIES_DIRECTORY).toURI().toURL());
            method.invoke(systemClassLoader, new File(schemaContext.getLocation() + File.separator + GENERATED_QUERIES_DIRECTORY).toURI().toURL());
        }
        catch (Exception ignore)
        {
        }
    }

}
