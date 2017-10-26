package entities

import com.onyx.extension.common.*
import com.onyx.persistence.annotations.Attribute
import com.onyx.persistence.annotations.Entity
import com.onyx.persistence.annotations.Identifier
import com.onyx.persistence.query.QueryCriteriaOperator

/**
 * Created by Tim Osborn on 1/18/17.
 */
@Entity
class AllAttributeV2Entity : AllAttributeEntity() {

    @Identifier
    @Attribute(size = 64)
    override var id: String? = null

    @Attribute
    var mutableFloat: Float? = null
    @Attribute
    var floatValue: Float = 0.toFloat()
    @Attribute
    var mutableByte: Byte? = null
    @Attribute
    var byteValue: Byte = 0
    @Attribute
    var mutableShort: Short? = null
    @Attribute
    var shortValue: Short = 0
    @Attribute
    var mutableChar: Char? = null
    @Attribute
    var charValue: Char = ' '
    @Attribute
    var entity: AllAttributeV2Entity? = null
    @Attribute
    var operator: QueryCriteriaOperator? = null
    @Attribute
    var bytes: ByteArray? = null
    @Attribute
    var shorts: ShortArray? = null
    @Attribute
    var amutableBytes: Array<Byte>? = null
    @Attribute
    var mutableShorts: Array<Short>? = null
    @Attribute
    var strings: Array<String>? = null
    @Attribute
    var entityList: MutableList<AllAttributeEntity>? = null
    @Attribute
    var entitySet: MutableSet<AllAttributeEntity>? = null

    override fun equals(o: Any?): Boolean {
        return o is AllAttributeV2Entity && o.id.forceCompare(id)
    }

    override fun hashCode(): Int {
        return if (id == null) 0 else id!!.hashCode()
    }
}
