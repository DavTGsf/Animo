package com.example.animo.domain

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

data class Descriptive(
    val n: Int,
    val mean: Double,
    val median: Double,
    /** Puede haber empate entre varias modas. */
    val mode: List<Double>,
    /** Desviación estándar muestral (n − 1). 0 si n < 2. */
    val sd: Double,
)

data class Interval(val low: Double, val high: Double) {
    val excludesZero: Boolean get() = low > 0.0 || high < 0.0
}

/** Estadística pura, sin dependencias de Android. */
object Stats {

    fun median(xs: List<Double>): Double {
        require(xs.isNotEmpty())
        val s = xs.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
    }

    fun mode(xs: List<Double>): List<Double> {
        val counts = xs.groupingBy { it }.eachCount()
        val max = counts.values.maxOrNull() ?: return emptyList()
        return counts.filterValues { it == max }.keys.sorted()
    }

    fun variance(xs: List<Double>): Double {
        if (xs.size < 2) return 0.0
        val m = xs.average()
        return xs.sumOf { (it - m) * (it - m) } / (xs.size - 1)
    }

    fun sd(xs: List<Double>): Double = sqrt(variance(xs))

    fun describe(xs: List<Double>): Descriptive? =
        if (xs.isEmpty()) null else Descriptive(xs.size, xs.average(), median(xs), mode(xs), sd(xs))

    /** d de Cohen con desviación combinada. Mide el tamaño de la diferencia, no su "significancia". */
    fun cohenD(a: List<Double>, b: List<Double>): Double {
        if (a.size < 2 || b.size < 2) return 0.0
        val diff = a.average() - b.average()
        val pooled = sqrt(((a.size - 1) * variance(a) + (b.size - 1) * variance(b)) / (a.size + b.size - 2))
        return when {
            pooled > 0.0 -> diff / pooled
            diff == 0.0 -> 0.0
            else -> if (diff > 0) 10.0 else -10.0
        }
    }

    /**
     * Prueba de permutación (dos colas) para la diferencia de medias. No supone normalidad,
     * lo que importa porque el ánimo es ordinal y las muestras son pequeñas. La semilla es fija
     * para que el resultado sea reproducible.
     */
    fun permutationPValue(a: List<Double>, b: List<Double>, iterations: Int = 2000, seed: Int = 42): Double {
        if (a.isEmpty() || b.isEmpty()) return 1.0
        val observed = abs(a.average() - b.average())
        val pooled = (a + b).toDoubleArray()
        val rnd = Random(seed)
        var extreme = 0
        repeat(iterations) {
            pooled.shuffle(rnd)
            var sa = 0.0
            for (i in a.indices) sa += pooled[i]
            var sb = 0.0
            for (i in a.size until pooled.size) sb += pooled[i]
            if (abs(sa / a.size - sb / b.size) >= observed - 1e-12) extreme++
        }
        return (extreme + 1.0) / (iterations + 1.0)
    }

    /**
     * Ajuste de Benjamini-Hochberg (control de la tasa de falsos descubrimientos).
     * Evita que, al probar muchas actividades, alguna parezca relevante solo por azar.
     */
    fun benjaminiHochberg(p: List<Double>): List<Double> {
        val m = p.size
        if (m == 0) return emptyList()
        val order = p.indices.sortedBy { p[it] }
        val q = DoubleArray(m)
        var prev = 1.0
        for (rank in m downTo 1) {
            val i = order[rank - 1]
            prev = minOf(prev, p[i] * m / rank)
            q[i] = prev
        }
        return q.toList()
    }

    /** Intervalo de confianza del 95 % para la diferencia de medias (Welch). */
    fun meanDiffCI(a: List<Double>, b: List<Double>): Interval? {
        if (a.size < 2 || b.size < 2) return null
        val va = variance(a) / a.size
        val vb = variance(b) / b.size
        val diff = a.average() - b.average()
        val se = sqrt(va + vb)
        if (se == 0.0) return Interval(diff, diff)
        val denom = va * va / (a.size - 1) + vb * vb / (b.size - 1)
        val df = if (denom == 0.0) (a.size + b.size - 2).toDouble() else (va + vb) * (va + vb) / denom
        val t = tCritical95(df)
        return Interval(diff - t * se, diff + t * se)
    }

    private val T95 = doubleArrayOf(
        12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
        2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
        2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042,
    )

    /** Valor crítico t de dos colas al 95 %, conservador (redondea los grados de libertad hacia abajo). */
    fun tCritical95(df: Double): Double {
        val d = floor(df).toInt()
        return when {
            d < 1 -> T95[0]
            d <= 30 -> T95[d - 1]
            d < 40 -> 2.042
            d < 60 -> 2.021
            d < 120 -> 2.000
            else -> 1.980
        }
    }

    fun pearson(x: List<Double>, y: List<Double>): Double? {
        require(x.size == y.size)
        if (x.size < 3) return null
        val mx = x.average()
        val my = y.average()
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        for (i in x.indices) {
            val dx = x[i] - mx
            val dy = y[i] - my
            sxy += dx * dy; sxx += dx * dx; syy += dy * dy
        }
        return if (sxx == 0.0 || syy == 0.0) null else sxy / sqrt(sxx * syy)
    }

    /** Rangos con empates promediados. */
    fun ranks(xs: List<Double>): List<Double> {
        val order = xs.indices.sortedBy { xs[it] }
        val r = DoubleArray(xs.size)
        var i = 0
        while (i < order.size) {
            var j = i
            while (j + 1 < order.size && xs[order[j + 1]] == xs[order[i]]) j++
            val avg = (i + j) / 2.0 + 1.0
            for (k in i..j) r[order[k]] = avg
            i = j + 1
        }
        return r.toList()
    }

    /** Correlación de Spearman: adecuada para variables ordinales como el ánimo y la energía. */
    fun spearman(x: List<Double>, y: List<Double>): Double? = pearson(ranks(x), ranks(y))
}
