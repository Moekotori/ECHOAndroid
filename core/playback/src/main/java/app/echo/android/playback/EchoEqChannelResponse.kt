package app.echo.android.playback

import app.echo.android.model.playback.*
import kotlin.math.*

/** Static complex transfer in native slot order, including L/R and M/S phase interactions. */
internal object EchoEqChannelResponse {
    fun curves(filters: List<OpraEqBand>, preamp: Float, rate: Float, frequencies: List<Float>? = null): Map<EchoEqBandScope, List<EchoEqResponsePoint>> {
        val prepared = filters.filter { it.enabled }.map { it.scope to EchoBiquadMath.stages(it.atPcCurveRate(rate), rate) }
            .filter { (_, stages) -> stages.any { abs(it.b0 - it.a0) > 1e-12 || abs(it.b1-it.a1) > 1e-12 || abs(it.b2-it.a2) > 1e-12 } }
        val points = frequencies ?: List(160) { (20.0 * 1000.0.pow(it / 159.0)).toFloat() }
        val result = EchoEqBandScope.entries.associateWith { ArrayList<EchoEqResponsePoint>(points.size) }
        val matrix = DoubleArray(8); val next = DoubleArray(8); val stage = DoubleArray(8)
        repeat(points.size) { index ->
            val f = points[index]
            matrix.fill(0.0); matrix[0] = 1.0; matrix[6] = 1.0
            val w = 2 * PI * f / rate; val c1 = cos(w); val s1 = -sin(w); val c2 = cos(2*w); val s2 = -sin(2*w)
            for ((scope, coefficients) in prepared) {
                var hr = 1.0; var hi = 0.0
                for (c in coefficients) {
                    val nr=c.b0+c.b1*c1+c.b2*c2; val ni=c.b1*s1+c.b2*s2
                    val dr=c.a0+c.a1*c1+c.a2*c2; val di=c.a1*s1+c.a2*s2; val den=(dr*dr+di*di).coerceAtLeast(1e-24)
                    val r=(nr*dr+ni*di)/den; val im=(ni*dr-nr*di)/den
                    val real=hr*r-hi*im; hi=hr*im+hi*r; hr=real
                }
                stage.fill(0.0)
                when (scope) {
                    EchoEqBandScope.Stereo -> { stage[0]=hr; stage[1]=hi; stage[6]=hr; stage[7]=hi }
                    EchoEqBandScope.Left -> { stage[0]=hr; stage[1]=hi; stage[6]=1.0 }
                    EchoEqBandScope.Right -> { stage[0]=1.0; stage[6]=hr; stage[7]=hi }
                    else -> {
                        val sign=if(scope==EchoEqBandScope.Mid) 1 else -1
                        stage[0]=(1+hr)/2; stage[1]=hi/2; stage[6]=stage[0]; stage[7]=stage[1]
                        stage[2]=sign*(hr-1)/2; stage[3]=sign*hi/2; stage[4]=stage[2]; stage[5]=stage[3]
                    }
                }
                for (row in 0..1) for (col in 0..1) {
                    var re=0.0; var im=0.0
                    for(k in 0..1) { val a=(row*2+k)*2; val b=(k*2+col)*2
                        re+=stage[a]*matrix[b]-stage[a+1]*matrix[b+1]; im+=stage[a]*matrix[b+1]+stage[a+1]*matrix[b] }
                    val at=(row*2+col)*2; next[at]=re; next[at+1]=im
                }
                next.copyInto(matrix)
            }
            fun db(re:Double,im:Double)= (20*log10(hypot(re,im).coerceAtLeast(1e-9))).toFloat()+preamp
            result.getValue(EchoEqBandScope.Left)+=EchoEqResponsePoint(f, db(matrix[0],matrix[1]))
            result.getValue(EchoEqBandScope.Right)+=EchoEqResponsePoint(f, db(matrix[6],matrix[7]))
            result.getValue(EchoEqBandScope.Mid)+=EchoEqResponsePoint(f,db((matrix[0]+matrix[2]+matrix[4]+matrix[6])/2,(matrix[1]+matrix[3]+matrix[5]+matrix[7])/2))
            result.getValue(EchoEqBandScope.Side)+=EchoEqResponsePoint(f,db((matrix[0]-matrix[2]-matrix[4]+matrix[6])/2,(matrix[1]-matrix[3]-matrix[5]+matrix[7])/2))
            val rowL=hypot(matrix[0],matrix[1])+hypot(matrix[2],matrix[3])
            val rowR=hypot(matrix[4],matrix[5])+hypot(matrix[6],matrix[7])
            val trace = matrix.sumOf { it*it }
            val detR=matrix[0]*matrix[6]-matrix[1]*matrix[7]-matrix[2]*matrix[4]+matrix[3]*matrix[5]
            val detI=matrix[0]*matrix[7]+matrix[1]*matrix[6]-matrix[2]*matrix[5]-matrix[3]*matrix[4]
            val singular=sqrt((trace+sqrt((trace*trace-4*(detR*detR+detI*detI)).coerceAtLeast(0.0)))/2)
            result.getValue(EchoEqBandScope.Stereo)+=EchoEqResponsePoint(f,(20*log10(max(max(rowL,rowR),singular).coerceAtLeast(1e-9))).toFloat()+preamp)
        }
        return result
    }

    fun peakBoost(filters: List<OpraEqBand>, rate: Float): Float {
        if (filters.none { it.affectsFrequencyResponse() }) return 0f
        val frequencies = LinkedHashSet<Float>()
        repeat(513) { frequencies += (10.0 * (rate * .0499999).pow(it / 512.0)).toFloat() }
        filters.filter { it.enabled }.forEach { band ->
            val center=band.frequencyHz.coerceIn(20f,rate*.45f)
            for(i in -16..16) frequencies += (center*exp(i/(16.0*max(.1f,band.q?:1f)))).toFloat()
        }
        val static=curves(filters,0f,rate,frequencies.filter { it>=0 && it<rate/2 }).getValue(EchoEqBandScope.Stereo).maxOfOrNull { it.gainDb } ?: 0f
        return static.coerceAtLeast(0f)+6f*filters.count { it.enabled && it.dynamicMode==EchoEqDynamicMode.Up }.coerceAtMost(4)
    }
}

internal fun OpraEqBand.atPcCurveRate(rate: Float): OpraEqBand {
    val qLimit = if (normalizedType() == EchoEqFilterType.BandStop) 24f else 12f
    val normal = frequencyHz >= 20f && gainDb in -24f..12f && (q ?: 1f) in .1f..qLimit
    val ceiling = rate * if (normal) .45f else .49f
    return if (frequencyHz > ceiling) copy(frequencyHz = ceiling) else this
}
