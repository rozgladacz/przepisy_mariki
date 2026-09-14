package pl.local.przepisy.domain.scaling

import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.model.PanSpec
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.PI

sealed interface ScaleSpec {
    data class Multiplier(val value: BigDecimal) : ScaleSpec
    data class Pan(val original: PanSpec, val target: PanSpec) : ScaleSpec
}

data class ScaleResult(val factor: BigDecimal, val approximate: Boolean)

object RecipeScaler {
    fun calculate(spec: ScaleSpec): ScaleResult = when (spec) {
        is ScaleSpec.Multiplier -> {
            require(spec.value > BigDecimal.ZERO) { "Mnożnik musi być dodatni" }
            ScaleResult(spec.value, approximate = false)
        }
        is ScaleSpec.Pan -> {
            require(spec.original.isValid() && spec.target.isValid()) { "Wymiary formy muszą być dodatnie" }
            val factor = BigDecimal.valueOf(area(spec.target))
                .divide(BigDecimal.valueOf(area(spec.original)), MathContext.DECIMAL64)
            ScaleResult(factor, approximate = spec.original.shape != spec.target.shape)
        }
    }

    private fun area(pan: PanSpec): Double = when (pan.shape) {
        PanShape.RECTANGLE -> requireNotNull(pan.widthCm) * requireNotNull(pan.heightCm)
        PanShape.CIRCLE -> PI * requireNotNull(pan.diameterCm) * pan.diameterCm / 4.0
    }
}
