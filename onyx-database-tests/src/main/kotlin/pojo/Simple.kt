package pojo

import java.io.Serializable

class Simple : Serializable {
    var hiya = 3

    override fun hashCode(): Int = hiya

    override fun equals(`val`: Any?): Boolean = if (`val` is Simple) {
        `val`.hiya == hiya
    } else false
}
