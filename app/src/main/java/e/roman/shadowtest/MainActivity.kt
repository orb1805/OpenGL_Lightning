package e.roman.shadowtest

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout


class MainActivity : Activity() {

    private lateinit var layout: LinearLayout
    private lateinit var approxRateET: EditText
    private lateinit var applyBtn: Button
    private var first = true

    private lateinit var mGLSurfaceView: GLSurfaceView
    private var mPtrCount = 0
    @SuppressLint("ClickableViewAccessibility")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        layout = findViewById(R.id.layout)
        approxRateET = findViewById(R.id.approxrate_et)
        applyBtn = findViewById(R.id.apply_btn)
        applyBtn.setOnClickListener {
            if (approxRateET.text.isNotEmpty()) {
                if (!first) {
                    layout.removeView(mGLSurfaceView)
                    Log.d("test1", "remove")
                }
                first = false
                val renderer = Renderer(approxRateET.text.toString().toInt())
                mGLSurfaceView = GLSurfaceView(this)

                // Check if the system supports OpenGL ES 2.0.
                /*val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val configurationInfo = activityManager.deviceConfigurationInfo
            val supportsEs2 = configurationInfo.reqGlEsVersion >= 0x20000
            if (supportsEs2) {
                // Request an OpenGL ES 2.0 compatible context.
                mGLSurfaceView.setEGLContextClientVersion(2)

                // Set the renderer to our demo renderer, defined below.
                mGLSurfaceView.setRenderer(renderer)
            } else {
                // This is where you could create an OpenGL ES 1.x compatible
                // renderer if you wanted to support both ES 1 and ES 2.
                return
            }*/
                mGLSurfaceView.setEGLContextClientVersion(2)
                mGLSurfaceView.setRenderer(renderer)
                var previousX = 0f
                var previousY = 0f
                val mScaleDetector = ScaleGestureDetector(this, MyPinchListener(renderer))
                mGLSurfaceView.setOnTouchListener { view: View, e: MotionEvent ->
                    when (e.action and MotionEvent.ACTION_MASK) {
                        MotionEvent.ACTION_POINTER_DOWN -> mPtrCount++
                        MotionEvent.ACTION_POINTER_UP -> mPtrCount--
                        MotionEvent.ACTION_DOWN -> mPtrCount++
                        MotionEvent.ACTION_UP -> mPtrCount--
                    }
                    val x: Float = e.x
                    val y: Float = e.y

                    if (mPtrCount > 1)
                        mScaleDetector.onTouchEvent(e)
                    else {
                        when (e.action) {
                            MotionEvent.ACTION_MOVE -> {
                                var dx: Float = x - previousX
                                var dy: Float = y - previousY

                                renderer.angleX += dx * 180.0f / 320f / 2
                                renderer.angleY += dy * 180.0f / 320f / 2
                            }
                        }
                        previousX = x
                        previousY = y
                    }

                    return@setOnTouchListener true
                }
                //setContentView(mGLSurfaceView)
                layout.addView(mGLSurfaceView)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        //mGLSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        //mGLSurfaceView.onPause()
    }
}