package servers

/**
 * Created by Tim Osborn on 2/9/17.
 */
class TestRemoteImplementation : TestRemoteInterface {

    override fun sayHello(): String {
        return "hello"
    }
}
