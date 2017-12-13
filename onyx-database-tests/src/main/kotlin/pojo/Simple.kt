package pojo

import java.io.Serializable

class Simple : Serializable {
    var hiya = 3

    override fun hashCode(): Int = hiya

    override fun equals(other: Any?): Boolean = if (other is Simple) {
        other.hiya == hiya
    } else false
}
