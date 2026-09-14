package pl.local.przepisy.domain.scaling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.local.przepisy.domain.model.PanShape
import pl.local.przepisy.domain.model.PanSpec
import java.math.BigDecimal

class RecipeScalingTest {
    @Test
    fun `mnoznik jest osobnym trybem i musi byc dodatni`() {
        val result = RecipeScaler.calculate(ScaleSpec.Multiplier(BigDecimal("1.5")))
        assertEquals(1.5, result.factor.toDouble(), 0.0)
        assertFalse(result.approximate)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            RecipeScaler.calculate(ScaleSpec.Multiplier(BigDecimal.ZERO))
        }
    }

    @Test
    fun `skaluje prostokat wedlug pola`() {
        val result = RecipeScaler.calculate(
            ScaleSpec.Pan(
                PanSpec(PanShape.RECTANGLE, widthCm = 20.0, heightCm = 30.0),
                PanSpec(PanShape.RECTANGLE, widthCm = 30.0, heightCm = 40.0),
            ),
        )
        assertEquals(2.0, result.factor.toDouble(), 0.000001)
        assertFalse(result.approximate)
    }

    @Test
    fun `skaluje kolo bez kumulacji zaokraglen`() {
        val result = RecipeScaler.calculate(
            ScaleSpec.Pan(
                PanSpec(PanShape.CIRCLE, diameterCm = 20.0),
                PanSpec(PanShape.CIRCLE, diameterCm = 30.0),
            ),
        )
        assertEquals(2.25, result.factor.toDouble(), 0.000001)
        assertFalse(result.approximate)
    }

    @Test
    fun `oznacza konwersje ksztaltu jako przyblizona`() {
        val result = RecipeScaler.calculate(
            ScaleSpec.Pan(
                PanSpec(PanShape.RECTANGLE, widthCm = 20.0, heightCm = 20.0),
                PanSpec(PanShape.CIRCLE, diameterCm = 24.0),
            ),
        )
        assertTrue(result.approximate)

        val reverse = RecipeScaler.calculate(
            ScaleSpec.Pan(
                PanSpec(PanShape.CIRCLE, diameterCm = 24.0),
                PanSpec(PanShape.RECTANGLE, widthCm = 20.0, heightCm = 20.0),
            ),
        )
        assertTrue(reverse.approximate)
        assertEquals(1.0, result.factor.multiply(reverse.factor).toDouble(), 0.000001)
    }

    @Test
    fun `kazda zmiana skali liczy sie od niezmiennej formy bazowej`() {
        val original = PanSpec(PanShape.RECTANGLE, widthCm = 20.0, heightCm = 30.0)
        val first = RecipeScaler.calculate(
            ScaleSpec.Pan(original, PanSpec(PanShape.RECTANGLE, widthCm = 30.0, heightCm = 40.0)),
        )
        val second = RecipeScaler.calculate(
            ScaleSpec.Pan(original, PanSpec(PanShape.RECTANGLE, widthCm = 10.0, heightCm = 15.0)),
        )
        assertEquals(2.0, first.factor.toDouble(), 0.000001)
        assertEquals(0.25, second.factor.toDouble(), 0.000001)
    }

    @Test
    fun `wykrywa zapis prostokata i tortownicy`() {
        assertEquals(PanShape.RECTANGLE, PanDetector.detect("blacha 20 x 30 cm")?.shape)
        assertEquals(23.0, PanDetector.detect("tortownica o średnicy 23 cm")?.diameterCm ?: 0.0, 0.0)
    }
}
