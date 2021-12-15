package it.drawit.streckbryggan

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class PulseIndicator() {
    enum class PulseDir {
        GROW,
        SHRINK,
        NEUTRAL,
    }

    enum class PulseRing {
        Inner,
        Outer,
    }

    private lateinit var viewOuter: View
    private lateinit var viewInner: View
    private var started: Boolean = false

    private var runningAnims: ConcurrentMap<Pair<PulseRing, PulseDir>, Pair<ObjectAnimator, ObjectAnimator>> =
        ConcurrentHashMap()

    constructor(context: AppCompatActivity) : this() {
        viewOuter = context.findViewById(R.id.poll_indicator_outer)
        viewInner = context.findViewById(R.id.poll_indicator_inner)

        playAnim(PulseRing.Inner, PulseDir.NEUTRAL)
        playAnim(PulseRing.Outer, PulseDir.NEUTRAL)
    }

    private fun playAnim(ring: PulseRing, dir: PulseDir) {
        val view = when (ring) {
            PulseRing.Outer -> viewOuter
            PulseRing.Inner -> viewInner
        }

        val target = animTarget(ring, dir)

        val animX = ObjectAnimator.ofFloat(view, "scaleX", target)
        val animY = ObjectAnimator.ofFloat(view, "scaleY", target)
        animX.duration = animDuration(dir)
        animY.duration = animDuration(dir)
        animX.repeatCount = 0
        animY.repeatCount = 0
        animX.addListener(AnimatorEndListener { onAnimEnd(ring, dir) })
        animX.start()
        animY.start()

        runningAnims[Pair(ring, dir)] = Pair(animX, animY)
    }

    private fun animTarget(ring: PulseRing, dir: PulseDir): Float {
        return when (Pair(ring, dir)) {
            Pair(PulseRing.Inner, PulseDir.GROW) -> 0.4f
            Pair(PulseRing.Inner, PulseDir.SHRINK) -> 0.25f
            Pair(PulseRing.Inner, PulseDir.NEUTRAL) -> 0.5f
            Pair(PulseRing.Outer, PulseDir.GROW) -> 0.8f
            Pair(PulseRing.Outer, PulseDir.SHRINK) -> 0.75f
            Pair(PulseRing.Outer, PulseDir.NEUTRAL) -> 0.4f
            else -> 0.0f /* unreachable */
        }
    }

    private fun animDuration(dir: PulseDir): Long {
        return if (dir == PulseDir.NEUTRAL) {
            200
        } else {
            2000
        }
    }

    private fun onAnimEnd(ring: PulseRing, dir: PulseDir) {
        runningAnims.remove(Pair(ring, dir))

        if (started) {
            when (dir) {
                PulseDir.SHRINK -> playAnim(ring, PulseDir.GROW)
                PulseDir.GROW -> playAnim(ring, PulseDir.SHRINK)
                else -> {
                }
            }
        }
    }

    private fun stopAllAnims() {
        runningAnims.iterator()
            .forEach { animXY -> animXY.value.first.cancel(); animXY.value.second.cancel() }
        runningAnims.clear()
    }

    fun start() {
        started = true
        stopAllAnims()
        playAnim(PulseRing.Inner, PulseDir.SHRINK)
        playAnim(PulseRing.Outer, PulseDir.GROW)
    }

    fun stop() {
        started = false
        stopAllAnims()
        playAnim(PulseRing.Inner, PulseDir.NEUTRAL)
        playAnim(PulseRing.Outer, PulseDir.NEUTRAL)
    }

    data class AnimatorEndListener(val callback: () -> Unit) : Animator.AnimatorListener {

        override fun onAnimationEnd(animation: Animator?) {
            callback()
        }

        override fun onAnimationStart(animation: Animator?) {}

        override fun onAnimationCancel(animation: Animator?) {}

        override fun onAnimationRepeat(animation: Animator?) {}
    }
}