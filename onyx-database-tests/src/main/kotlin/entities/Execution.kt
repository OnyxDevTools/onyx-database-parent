package entities

import com.onyx.persistence.ManagedEntity
import com.onyx.persistence.annotations.*
import java.util.*

@Suppress("unused")
@Entity(fileName = "executions/execution")
class Execution : ManagedEntity() {

    @Identifier
    var executionId: String = ""

    @Attribute
    var purchaseTime: Date = Date()

    @Attribute
    var closeTime: Date = Date()

    @Index
    var symbol: String = ""

    @Attribute
    var openPrice: Float = 0.0f

    @Attribute
    var closePrice: Float = 0.0f

    @Attribute
    var shares: Int = 0

    @Attribute
    var profitLoss: Float = 0.0f

    @Attribute
    var isSimulated: Int = 1

    @Attribute
    var netChange: Double = 0.0

    @Partition
    var tradeSimulationId: Int = 0

    @Attribute
    var weekYear: String = ""

    @Attribute
    var dayYear: String = ""

    @Attribute
    var strategy: String = ""

    @Attribute
    var volatility: Double? = null

    @Attribute
    var marketGapPercent: Double? = null

    @Attribute
    var gapPercent: Double? = null

    @Attribute
    var marketMean: Double? = null

    @Attribute
    var bullScore: Double? = null

    @Attribute
    var rsi: Double? = null

    @Attribute
    var volume: Double? = null

    @Attribute
    var hadReversal: Boolean? = null

    @Attribute
    var isTop50: Boolean? = null

    @Attribute
    var isTop75: Boolean? = null

    @Attribute
    var atr: Double? = null

    @Attribute
    var isGain: Boolean? = null
}
