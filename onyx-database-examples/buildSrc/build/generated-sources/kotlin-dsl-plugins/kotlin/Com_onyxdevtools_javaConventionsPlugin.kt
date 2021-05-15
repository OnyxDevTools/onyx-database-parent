/**
 * Precompiled [com.onyxdevtools.java-conventions.gradle.kts][Com_onyxdevtools_java_conventions_gradle] script plugin.
 *
 * @see Com_onyxdevtools_java_conventions_gradle
 */
class Com_onyxdevtools_javaConventionsPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Com_onyxdevtools_java_conventions_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
