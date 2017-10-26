package pojo

import java.io.Serializable

class Simple : Serializable {
    var hiya = 3

    override fun hashCode(): Int {
        return hiya
    }

    override fun equals(`val`: Any?): Boolean {
        return if (`val` is Simple) {
            `val`.hiya == hiya
        } else false

    }
}
