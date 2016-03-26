package com.onyx.util;

import com.onyx.descriptor.AttributeDescriptor;
import com.onyx.descriptor.EntityDescriptor;
import com.onyx.descriptor.IndexDescriptor;
import com.onyx.descriptor.RelationshipDescriptor;
import com.onyx.persistence.context.SchemaContext;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by timothy.osborn on 3/6/15.
 */
public class EntityClassLoader {

    protected static final Configuration cfg;
    protected static final String CLASS_TEMPLATE = "class.ftl";

    public static Set<String> LOADED_CLASSES = new HashSet<>();

    public static final String GENERATED_DIRECTORY = "generated";
    public static final String GENERATED_ENTITIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "entities";
    public static final String GENERATED_QUERIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "queries";

    public static final String SOURCE_DIRECTORY = "source";
    public static final String SOURCE_ENTITIES_DIRECTORY = SOURCE_DIRECTORY + File.separator + "entities";
    public static final String SOURCE_QUERIES_DIRECTORY = SOURCE_DIRECTORY + File.separator + "queries";

    static
    {
        cfg = new Configuration();
        cfg.setClassForTemplateLoading(EntityClassLoader.class, "/templates");
    }

    /**
     * Generate Write a class to disk
     *
     * @param descriptor
     */
    public synchronized static final void writeClass(EntityDescriptor descriptor, String databaseLocation)
    {

        String outputDirectory = databaseLocation + File.separator + SOURCE_ENTITIES_DIRECTORY;

        new File(outputDirectory).mkdirs();

        final Map<String, Object> values = new HashMap();
        values.put("className", descriptor.getClazz().getCanonicalName().replace(descriptor.getClazz().getPackage().getName() + ".", ""));
        values.put("packageName", descriptor.getClazz().getPackage().getName());
        values.put("generatorType", descriptor.getIdentifier().getGenerator().getDeclaringClass().getName() + "." + descriptor.getIdentifier().getGenerator().name());
        values.put("idType", descriptor.getIdentifier().getType().getCanonicalName());
        values.put("idName", descriptor.getIdentifier().getName());

        List<Map<String, Object>> attributes = new ArrayList<>();
        values.put("attributes", attributes);

        Map<String, Object> attributeMap = null;

        for (AttributeDescriptor attribute : descriptor.getAttributes().values())
        {
            if(attribute.getName().equals(descriptor.getIdentifier().getName()))
                continue;
            attributeMap = new HashMap();
            attributeMap.put("name", attribute.getName());
            attributeMap.put("type", attribute.getType().getCanonicalName());

            attributeMap.put("isPartition", (descriptor.getPartition() != null && descriptor.getPartition().getName().equals(attribute.getName())));

            IndexDescriptor indexDescriptor = descriptor.getIndexes().get(attribute.getName());
            attributeMap.put("isIndex", (indexDescriptor != null));

            attributes.add(attributeMap);
        }

        List<Map<String, Object>> relationships = new ArrayList();
        values.put("relationships", relationships);

        Map<String, Object> relationshipMap = null;
        for (RelationshipDescriptor relationship : descriptor.getRelationships().values())
        {
            relationshipMap = new HashMap();
            relationshipMap.put("name", relationship.getName());
            relationshipMap.put("type", relationship.getType().getCanonicalName());
            relationshipMap.put("inverseClass", relationship.getInverseClass().getCanonicalName());
            relationshipMap.put("inverse", relationship.getInverse());
            relationshipMap.put("fetchPolicy", relationship.getFetchPolicy().getDeclaringClass().getName() + "." + relationship.getFetchPolicy().name());
            relationshipMap.put("cascadePolicy", relationship.getCascadePolicy().getDeclaringClass().getName() + "." + relationship.getCascadePolicy().name());
            relationshipMap.put("relationshipType", relationship.getRelationshipType().getDeclaringClass().getName() + "." + relationship.getRelationshipType().name());
            relationshipMap.put("parentClass", relationship.getParentClass().getCanonicalName());

            relationships.add(relationshipMap);
        }


        try
        {
            //Load template from source folder
            Template template = cfg.getTemplate(CLASS_TEMPLATE);

            // File output
            File classFile = new File(outputDirectory + File.separator + descriptor.getClazz().getCanonicalName().replaceAll("\\.", "/") + ".java");
            classFile.getParentFile().mkdirs();
            classFile.createNewFile();

            final Writer file = new FileWriter(outputDirectory + File.separator + descriptor.getClazz().getCanonicalName().replaceAll("\\.", "/") + ".java");
            template.process(values, file);
            file.flush();
            file.close();

        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (TemplateException e)
        {
            e.printStackTrace();
        }
    }

    protected static SchemaContext schemaContext = null;

    /**
     * Load the class source from file and load it into class loader
     *
     * @param context
     */
    public static final void loadClasses(SchemaContext context)
    {
        schemaContext = context;
        final File entitiesSourceDirectory = new File(context.getLocation() + File.separator + SOURCE_ENTITIES_DIRECTORY);
        final File entitiesGeneratedDirectory = new File(context.getLocation() + File.separator + GENERATED_ENTITIES_DIRECTORY);
        entitiesGeneratedDirectory.mkdirs();
        entitiesSourceDirectory.mkdirs();

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        List<File> classes = new ArrayList();

        try
        {
            Files.walk(Paths.get(entitiesSourceDirectory.getPath()))
                    .filter(
                    (e) -> {
                        return (!e.toFile().isDirectory() && !e.toFile().isHidden() && e.toFile().getPath().endsWith(".java"));
                    })
                    .forEach(
                    (e) -> {
                        classes.add(e.toFile());
                    });

            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(entitiesGeneratedDirectory));

            // Compile the file
            compiler.getTask(null,
                    fileManager,
                    null,
                    null,
                    null,
                    fileManager.getJavaFileObjectsFromFiles(classes))
                    .call();

            fileManager.close();

            URLClassLoader systemClassLoader =  (URLClassLoader)ClassLoader.getSystemClassLoader();

            addClassPaths();

            Files.walk(Paths.get(entitiesSourceDirectory.getPath()))
                    .filter(
                    (e) -> {
                        return (!e.toFile().isDirectory() && !e.toFile().isHidden() && e.toFile().getPath().endsWith(".java"));
                    }).forEach(
                    (e) -> {
                        try
                        {
                            String path = e.toFile().getPath().replace(entitiesSourceDirectory.getPath() + File.separator, "");
                            path = path.replaceAll("\\.java", "");
                            path = path.replaceAll("\\\\", ".");
                            path = path.replaceAll("/", ".");
                            LOADED_CLASSES.add(systemClassLoader.loadClass(path).getCanonicalName());
                        } catch (ClassNotFoundException ex){
                            ex.printStackTrace();
                        }
                    });
        } catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    public static void addClassPaths()
    {

        URLClassLoader systemClassLoader =  (URLClassLoader)ClassLoader.getSystemClassLoader();

        // Add URL to class path
        Class systemClass = URLClassLoader.class;

        try
        {
            Method method = systemClass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(systemClassLoader, new File(schemaContext.getLocation() + File.separator + GENERATED_ENTITIES_DIRECTORY).toURL());
            method.invoke(systemClassLoader, new File(schemaContext.getLocation() + File.separator + GENERATED_QUERIES_DIRECTORY).toURL());
        }
        catch (Exception ignore){}
    }

    /**
     * Wrapper for getting template
     *
     * @param name
     * @return
     * @throws IOException
     */
    public static Template getTemplate(String name) throws IOException
    {
        return cfg.getTemplate(name);
    }
}
