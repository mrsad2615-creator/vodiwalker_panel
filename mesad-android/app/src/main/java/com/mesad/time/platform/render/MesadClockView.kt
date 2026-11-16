package com.mesad.time.platform.render

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.time.ZonedDateTime
import java.time.ZoneId
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time three-dimensional MESAD instrument, rendered with OpenGL ES 3.0-compatible
 * geometry and a Cook-Torrance-inspired metallic lighting shader. This is real mesh geometry,
 * not a transformed bitmap. Filament artifacts remain available for a future device-specific
 * renderer swap; this portable GLES renderer works offline without binary model assets.
 */
class MesadClockView(context: Context) : GLSurfaceView(context) {
    private val clockRenderer = MesadGlRenderer()
    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(clockRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
    fun setEngine(engine: String) { queueEvent { clockRenderer.engine = engine } }
    fun setPerformanceMode(enabled: Boolean) { renderMode = if (enabled) RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY }
    fun setScale(scale: Float) { queueEvent { clockRenderer.scale = scale.coerceIn(.65f, 1.4f) } }
    fun setGlow(value: Float) { queueEvent { clockRenderer.glow = value.coerceIn(0f, 1f) } }
    fun setMaterial(value: String) { queueEvent { clockRenderer.finish = value } }
}

private class MesadGlRenderer : GLSurfaceView.Renderer {
    @Volatile var engine: String = "ORBIT"
    @Volatile var scale: Float = 1f
    @Volatile var glow: Float = .55f
    @Volatile var finish: String = "BRUSHED_TITANIUM"
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var colorHandle = 0
    private var glowHandle = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private lateinit var torus: FloatBuffer
    private lateinit var sphere: FloatBuffer
    private lateinit var wedge: FloatBuffer
    private var torusCount = 0
    private var sphereCount = 0
    private var wedgeCount = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(.025f, .035f, .05f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
            GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
            GLES20.glLinkProgram(p)
        }
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        glowHandle = GLES20.glGetUniformLocation(program, "uGlow")
        val t = torusMesh(majorRadius = .78f, minorRadius = .05f, majorSegments = 96, minorSegments = 12)
        torus = buffer(t.first); torusCount = t.second
        val s = sphereMesh(segments = 36, rings = 24)
        sphere = buffer(s.first); sphereCount = s.second
        val w = coneMesh(); wedge = buffer(w.first); wedgeCount = w.second
        Matrix.setLookAtM(view, 0, 0f, 0f, 3.7f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.frustumM(projection, 0, -1f, 1f, -height.toFloat() / width, height.toFloat() / width, 2f, 12f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val sec = now.second + now.nano / 1_000_000_000f
        val min = now.minute + sec / 60f
        val hour = now.hour % 12 + min / 60f
        val base = FloatArray(16)
        Matrix.setIdentityM(base, 0)
        Matrix.rotateM(base, 0, -7f, 1f, 0f, 0f)
        Matrix.rotateM(base, 0, (System.currentTimeMillis() % 120000L) / 120000f * 4f, 0f, 0f, 1f)
        Matrix.scaleM(base, 0, scale, scale, scale)

        val alloy = when (finish) { "CHAMPAGNE_GOLD" -> color(.90f,.72f,.48f); "MATTE_OBSIDIAN" -> color(.24f,.29f,.36f); "POLISHED_TITANIUM" -> color(.88f,.91f,.95f); else -> color(.66f,.72f,.80f) }
        drawMesh(torus, torusCount, base, alloy, .02f, 1.02f)
        val inner = copy(base); Matrix.scaleM(inner, 0, .78f, .78f, .78f)
        drawMesh(torus, torusCount, inner, color(.0f, .78f, .9f), glow * .42f, 1.02f)
        val core = copy(base); Matrix.scaleM(core, 0, .68f, .68f, .68f)
        Matrix.rotateM(core, 0, (System.currentTimeMillis() % 60000L) / 60000f * 5f, .4f, 1f, .2f)
        drawMesh(sphere, sphereCount, core, color(.035f, .14f, .23f), glow * .08f, 1f)
        if (engine == "NEXUS") {
            for (i in 0..2) {
                val orbit = copy(base)
                Matrix.rotateM(orbit, 0, (System.currentTimeMillis() % 180000L) / 180000f * 360f * (if (i == 1) -1f else 1f), 1f, .35f + i * .4f, .2f)
                Matrix.scaleM(orbit, 0, .9f + i * .1f, .9f + i * .1f, .9f + i * .1f)
                drawMesh(torus, torusCount, orbit, if (i == 1) color(.9f,.72f,.48f) else color(0f,.78f,.9f), glow * .55f, 1f)
            }
        } else if (engine == "CHRONOS") {
            for (i in 0..1) {
                val gear = copy(base); Matrix.scaleM(gear, 0, .89f - i * .12f, .89f - i * .12f, .89f - i * .12f)
                drawMesh(torus, torusCount, gear, if(i==0) color(.9f,.72f,.48f) else color(.55f,.61f,.69f), .025f, 1.0f)
            }
        }
        val wedgeM = copy(base); Matrix.translateM(wedgeM, 0, 0f, .91f, .05f); Matrix.scaleM(wedgeM, 0, .19f, .22f, .15f)
        drawMesh(wedge, wedgeCount, wedgeM, color(.9f,.72f,.48f), .06f, 1f)
        drawHand(base, hour * 30f, .37f, .075f, color(.79f,.83f,.88f), .01f)
        drawHand(base, min * 6f, .56f, .045f, color(.86f,.89f,.93f), .01f)
        drawHand(base, sec * 6f, .70f, .018f, color(0f,.82f,.95f), glow * .8f)
    }

    private fun drawHand(base: FloatArray, degrees: Float, length: Float, thickness: Float, c: FloatArray, emission: Float) {
        val hand = copy(base); Matrix.rotateM(hand, 0, -degrees, 0f, 0f, 1f); Matrix.translateM(hand, 0, 0f, length * .47f, .18f); Matrix.scaleM(hand, 0, thickness, length, .025f)
        drawMesh(sphere, sphereCount, hand, c, emission, .88f)
    }
    private fun drawMesh(data: FloatBuffer, count: Int, transform: FloatArray, color: FloatArray, emission: Float, roughness: Float) {
        Matrix.multiplyMM(mvp, 0, view, 0, transform, 0); Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0); GLES20.glUniformMatrix4fv(modelHandle, 1, false, transform, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0); GLES20.glUniform1f(glowHandle, emission + roughness * .08f)
        data.position(0); GLES20.glEnableVertexAttribArray(positionHandle); GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 24, data)
        data.position(3); GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 24, data)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
        GLES20.glDisableVertexAttribArray(positionHandle); GLES20.glDisableVertexAttribArray(normalHandle)
    }

    private fun shader(type: Int, source: String): Int = GLES20.glCreateShader(type).also { s ->
        GLES20.glShaderSource(s, source); GLES20.glCompileShader(s)
        val status = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        if(status[0] == 0) { val log=GLES20.glGetShaderInfoLog(s); GLES20.glDeleteShader(s); throw IllegalStateException(log) }
    }
    private fun copy(src: FloatArray) = FloatArray(16).also { System.arraycopy(src,0,it,0,16) }
    private fun color(r:Float,g:Float,b:Float)=floatArrayOf(r,g,b,1f)
    private fun buffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(values).apply { position(0) }

    private fun torusMesh(majorRadius:Float,minorRadius:Float,majorSegments:Int,minorSegments:Int):Pair<FloatArray,Int>{
        val out=ArrayList<Float>(majorSegments*minorSegments*36)
        fun vertex(u:Float,v:Float){val cu=cos(u);val su=sin(u);val cv=cos(v);val sv=sin(v);val x=(majorRadius+minorRadius*cv)*cu;val y=(majorRadius+minorRadius*cv)*su;val z=minorRadius*sv;val nx=cv*cu;val ny=cv*su;val nz=sv;out.addAll(listOf(x,y,z,nx,ny,nz))}
        for(i in 0 until majorSegments){val u0=i*2*Math.PI/majorSegments;val u1=(i+1)*2*Math.PI/majorSegments;for(j in 0 until minorSegments){val v0=j*2*Math.PI/minorSegments;val v1=(j+1)*2*Math.PI/minorSegments;vertex(u0.toFloat(),v0.toFloat());vertex(u1.toFloat(),v0.toFloat());vertex(u1.toFloat(),v1.toFloat());vertex(u0.toFloat(),v0.toFloat());vertex(u1.toFloat(),v1.toFloat());vertex(u0.toFloat(),v1.toFloat())}}
        return Pair(out.toFloatArray(),out.size/6)
    }
    private fun sphereMesh(segments:Int,rings:Int):Pair<FloatArray,Int>{val out=ArrayList<Float>();fun v(theta:Double,phi:Double){val x=(sin(theta)*cos(phi)).toFloat();val y=(cos(theta)).toFloat();val z=(sin(theta)*sin(phi)).toFloat();out.addAll(listOf(x,y,z,x,y,z))};for(r in 0 until rings){val t0=Math.PI*r/rings;val t1=Math.PI*(r+1)/rings;for(s in 0 until segments){val p0=2*Math.PI*s/segments;val p1=2*Math.PI*(s+1)/segments;v(t0,p0);v(t1,p0);v(t1,p1);v(t0,p0);v(t1,p1);v(t0,p1)}};return Pair(out.toFloatArray(),out.size/6)}
    private fun coneMesh():Pair<FloatArray,Int>{val out=ArrayList<Float>();for(i in 0 until 4){val a=i*Math.PI/2;val b=(i+1)*Math.PI/2;val x1=cos(a).toFloat();val z1=sin(a).toFloat();val x2=cos(b).toFloat();val z2=sin(b).toFloat();out.addAll(listOf(0f,1f,0f,0f,1f,0f,x1,-1f,z1,x1,0f,z1,x2,-1f,z2,x2,0f,z2))};return Pair(out.toFloatArray(),out.size/6)}

    companion object {
        private const val VERTEX_SHADER="""attribute vec3 aPosition; attribute vec3 aNormal; uniform mat4 uMvp; uniform mat4 uModel; varying vec3 vNormal; varying vec3 vPosition; void main(){vec4 p=uModel*vec4(aPosition,1.0);vPosition=p.xyz;vNormal=mat3(uModel)*aNormal;gl_Position=uMvp*vec4(aPosition,1.0);}"""
        private const val FRAGMENT_SHADER="""precision mediump float; uniform vec4 uColor; uniform float uGlow; varying vec3 vNormal; varying vec3 vPosition; void main(){vec3 n=normalize(vNormal);vec3 l1=normalize(vec3(0.45,0.7,0.8));vec3 l2=normalize(vec3(-0.7,-0.2,0.4));float diffuse=max(dot(n,l1),0.0)*0.72+max(dot(n,l2),0.0)*0.24;vec3 viewDir=normalize(-vPosition);vec3 h=normalize(l1+viewDir);float spec=pow(max(dot(n,h),0.0),24.0)*0.42;float fres=pow(1.0-max(dot(n,viewDir),0.0),3.0);vec3 col=uColor.rgb*(0.2+diffuse)+vec3(0.76,0.86,0.96)*spec+uColor.rgb*(fres*0.32+uGlow*0.4);gl_FragColor=vec4(col,uColor.a);}"""
    }
}
