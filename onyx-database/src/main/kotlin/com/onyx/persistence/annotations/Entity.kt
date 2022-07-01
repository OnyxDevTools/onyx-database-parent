package com.onyx.persistence.annotations

/**
 * Annotation used to indicate a class that is specified as a managed entity.
 *
 * Also, in order to be a managed entity the class must extend the com.onyx.persistence.ManagedEntity class
 *
 * @author Tim Osborn
 * @since 1.0.0
 *
 * <pre>
 *
 * @Entity
 *
 * @Entity
 * public class MyEntity extends ManagedEntity
 * {
 * ...
 * }
 *
 * </pre>
 *
 * @see com.onyx.persistence.ManagedEntity
 */

@Target(AnnotationTarget.CLASS)
annotation class Entity(val fileName: String = "", val archiveDirectories: Array<String> = [])
