package com.onyx.util;

import com.onyx.entity.SystemAttribute;
import com.onyx.entity.SystemEntity;
import com.onyx.entity.SystemIndex;
import com.onyx.entity.SystemRelationship;
import com.onyx.exception.OnyxException;
import com.onyx.persistence.annotations.values.CascadePolicy;
import com.onyx.persistence.annotations.values.FetchPolicy;
import com.onyx.persistence.annotations.values.IdentifierGenerator;
import com.onyx.persistence.annotations.values.RelationshipType;
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
 * Created by timothy.osborn on 3/6/15.
 * <p>
 * This class saves the entity information and formats the source on disk
 */
@SuppressWarnings("WeakerAccess")
public class EntityClassLoader {

    public static final Set<String> LOADED_CLASSES = new HashSet<>();
    public static final Set<String> WRITTEN_CLASSES = new HashSet<>();

    public static final String GENERATED_DIRECTORY = "generated";
    public static final String GENERATED_ENTITIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "entities";
    public static final String GENERATED_QUERIES_DIRECTORY = GENERATED_DIRECTORY + File.separator + "queries";

    public static final String SOURCE_DIRECTORY = "source";
    public static final String XTEND_SOURCE_DIRECTORY = "xtend";
    public static final String SOURCE_ENTITIES_DIRECTORY = SOURCE_DIRECTORY + File.separator + "entities";
    public static final String XTEND_SOURCE_ENTITIES_DIRECTORY = XTEND_SOURCE_DIRECTORY + File.separator + "entities";

    /**
     * Write an enum to file
     * @param enumClass Enum Class Name
     * @param enumValues Enum values separated by comman and ended with a semicolon.
     * @param outputDirectory Source directory
     */
    public static void writeEnum(String enumClass, String enumValues, String outputDirectory) {
        String[] nameTokens = enumClass.split("\\.");
        String packageName = "package ";
        for (int i = 0; i < nameTokens.length - 1; i++)
            packageName = packageName + nameTokens[i] + ".";

        packageName = packageName.substring(0, packageName.length() - 1);
        packageName = packageName + ";\n\n";

        String classDef = "public enum " + nameTokens[nameTokens.length - 1] + " { \n";
        String end = "\n}";
        String classContents = packageName + classDef + enumValues + end;

        writeClassContents(outputDirectory, enumClass, classContents);
    }

