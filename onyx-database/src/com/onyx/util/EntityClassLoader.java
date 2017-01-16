package com.onyx.util;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemIndex;
import com.onyx.entity.SystemRelationship;
import com.onyx.persistence.annotations.CascadePolicy;
import com.onyx.persistence.annotations.FetchPolicy;
import com.onyx.persistence.annotations.IdentifierGenerator;
import com.onyx.persistence.annotations.RelationshipType;
import com.onyx.persistence.context.SchemaContext;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;


/**
 Created by timothy.osborn on 3/6/15.
 */
public class EntityClassLoader
{
    protected static final String CLASS_TEMPLATE_PATH = "templates/class.mustache";

    public static Set<String> LOADED_CLASSES = new HashSet<>();

    public static final String GENERATED_DIRECTORY = "generated";
    public static final String GENERATED_ENTITIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "entities";
    public static final String GENERATED_QUERIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "queries";

    public static final String SOURCE_DIRECTORY = "source";
    public static final String SOURCE_ENTITIES_DIRECTORY = SOURCE_DIRECTORY + File.separator + "entities";

    protected static final Mustache CLASS_TEMPLATE;

    static
    {
        MustacheFactory mf = new DefaultMustacheFactory();
        CLASS_TEMPLATE = mf.compile(new InputStreamReader(EntityClassLoader.class.getClassLoader().getResourceAsStream(CLASS_TEMPLATE_PATH)), CLASS_TEMPLATE_PATH);
    }

