package it.drawit.streckbryggan

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread


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

    private lateinit var outerRing: View
    private lateinit var innerRing: View

    private var animator: SyncAnimator = SyncAnimator()

    constructor(context: AppCompatActivity) : this() {
        outerRing = context.findViewById(R.id.poll_indicator_outer)
        innerRing = context.findViewById(R.id.poll_indicator_inner)

        neutral()
    }

    private fun breatheIn() {
        val animations = ArrayList<Animator>()
        prepareAnimation(animations, PulseRing.Inner, PulseDir.SHRINK)
        prepareAnimation(animations, PulseRing.Outer, PulseDir.GROW)
        animator.play(animations, animDuration(PulseDir.GROW)) { breatheOut() }
    }

    private fun breatheOut() {
        val animations = ArrayList<Animator>()
        prepareAnimation(animations, PulseRing.Inner, PulseDir.GROW)
        prepareAnimation(animations, PulseRing.Outer, PulseDir.SHRINK)
        animator.play(animations, animDuration(PulseDir.GROW)) { breatheIn() }

    }

    private fun neutral() {
        val animations = ArrayList<Animator>()
        prepareAnimation(animations, PulseRing.Inner, PulseDir.NEUTRAL)
        prepareAnimation(animations, PulseRing.Outer, PulseDir.NEUTRAL)
        animator.play(animations, animDuration(PulseDir.NEUTRAL))
    }

    private fun prepareAnimation(
        animationSet: MutableList<Animator>,
        ring: PulseRing,
        dir: PulseDir
    ) {
        val view = when (ring) {
            PulseRing.Outer -> outerRing
            PulseRing.Inner -> innerRing
        }

        val target = animTarget(ring, dir)

        val animX = ObjectAnimator.ofFloat(view, "scaleX", target)
        val animY = ObjectAnimator.ofFloat(view, "scaleY", target)
        animX.duration = animDuration(dir)
        animY.duration = animDuration(dir)
        animX.repeatCount = 0
        animY.repeatCount = 0
        animationSet.add(animX)
        animationSet.add(animY)
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

    fun start() {
        breatheIn()
    }

    fun stop() {
        neutral()
    }

    /** Play a set of animations all together **/
    private class SyncAnimator {
        private var animator: AnimatorSet = AnimatorSet()
        private var callbackJob: Job? = null

        fun play(animations: List<Animator>, duration: Long, callback: (() -> Unit)? = null) {
            runOnUiThread {
                stop()
                animator.playTogether(animations)
                callback?.let {
                    callbackJob = GlobalScope.launch {
                        delay(timeMillis = duration)
                        it()
                    }
                }
                animator.start()
            }
        }

        fun stop() {
            runOnUiThread {
                callbackJob?.cancel()
                animator.cancel()
                animator = AnimatorSet()
            }
        }
    }
}