    /**
     * Helper method used to write the class contents to a file
     *
     * @param outputDirectory Source directory
     * @param className Name of class including package
     * @param classContents Class source code
     * @param extension file extension
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void writeClassContents(String outputDirectory, String className, String classContents, String extension) {
        try {
            // File output
            final File classFile = new File(outputDirectory + File.separator +
                    className.replaceAll("\\.", "/") + extension);
            classFile.getParentFile().mkdirs();
            classFile.createNewFile();

            final Writer file = new FileWriter(outputDirectory + File.separator +
                    className.replaceAll("\\.", "/") + extension);

            file.write(classContents);
            file.flush();

            file.flush();
            file.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method used to write the class contents to a file
     *
     * @param outputDirectory Source directory
     * @param className Name of class including package
     * @param classContents Class source code
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void writeClassContents(String outputDirectory, String className, String classContents) {
        writeClassContents(outputDirectory, className, classContents, ".java");
    }

    public static void writeXtendClass(final SystemEntity systemEntity, final String databaseLocation, SchemaContext schemaContext, String idType, String idName) {
        final String packageName = systemEntity.getName().replace("." + systemEntity.getClassName(), "");
        final String outputDirectory = databaseLocation + File.separator + XTEND_SOURCE_ENTITIES_DIRECTORY;
        final String className = systemEntity.getClassName();
        final String generatorType = IdentifierGenerator.values()[systemEntity.getIdentifier().getGenerator()].toString();
        final String fileName = systemEntity.getFileName();
        final String idLoadFactor = String.valueOf(systemEntity.getIdentifier().getLoadFactor());

        final StringBuilder builder = new StringBuilder();
        builder.append("package ");
        builder.append(packageName);
        builder.append("\n" +
                "\n" +
                "import com.onyx.persistence.annotations.*\n" +
                "import com.onyx.persistence.*\n" +
                "import net.sagittarian.onyx.annotations.OnyxFields\n" +
                "import net.sagittarian.onyx.annotations.OnyxJoins\n" +
                "import org.eclipse.xtend.lib.annotations.Accessors" +
                "\n\n" +
                "@Entity(fileName = \"")
                .append(fileName)
                .append("\")\n")
                .append("@OnyxFields\n")
                .append("@OnyxJoins\n")
                .append("@Accessors\n")
                .append("class ")
                .append(className)
                .append(" extends ManagedEntity implements IManagedEntity\n{\n\n")
                .append("    @Attribute\n")
                .append("    @Identifier(generator = ")
                .append(generatorType)
                .append(", loadFactor = ")
                .append(idLoadFactor)
                .append(")\n    ")
                .append(idType)
                .append(" ")
                .append(idName)
                .append(";\n\n");

        for (final SystemAttribute attribute : systemEntity.getAttributes()) {
            if (attribute.getName().equals(systemEntity.getIdentifier().getName())) {
                continue;
            }

            if (attribute.isEnum()) {
                writeEnum(attribute.getDataType(), attribute.getEnumValues(), outputDirectory);
            }

            boolean isIndex = false;
            for (SystemIndex indexDescriptor : systemEntity.getIndexes()) {
                if (indexDescriptor.getName().equals(attribute.getName())) {
                    isIndex = true;
                    break;
                }
            }
            builder.append("\n    @Attribute\n");
            if (isIndex) {
                builder.append("    @Index\n");
            }

            if ((systemEntity.getPartition() != null) && systemEntity.getPartition().getName().equals(attribute.getName())) {
                builder.append("    @Partition\n");
            }
            builder.append("    ")
                   .append(attribute.getDataType())
                   .append(" ").append(attribute.getName()).append("\n");
        }

        builder.append("\n\n");

        for (final SystemRelationship relationship : systemEntity.getRelationships()) {

            String type;

            if ((relationship.getRelationshipType() == RelationshipType.ONE_TO_MANY.ordinal()) ||
                    (relationship.getRelationshipType() == RelationshipType.MANY_TO_MANY.ordinal())) {
                final String genericType = relationship.getInverseClass();
                final String collectionClass = List.class.getName();
                type = collectionClass + "<" + genericType + ">";
            } else {
                type = relationship.getInverseClass();
            }

            builder.append("    @Relationship(type = ")
                   .append(RelationshipType.values()[relationship.getRelationshipType()].name())
                   .append(", inverseClass = ")
                   .append(relationship.getInverseClass())
                   .append(", inverse = \"")
                   .append(relationship.getInverse())
                   .append("\", fetchPolicy = ")
                   .append(FetchPolicy.values()[relationship.getFetchPolicy()].name())
                   .append(", cascadePolicy = ")
                   .append(CascadePolicy.values()[relationship.getCascadePolicy()].name())
                   .append(", loadFactor = ")
                   .append(String.valueOf(relationship.getLoadFactor()))
                   .append(")\n    ")
                   .append(type)
                   .append(" ")
                   .append(relationship.getName())
                   .append("\n");

            // Get the base Descriptor so that we ensure they get generated also
            try {
                schemaContext.getBaseDescriptorForEntity(Class.forName(relationship.getInverseClass()));
            } catch (OnyxException | ClassNotFoundException ignore) {
            }
        }

        builder.append("\n}\n");

        writeClassContents(outputDirectory, systemEntity.getName(), builder.toString(), ".xtend");
    }

    /**
     * Generate Write a class to disk.  This is used for remote purposes.  Since we do not have a handle on the entity descriptors, we use the system entity to load
     *
     * @param systemEntity     System entity
     * @param databaseLocation Database location
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public synchronized static void writeClass(final SystemEntity systemEntity, final String databaseLocation, SchemaContext context) {
        if(context instanceof CacheSchemaContext)
            return;

        final String outputDirectory = databaseLocation + File.separator + SOURCE_ENTITIES_DIRECTORY;
        new File(outputDirectory).mkdirs();

        AtomicReference<String> idType = new AtomicReference<>();
        AtomicReference<String> idName = new AtomicReference<>();

        WRITTEN_CLASSES.add(systemEntity.getName());

        for (SystemAttribute attribute : systemEntity.getAttributes()) {
            if (attribute.getName().equals(systemEntity.getIdentifier().getName())) {
                idType.set(attribute.getDataType());
                idName.set(attribute.getName());
                break;
            }
        }

        writeXtendClass(systemEntity, databaseLocation, context, idType.get(), idName.get());
    }

    @SuppressWarnings({"WeakerAccess", "unused"})
    public EntityClassLoader() {
    }

    /**
     * Load the class source from file and load it into class loader.
     *
     * @param context Schema Context
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void loadClasses(final SchemaContext context, String location) {

        final File entitiesSourceDirectory = new File(location + File.separator + SOURCE_ENTITIES_DIRECTORY);
        final File entitiesGeneratedDirectory = new File(location + File.separator + GENERATED_ENTITIES_DIRECTORY);
        entitiesGeneratedDirectory.mkdirs();
        entitiesSourceDirectory.mkdirs();

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        final List<File> classes = new ArrayList<>();

        try {
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

        } catch (Exception e) {
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
     * @param context Schema Context
     */
    public static void loadClasses(final SchemaContext context) {
        loadClasses(context, context.getLocation());
    }

    @SuppressWarnings("unchecked")
    public static void addClassPaths(SchemaContext schemaContext) {
        final URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();

        // Add URL to class path
        final Class systemClass = URLClassLoader.class;

        try {
            final Method method = systemClass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(systemClassLoader, new File(schemaContext.getLocation() + File.separator + GENERATED_ENTITIES_DIRECTORY).toURI().toURL());
            method.invoke(systemClassLoader, new File(schemaContext.getLocation() + File.separator + GENERATED_QUERIES_DIRECTORY).toURI().toURL());
        } catch (Exception ignore) {
        }
    }

}