    /**
     * Generate Write a class to disk.
     *
     * @param  descriptor
     * @param  databaseLocation
     */
    public synchronized static final void writeClass(final EntityDescriptor descriptor, final String databaseLocation)
    {
        final String outputDirectory = databaseLocation + File.separator + SOURCE_ENTITIES_DIRECTORY;

        new File(outputDirectory).mkdirs();

        final Map<String, Object> values = new HashMap();
        values.put("className", descriptor.getClazz().getName().replace(descriptor.getClazz().getPackage().getName() + ".", ""));
        values.put("packageName", descriptor.getClazz().getPackage().getName());
        values.put("generatorType",
            descriptor.getIdentifier().getGenerator().getDeclaringClass().getName() + "." +
            descriptor.getIdentifier().getGenerator().name());
        values.put("idType", descriptor.getIdentifier().getType().getName());
        values.put("idName", descriptor.getIdentifier().getName());

        final List<Map<String, Object>> attributes = new ArrayList<>();
        values.put("attributes", attributes);

        Map<String, Object> attributeMap = null;

        for (final AttributeDescriptor attribute : descriptor.getAttributes().values())
        {

            if (attribute.getName().equals(descriptor.getIdentifier().getName()))
            {
                continue;
            }

            attributeMap = new HashMap();
            attributeMap.put("name", attribute.getName());
            attributeMap.put("type", attribute.getType().getName());

            attributeMap.put("isPartition",
                ((descriptor.getPartition() != null) && descriptor.getPartition().getName().equals(attribute.getName())));

            final IndexDescriptor indexDescriptor = descriptor.getIndexes().get(attribute.getName());
            attributeMap.put("isIndex", (indexDescriptor != null));

            attributes.add(attributeMap);
        }

        final List<Map<String, Object>> relationships = new ArrayList();
        values.put("relationships", relationships);

        Map<String, Object> relationshipMap = null;

        for (final RelationshipDescriptor relationship : descriptor.getRelationships().values())
        {
            relationshipMap = new HashMap();
            relationshipMap.put("name", relationship.getName());

            if ((relationship.getRelationshipType() == RelationshipType.ONE_TO_MANY) ||
                    (relationship.getRelationshipType() == RelationshipType.MANY_TO_MANY))
            {
                final String genericType = relationship.getInverseClass().getName();
                final String collectionClass = relationship.getType().getName();
                final String type = collectionClass + "<" + genericType + ">";
                relationshipMap.put("type", type);
            }
            else
            {
                relationshipMap.put("type", relationship.getType().getName());
            }

            relationshipMap.put("inverseClass", relationship.getInverseClass().getName());
            relationshipMap.put("inverse", relationship.getInverse());
            relationshipMap.put("fetchPolicy",
                relationship.getFetchPolicy().getDeclaringClass().getName() + "." + relationship.getFetchPolicy().name());
            relationshipMap.put("cascadePolicy",
                relationship.getCascadePolicy().getDeclaringClass().getName() + "." + relationship.getCascadePolicy().name());

            relationshipMap.put("relationshipType",
                relationship.getRelationshipType().getDeclaringClass().getName() + "." + relationship.getRelationshipType().name());

            relationshipMap.put("parentClass", relationship.getParentClass().getName());

            relationships.add(relationshipMap);
        }

        try
        {

            // File output
            final File classFile = new File(outputDirectory + File.separator +
                    descriptor.getClazz().getName().replaceAll("\\.", "/") + ".java");
            classFile.getParentFile().mkdirs();
            classFile.createNewFile();

            final Writer file = new FileWriter(outputDirectory + File.separator +
                    descriptor.getClazz().getName().replaceAll("\\.", "/") + ".java");

            CLASS_TEMPLATE.execute(file, values);
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
     * @param  systemEntity
     * @param  databaseLocation
     */
    @SuppressWarnings("unused")
    public synchronized static final void writeClass(final SystemEntity systemEntity, final String databaseLocation)
    {
        final String outputDirectory = databaseLocation + File.separator + SOURCE_ENTITIES_DIRECTORY;

        new File(outputDirectory).mkdirs();

        final Map<String, Object> values = new HashMap();
        values.put("className", systemEntity.getClassName());
        values.put("packageName", systemEntity.getName().replace("."+systemEntity.getClassName(), ""));
        values.put("generatorType", IdentifierGenerator.values()[systemEntity.getIdentifier().getGenerator()].getDeclaringClass().getName() + "." + IdentifierGenerator.values()[systemEntity.getIdentifier().getGenerator()].toString());

        for(SystemAttribute attribute : systemEntity.getAttributes())
        {
            if(attribute.getName().equals(systemEntity.getIdentifier().getName()))
                values.put("idType", attribute.getDataType());
        }
        values.put("idName", systemEntity.getIdentifier().getName());

        final List<Map<String, Object>> attributes = new ArrayList<>();
        values.put("attributes", attributes);

        Map<String, Object> attributeMap = null;

        for (final SystemAttribute attribute : systemEntity.getAttributes())
        {

            if (attribute.getName().equals(systemEntity.getIdentifier().getName()))
            {
                continue;
            }

            attributeMap = new HashMap();
            attributeMap.put("name", attribute.getName());
            attributeMap.put("type", attribute.getDataType());

            attributeMap.put("isPartition",
                    ((systemEntity.getPartition() != null) && systemEntity.getPartition().getName().equals(attribute.getName())));

            boolean isIndex = false;
            for(SystemIndex indexDescriptor : systemEntity.getIndexes())
            {
                if(indexDescriptor.getName().equals(attribute.getName())) {
                    isIndex = true;
                    break;
                }
            }

            attributeMap.put("isIndex", isIndex);

            attributes.add(attributeMap);
        }

        final List<Map<String, Object>> relationships = new ArrayList();
        values.put("relationships", relationships);

        Map<String, Object> relationshipMap = null;

        for (final SystemRelationship relationship : systemEntity.getRelationships())
        {
            relationshipMap = new HashMap();
            relationshipMap.put("name", relationship.getName());

            if ((relationship.getRelationshipType() == RelationshipType.ONE_TO_MANY.ordinal()) ||
                    (relationship.getRelationshipType() == RelationshipType.MANY_TO_MANY.ordinal()))
            {
                final String genericType = relationship.getInverseClass();
                final String collectionClass = List.class.getName();
                final String type = collectionClass + "<" + genericType + ">";
                relationshipMap.put("type", type);
            }
            else
            {
                relationshipMap.put("type", relationship.getInverseClass());
            }

            relationshipMap.put("inverseClass", relationship.getInverseClass());
            relationshipMap.put("inverse", relationship.getInverse());
            relationshipMap.put("fetchPolicy",
                    FetchPolicy.values()[relationship.getFetchPolicy()].getDeclaringClass().getName() + "." + FetchPolicy.values()[relationship.getFetchPolicy()].name());
            relationshipMap.put("cascadePolicy",
                    CascadePolicy.values()[relationship.getCascadePolicy()].getDeclaringClass().getName() + "." + CascadePolicy.values()[relationship.getCascadePolicy()].name());

            relationshipMap.put("relationshipType",
                    RelationshipType.values()[relationship.getRelationshipType()].getDeclaringClass().getName() + "." + RelationshipType.values()[relationship.getRelationshipType()].name());

            relationshipMap.put("parentClass", relationship.getParentClass());

            relationships.add(relationshipMap);
        }

        try
        {

            // File output
            final File classFile = new File(outputDirectory + File.separator +
                    systemEntity.getClassName().replaceAll("\\.", "/") + ".java");
            classFile.getParentFile().mkdirs();
            classFile.createNewFile();

            final Writer file = new FileWriter(outputDirectory + File.separator +
                    systemEntity.getClassName().replaceAll("\\.", "/") + ".java");

            CLASS_TEMPLATE.execute(file, values);
            file.flush();

            file.flush();
            file.close();

        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    protected static SchemaContext schemaContext = null;

    public EntityClassLoader()
    {
    }

    /**
     * Load the class source from file and load it into class loader.
     *
     * @param  context
     */
    public static final void loadClasses(final SchemaContext context, String location)
    {
        schemaContext = context;

        final File entitiesSourceDirectory = new File(location + File.separator + SOURCE_ENTITIES_DIRECTORY);
        final File entitiesGeneratedDirectory = new File(location + File.separator + GENERATED_ENTITIES_DIRECTORY);
        entitiesGeneratedDirectory.mkdirs();
        entitiesSourceDirectory.mkdirs();

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        final List<File> classes = new ArrayList();

        try
        {
            Files.walk(Paths.get(entitiesSourceDirectory.getPath())).filter((e) ->
            {
                return (!e.toFile().isDirectory() && !e.toFile().isHidden() && e.toFile().getPath().endsWith(".java"));
            }).forEach((e) -> { classes.add(e.toFile()); });

            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(entitiesGeneratedDirectory));

            // Compile the file
            compiler.getTask(null, fileManager, null, null, null, fileManager.getJavaFileObjectsFromFiles(classes)).call();

            fileManager.close();

            final URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();

            addClassPaths();

            Files.walk(Paths.get(entitiesSourceDirectory.getPath())).filter((e) ->
            {
                return (!e.toFile().isDirectory() && !e.toFile().isHidden() && e.toFile().getPath().endsWith(".java"));
            }).forEach((e) ->
            {

                try
                {
                    String path = e.toFile().getPath().replace(entitiesSourceDirectory.getPath() + File.separator, "");
                    path = path.replaceAll("\\.java", "");
                    path = path.replaceAll("\\\\", ".");
                    path = path.replaceAll("/", ".");
                    LOADED_CLASSES.add(systemClassLoader.loadClass(path).getName());
                }
                catch (ClassNotFoundException ex)
                {
                    ex.printStackTrace();
                }
            });
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Load the class source from file and load it into class loader.
     *
     * @param  context
     */
    public static final void loadClasses(final SchemaContext context)
    {
       loadClasses(context, context.getLocation());
    }

    public static void addClassPaths()
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
