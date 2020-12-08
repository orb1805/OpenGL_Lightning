package e.roman.shadowtest

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class Renderer(approxRate: Int) : GLSurfaceView.Renderer {
    @Volatile
    var angleX: Float = 0f
    @Volatile
    var angleY: Float = 0f
    @Volatile
    var pinchSize: Float = 1f

    private val mModelMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mProjectionMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)
    private val mLightModelMatrix = FloatArray(16)
    private var sectorBottom: MutableList<FloatBuffer>
    private var sectorBottomColor: MutableList<FloatBuffer>
    private var sectorBottomNormal: MutableList<FloatBuffer>
    private var sectorCeiling: MutableList<FloatBuffer>
    private var sectorCeilingColor: MutableList<FloatBuffer>
    private var sectorCeilingNormal: MutableList<FloatBuffer>
    private var side: MutableList<FloatBuffer>
    private var sideColor: MutableList<FloatBuffer>
    private var sideNormal: MutableList<FloatBuffer>
    /*private val mCubePositions: FloatBuffer
    private val mCubeColors: FloatBuffer
    private val mCubeNormals: FloatBuffer*/
    private val transX = -0.6f
    private val transZ = 0.6f
    //var approxRate = 40
    private var mMVPMatrixHandle = 0
    private var mMVMatrixHandle = 0
    private var mLightPosHandle = 0
    private var mPositionHandle = 0
    private var mColorHandle = 0
    private var mNormalHandle = 0
    private val mBytesPerFloat = 4
    private val mPositionDataSize = 3
    private val mColorDataSize = 4
    private val mNormalDataSize = 3
    private val mLightPosInModelSpace = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
    private val mLightPosInWorldSpace = FloatArray(4)
    private val mLightPosInEyeSpace = FloatArray(4)
    private var mPerVertexProgramHandle = 0
    private var mPointProgramHandle = 0
    private val vertexShader =
        """uniform mat4 u_MVPMatrix;      
uniform mat4 u_MVMatrix;       
uniform vec3 u_LightPos;       
attribute vec4 a_Position;     
attribute vec4 a_Color;        
attribute vec3 a_Normal;       
varying vec4 v_Color;          
void main()                    
{                              
   vec3 modelViewVertex = vec3(u_MVMatrix * a_Position);              
   vec3 modelViewNormal = vec3(u_MVMatrix * vec4(a_Normal, 0.0));     
   float distance = length(u_LightPos - modelViewVertex);             
   vec3 lightVector = normalize(u_LightPos - modelViewVertex);        
   float diffuse = max(dot(modelViewNormal, lightVector), 0.5);       
   diffuse = diffuse * (1.0 / (1.0 + (0.25 * distance * distance)));  
   v_Color = a_Color * diffuse;                                       
   gl_Position = u_MVPMatrix * a_Position;                            
}                                                                     
"""
    protected fun getFragmentShader(): String {
        return """precision mediump float;       
varying vec4 v_Color;          
void main()                    
{                              
   gl_FragColor = v_Color;     
}                              
"""
    }

    override fun onSurfaceCreated(glUnused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val eyeX = 0.0f
        val eyeY = 0.0f
        val eyeZ = -0.5f
        val lookX = 0.0f
        val lookY = 0.0f
        val lookZ = -5.0f
        val upX = 0.0f
        val upY = 1.0f
        val upZ = 0.0f
        Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ)
        val vertexShader = vertexShader
        val fragmentShader = getFragmentShader()
        val vertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        mPerVertexProgramHandle = createAndLinkProgram(
            vertexShaderHandle,
            fragmentShaderHandle,
            arrayOf("a_Position", "a_Color", "a_Normal")
        )
        val pointVertexShader = """uniform mat4 u_MVPMatrix;      
attribute vec4 a_Position;     
void main()                    
{                              
   gl_Position = u_MVPMatrix   
               * a_Position;   
   gl_PointSize = 5.0;         
}                              
"""
        val pointFragmentShader = """precision mediump float;       
void main()                    
{                              
   gl_FragColor = vec4(1.0,    
   1.0, 1.0, 1.0);             
}                              
"""
        val pointVertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, pointVertexShader)
        val pointFragmentShaderHandle =
            compileShader(GLES20.GL_FRAGMENT_SHADER, pointFragmentShader)
        mPointProgramHandle = createAndLinkProgram(
            pointVertexShaderHandle,
            pointFragmentShaderHandle,
            arrayOf("a_Position")
        )
    }

    override fun onSurfaceChanged(glUnused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        val left = -ratio
        val bottom = -1.0f
        val top = 1.0f
        val near = 1.0f
        val far = 10.0f
        Matrix.frustumM(mProjectionMatrix, 0, left, ratio, bottom, top, near, far)
    }

    override fun onDrawFrame(glUnused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val time = SystemClock.uptimeMillis() % 10000L
        val angleInDegrees = 360.0f / 10000.0f * time.toInt()
        GLES20.glUseProgram(mPerVertexProgramHandle)
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_MVPMatrix")
        mMVMatrixHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_MVMatrix")
        mLightPosHandle = GLES20.glGetUniformLocation(mPerVertexProgramHandle, "u_LightPos")
        mPositionHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_Position")
        mColorHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_Color")
        mNormalHandle = GLES20.glGetAttribLocation(mPerVertexProgramHandle, "a_Normal")
        Matrix.setIdentityM(mLightModelMatrix, 0)
        Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, -5.0f)
        //Matrix.rotateM(mLightModelMatrix, 0, angleInDegrees, 0.0f, 1.0f, 0.0f)
        Matrix.translateM(mLightModelMatrix, 0, 0.0f, 0.0f, 2.0f)
        Matrix.multiplyMV(mLightPosInWorldSpace, 0, mLightModelMatrix, 0, mLightPosInModelSpace, 0)
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mViewMatrix, 0, mLightPosInWorldSpace, 0)

        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, -5.0f)
        Matrix.rotateM(mModelMatrix, 0, angleY, 1.0f, 0.0f, 0.0f)
        Matrix.rotateM(mModelMatrix, 0, angleX, 0.0f, 1.0f, 0.0f)
        Matrix.scaleM(mModelMatrix, 0, pinchSize, pinchSize, pinchSize)
        for (i in sectorBottom.indices)
            drawCube(sectorBottom[i], sectorBottomColor[i], sectorBottomNormal[i])
        for (i in sectorCeiling.indices)
            drawCube(sectorCeiling[i], sectorCeilingColor[i], sectorCeilingNormal[i])
        for (i in side.indices)
            drawCube(side[i], sideColor[i], sideNormal[i])
        //drawCube(mCubePositions)

        GLES20.glUseProgram(mPointProgramHandle)
        drawLight()
    }

    /**
     * Draws a cube.
     */
    private fun drawCube(mCubePositions: FloatBuffer, mCubeColors: FloatBuffer, mCubeNormals: FloatBuffer) {
        mCubePositions.position(0)
        GLES20.glVertexAttribPointer(
            mPositionHandle, mPositionDataSize, GLES20.GL_FLOAT, false,
            0, mCubePositions
        )
        GLES20.glEnableVertexAttribArray(mPositionHandle)
        mCubeColors.position(0)
        GLES20.glVertexAttribPointer(
            mColorHandle, mColorDataSize, GLES20.GL_FLOAT, false,
            0, mCubeColors
        )
        GLES20.glEnableVertexAttribArray(mColorHandle)
        mCubeNormals.position(0)
        GLES20.glVertexAttribPointer(
            mNormalHandle, mNormalDataSize, GLES20.GL_FLOAT, false,
            0, mCubeNormals
        )
        GLES20.glEnableVertexAttribArray(mNormalHandle)
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, mMVPMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniform3f(
            mLightPosHandle,
            mLightPosInEyeSpace[0], mLightPosInEyeSpace[1], mLightPosInEyeSpace[2]
        )
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    private fun drawLight() {
        val pointMVPMatrixHandle = GLES20.glGetUniformLocation(mPointProgramHandle, "u_MVPMatrix")
        val pointPositionHandle = GLES20.glGetAttribLocation(mPointProgramHandle, "a_Position")
        GLES20.glVertexAttrib3f(
            pointPositionHandle,
            mLightPosInModelSpace[0], mLightPosInModelSpace[1], mLightPosInModelSpace[2]
        )
        GLES20.glDisableVertexAttribArray(pointPositionHandle)
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mLightModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(pointMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
    }

    private fun compileShader(shaderType: Int, shaderSource: String): Int {
        var shaderHandle = GLES20.glCreateShader(shaderType)
        if (shaderHandle != 0) {
            GLES20.glShaderSource(shaderHandle, shaderSource)
            GLES20.glCompileShader(shaderHandle)
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shaderHandle))
                GLES20.glDeleteShader(shaderHandle)
                shaderHandle = 0
            }
        }
        if (shaderHandle == 0) {
            throw RuntimeException("Error creating shader.")
        }
        return shaderHandle
    }

    private fun createAndLinkProgram(
        vertexShaderHandle: Int,
        fragmentShaderHandle: Int,
        attributes: Array<String>?
    ): Int {
        var programHandle = GLES20.glCreateProgram()
        if (programHandle != 0) {
            GLES20.glAttachShader(programHandle, vertexShaderHandle)
            GLES20.glAttachShader(programHandle, fragmentShaderHandle)
            if (attributes != null) {
                val size = attributes.size
                for (i in 0 until size) {
                    GLES20.glBindAttribLocation(programHandle, i, attributes[i])
                }
            }
            GLES20.glLinkProgram(programHandle)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Error compiling program: " + GLES20.glGetProgramInfoLog(programHandle))
                GLES20.glDeleteProgram(programHandle)
                programHandle = 0
            }
        }
        if (programHandle == 0) {
            throw RuntimeException("Error creating program.")
        }
        return programHandle
    }

    companion object {
        private const val TAG = "LessonTwoRenderer"
    }

    init {
        sectorBottom = mutableListOf()
        sectorBottomColor = mutableListOf()
        sectorBottomNormal = mutableListOf()
        sectorCeiling = mutableListOf()
        sectorCeilingColor = mutableListOf()
        sectorCeilingNormal = mutableListOf()
        side = mutableListOf()
        sideColor = mutableListOf()
        sideNormal = mutableListOf()
        var x2 = 0.8f
        var x = 0f
        var dx = (x2 - x) / approxRate
        var cubePositionData: FloatArray
        var cubeColorData: FloatArray
        var cubeNormalData: FloatArray
        while (x <= x2) {
            cubePositionData =
                floatArrayOf(
                    2 * x + transX, 2 * 0.5f, -2 * y(x) + transZ,
                    2 * (x + dx) + transX, 2 * 0.5f, -2 * y(x + dx) + transZ,
                    transX, 2 * 0.5f, -2 * 0.4f + transZ
                )
            cubeColorData = floatArrayOf(
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
            )
            cubeNormalData = floatArrayOf(
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f
            )
            sectorBottom.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorBottom.last().put(cubePositionData).position(0)
            sectorBottomColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorBottomColor.last().put(cubeColorData).position(0)
            sectorBottomNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorBottomNormal.last().put(cubeNormalData).position(0)
            cubePositionData =
                floatArrayOf(
                    2 * (x + dx) + transX, -2 * 0.5f, -2 * y(x + dx) + transZ,
                    2 * x + transX, -2 * 0.5f, -2 * y(x) + transZ,
                    transX, -2 * 0.5f, -2 * 0.4f + transZ
                )
            cubeColorData = floatArrayOf(
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
            )

            cubeNormalData = floatArrayOf(
                0.0f, -1.0f, 0.0f,
                0.0f, -1.0f, 0.0f,
                0.0f, -1.0f, 0.0f
            )
            sectorCeiling.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorCeiling.last().put(cubePositionData).position(0)
            sectorCeilingColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorCeilingColor.last().put(cubeColorData).position(0)
            sectorCeilingNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorCeilingNormal.last().put(cubeNormalData).position(0)
            cubePositionData =
                floatArrayOf(
                    2 * (x + dx) + transX, 2 * 0.5f, -2 * y(x + dx) + transZ,
                    2 * x + transX, 2 * 0.5f, -2 * y(x) + transZ,
                    2 * x + transX, -2 * 0.5f, -2 * y(x) + transZ
                )
            cubeColorData = floatArrayOf(
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
            )

            cubeNormalData = floatArrayOf(
                2 * (x + dx) - 2 * x, 0.0f, -2 * y(x) + 2 * y(x + dx),
                2 * (x + dx) - 2 * x, 0.0f, -2 * y(x) + 2 * y(x + dx),
                2 * (x + dx) - 2 * x, 0.0f, -2 * y(x) + 2 * y(x + dx)
            )
            side.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            side.last().put(cubePositionData).position(0)
            sideColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sideColor.last().put(cubeColorData).position(0)
            sideNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sideNormal.last().put(cubeNormalData).position(0)
            cubePositionData =
                floatArrayOf(
                    2 * x + transX, -2 * 0.5f, -2 * y(x) + transZ,
                    2 * (x + dx) + transX, -2 * 0.5f, -2 * y(x + dx) + transZ,
                    2 * (x + dx) + transX, 2 * 0.5f, -2 * y(x + dx) + transZ,
                )
            cubeColorData = floatArrayOf(
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f
            )

            cubeNormalData = floatArrayOf(
                2 * (x + dx) - 2 * x, 0.0f, -2 * y(x) + 2 * y(x + dx),
                2 * (x + dx) - 2 * x, 0.0f, -2 * y(x) + 2 * y(x + dx),
                2 * (x + dx) - 2 * x, 0.0f, -2 * y(x) + 2 * y(x + dx)
            )
            side.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            side.last().put(cubePositionData).position(0)
            sideColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sideColor.last().put(cubeColorData).position(0)
            sideNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sideNormal.last().put(cubeNormalData).position(0)
            /*sectorCeiling.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorCeiling.last().put(cubePositionData).position(0)
            sectorCeilingColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorCeilingColor.last().put(cubeColorData).position(0)
            sectorCeilingNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
                .order(ByteOrder.nativeOrder()).asFloatBuffer())
            sectorCeilingNormal.last().put(cubeNormalData).position(0)*/
            x += dx
        }
        cubePositionData =
            floatArrayOf(
                transX, -2 * 0.5f, -2 * 0.4f + transZ,
                transX, 2 * 0.5f, -2 * 0.4f + transZ,
                2 * x + transX, 2 * 0.5f, -2 * y(x) + transZ,
            )
        cubeColorData = floatArrayOf(
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f
        )
        cubeNormalData = floatArrayOf(
            2 * x, 0.0f, -2 * 0.4f + 2 * y(x),
            2 * x, 0.0f, -2 * 0.4f + 2 * y(x),
            2 * x, 0.0f, -2 * 0.4f + 2 * y(x)
        )
        side.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        side.last().put(cubePositionData).position(0)
        sideColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        sideColor.last().put(cubeColorData).position(0)
        sideNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        sideNormal.last().put(cubeNormalData).position(0)
        cubePositionData =
            floatArrayOf(
                transX, -2 * 0.5f, -2 * 0.4f + transZ,
                2 * x + transX, 2 * 0.5f, -2 * y(x) + transZ,
                2 * x + transX, -2 * 0.5f, -2 * y(x) + transZ,
            )
        cubeColorData = floatArrayOf(
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f
        )
        cubeNormalData = floatArrayOf(
            2 * x, 0.0f, -2 * 0.4f + 2 * y(x),
            2 * x, 0.0f, -2 * 0.4f + 2 * y(x),
            2 * x, 0.0f, -2 * 0.4f + 2 * y(x)
        )
        side.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        side.last().put(cubePositionData).position(0)
        sideColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        sideColor.last().put(cubeColorData).position(0)
        sideNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        sideNormal.last().put(cubeNormalData).position(0)
        cubePositionData =
            floatArrayOf(
                transX, 2 * 0.5f, transZ,
                transX, 2 * 0.5f, -2 * 0.4f + transZ,
                transX, -2 * 0.5f, -2 * 0.4f + transZ
            )
        cubeColorData = floatArrayOf(
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f
        )
        cubeNormalData = floatArrayOf(
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f
        )
        side.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        side.last().put(cubePositionData).position(0)
        sideColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        sideColor.last().put(cubeColorData).position(0)
        sideNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        sideNormal.last().put(cubeNormalData).position(0)
        cubePositionData =
            floatArrayOf(
                transX, -2 * 0.5f, transZ,
                transX, 2 * 0.5f, transZ,
                transX, -2 * 0.5f, -2 * 0.4f + transZ
            )
        cubeColorData = floatArrayOf(
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f
        )
        cubeNormalData = floatArrayOf(
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f,
            0.0f, 0.0f, -1.0f
        )
        side.add(ByteBuffer.allocateDirect(cubePositionData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        side.last().put(cubePositionData).position(0)
        sideColor.add(ByteBuffer.allocateDirect(cubeColorData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        sideColor.last().put(cubeColorData).position(0)
        sideNormal.add(ByteBuffer.allocateDirect(cubeNormalData.size * mBytesPerFloat)
            .order(ByteOrder.nativeOrder()).asFloatBuffer())
        sideNormal.last().put(cubeNormalData).position(0)
    }

    private fun y(x: Float): Float{
        return x * x
    }
}