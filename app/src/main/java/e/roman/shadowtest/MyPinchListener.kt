package e.roman.shadowtest

import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import kotlin.concurrent.fixedRateTimer

class MyPinchListener(private val renderer: Renderer) : SimpleOnScaleGestureListener() {

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        renderer.pinchSize *= detector.scaleFactor
        return true
    }
}