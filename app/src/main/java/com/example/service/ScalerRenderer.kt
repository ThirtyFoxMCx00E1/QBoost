package com.example.service

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The Upscaler and Frame gen engine (OpenGL ES 2.0), the same idea as Lossless Scaling on PC:
 * the game screen is captured, processed on the GPU and shown again on top of the game.
 *
 *  - Upscaler: contrast adaptive sharpening (the CAS algorithm of AMD FidelityFX).
 *  - Frame gen: every captured frame is followed by an in-between frame. Motion is estimated by
 *    block matching on a 1/8 size copy, then both neighbouring frames are warped and blended.
 *
 * Everything runs on its own thread. It cannot see inside the game, so it works on the finished image.
 */
class ScalerRenderer(
    private val outputSurface: Surface,
    private val outWidth: Int,
    private val outHeight: Int,
    private val inWidth: Int,
    private val inHeight: Int,
    private val listener: Listener
) {
    interface Listener {
        fun onFirstFrame()
        fun onError(message: String)
    }

    @Volatile
    var upscalerOn = false

    @Volatile
    var frameGenOn = false

    @Volatile
    var sharpness = 0.6f

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var inputSurface: Surface? = null

    @Volatile
    private var stopped = false
    private var reportedFirstFrame = false

    // ---- EGL ----
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    // ---- GL objects ----
    private var oesTexture = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val frameTex = IntArray(2)
    private val frameFbo = IntArray(2)
    private var interpTex = 0
    private var interpFbo = 0
    private var flowTex = 0
    private var flowFbo = 0
    private var flowWidth = 1
    private var flowHeight = 1

    private var copyProgram: GlProgram? = null
    private var casProgram: GlProgram? = null
    private var flowProgram: GlProgram? = null
    private var interpProgram: GlProgram? = null
    private var frameGenSupported = false

    private var currentIndex = 0
    private var framesSeen = 0
    private var lastFrameNanos = 0L
    private var frameIntervalMs = 33.0
    private var showRunnable: Runnable? = null

    private val stMatrix = FloatArray(16)
    private val identity = FloatArray(16).also {
        it[0] = 1f
        it[5] = 1f
        it[10] = 1f
        it[15] = 1f
    }

    private val posBuffer: FloatBuffer = floatBufferOf(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val uvBuffer: FloatBuffer = floatBufferOf(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f))

    /** Starts the render thread. Returns the Surface the screen capture must draw into, or null on failure. */
    fun start(): Surface? {
        val t = HandlerThread("qboost-scaler")
        t.start()
        thread = t
        val h = Handler(t.looper)
        handler = h

        val latch = CountDownLatch(1)
        var failed = false
        h.post {
            try {
                initGl()
            } catch (e: Throwable) {
                failed = true
                listener.onError("GL setup failed: ${e.message}")
            }
            latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
        if (failed) {
            stop()
            return null
        }
        return inputSurface
    }

    fun stop() {
        if (stopped) return
        stopped = true
        val h = handler
        val t = thread
        if (h != null && t != null) {
            h.removeCallbacksAndMessages(null)
            h.post {
                releaseGl()
                t.quitSafely()
            }
        }
    }

    // ============================================================================================
    //  Setup / teardown
    // ============================================================================================

    private fun initGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0] ?: error("no EGL config")

        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            config,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0
        )
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, outputSurface, intArrayOf(EGL14.EGL_NONE), 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "no EGL window surface" }
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent failed" }
        EGL14.eglSwapInterval(eglDisplay, 1)

        copyProgram = GlProgram(VERTEX_SHADER, FRAGMENT_COPY_OES)
        casProgram = GlProgram(VERTEX_SHADER, FRAGMENT_CAS)
        try {
            flowProgram = GlProgram(VERTEX_SHADER, FRAGMENT_FLOW)
            interpProgram = GlProgram(VERTEX_SHADER, FRAGMENT_INTERP)
            frameGenSupported = true
        } catch (_: Throwable) {
            // Frame gen shaders not supported by this GPU driver: the upscaler still works
            flowProgram = null
            interpProgram = null
            frameGenSupported = false
        }

        // external texture that receives the captured screen
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        oesTexture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // two frames (previous + current), the in-between frame, and the small motion map
        for (i in 0..1) {
            frameTex[i] = createTexture2D(inWidth, inHeight)
            frameFbo[i] = createFbo(frameTex[i])
        }
        if (frameGenSupported) {
            interpTex = createTexture2D(inWidth, inHeight)
            interpFbo = createFbo(interpTex)
            flowWidth = maxOf(1, inWidth / 8)
            flowHeight = maxOf(1, inHeight / 8)
            flowTex = createTexture2D(flowWidth, flowHeight)
            flowFbo = createFbo(flowTex)
        }

        val st = SurfaceTexture(oesTexture)
        st.setDefaultBufferSize(inWidth, inHeight)
        st.setOnFrameAvailableListener(SurfaceTexture.OnFrameAvailableListener { onSourceFrame() }, handler)
        surfaceTexture = st
        inputSurface = Surface(st)
    }

    private fun releaseGl() {
        try {
            showRunnable?.let { handler?.removeCallbacks(it) }
            surfaceTexture?.setOnFrameAvailableListener(null)
            surfaceTexture?.release()
            inputSurface?.release()
            surfaceTexture = null
            inputSurface = null
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (_: Throwable) {
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    // ============================================================================================
    //  Per captured frame
    // ============================================================================================

    private fun onSourceFrame() {
        if (stopped) return
        try {
            val st = surfaceTexture ?: return
            val now = System.nanoTime()
            if (lastFrameNanos != 0L) {
                val dtMs = (now - lastFrameNanos) / 1_000_000.0
                frameIntervalMs = frameIntervalMs * 0.8 + dtMs.coerceIn(4.0, 200.0) * 0.2
            }
            lastFrameNanos = now

            // a newer frame arrived: forget the pending "show the real frame" of the previous one
            showRunnable?.let { handler?.removeCallbacks(it) }
            showRunnable = null

            st.updateTexImage()
            st.getTransformMatrix(stMatrix)

            val previousIndex = currentIndex
            currentIndex = 1 - currentIndex
            drawCopy(currentIndex)
            framesSeen++

            // Frame gen only makes sense while the game runs slower than about 80 fps
            val doFrameGen = frameGenOn && frameGenSupported && framesSeen >= 2 && frameIntervalMs > 12.0
            if (doFrameGen) {
                drawFlow(previousIndex, currentIndex)
                drawInterpolated(previousIndex, currentIndex)
                present(interpTex)

                val realIndex = currentIndex
                val show = Runnable {
                    if (!stopped) {
                        try {
                            present(frameTex[realIndex])
                        } catch (e: Throwable) {
                            listener.onError("Present failed: ${e.message}")
                        }
                    }
                }
                showRunnable = show
                handler?.postDelayed(show, (frameIntervalMs / 2.0).toLong().coerceIn(2L, 40L))
            } else {
                present(frameTex[currentIndex])
            }

            if (!reportedFirstFrame) {
                reportedFirstFrame = true
                listener.onFirstFrame()
            }
        } catch (e: Throwable) {
            listener.onError("Render failed: ${e.message}")
        }
    }

    /** Captured screen (external texture) -> ordinary texture [index]. */
    private fun drawCopy(index: Int) {
        val p = copyProgram ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameFbo[index])
        GLES20.glViewport(0, 0, inWidth, inHeight)
        GLES20.glUseProgram(p.id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniform1i(p.u("uTex"), 0)
        drawQuad(p, stMatrix)
    }

    /** Block matching on a 1/8 size grid: where did every block move between the two frames? */
    private fun drawFlow(previousIndex: Int, currentIndex: Int) {
        val p = flowProgram ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, flowFbo)
        GLES20.glViewport(0, 0, flowWidth, flowHeight)
        GLES20.glUseProgram(p.id)
        bindTexture2D(0, frameTex[previousIndex])
        bindTexture2D(1, frameTex[currentIndex])
        GLES20.glUniform1i(p.u("uPrev"), 0)
        GLES20.glUniform1i(p.u("uCur"), 1)
        GLES20.glUniform2f(p.u("uStep"), 8f / inWidth, 8f / inHeight)
        GLES20.glUniform2f(p.u("uPatch"), 4f / inWidth, 4f / inHeight)
        drawQuad(p, identity)
    }

    /** The in-between frame: both neighbours moved half way along the motion and blended. */
    private fun drawInterpolated(previousIndex: Int, currentIndex: Int) {
        val p = interpProgram ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, interpFbo)
        GLES20.glViewport(0, 0, inWidth, inHeight)
        GLES20.glUseProgram(p.id)
        bindTexture2D(0, frameTex[previousIndex])
        bindTexture2D(1, frameTex[currentIndex])
        bindTexture2D(2, flowTex)
        GLES20.glUniform1i(p.u("uPrev"), 0)
        GLES20.glUniform1i(p.u("uCur"), 1)
        GLES20.glUniform1i(p.u("uFlow"), 2)
        GLES20.glUniform2f(p.u("uStep"), 8f / inWidth, 8f / inHeight)
        drawQuad(p, identity)
    }

    /** Puts a texture on the screen overlay, sharpened when the Upscaler is on. */
    private fun present(texture: Int) {
        val p = casProgram ?: return
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, outWidth, outHeight)
        GLES20.glUseProgram(p.id)
        bindTexture2D(0, texture)
        GLES20.glUniform1i(p.u("uTex"), 0)
        GLES20.glUniform2f(p.u("uTexel"), 1f / inWidth, 1f / inHeight)
        GLES20.glUniform1f(p.u("uSharp"), if (upscalerOn) sharpness else 0f)
        drawQuad(p, identity)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    // ============================================================================================
    //  GL helpers
    // ============================================================================================

    private fun bindTexture2D(unit: Int, texture: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
    }

    private fun drawQuad(p: GlProgram, textureMatrix: FloatArray) {
        GLES20.glUniformMatrix4fv(p.u("uSt"), 1, false, textureMatrix, 0)
        val aPos = p.attr("aPos")
        val aUv = p.attr("aUv")
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, posBuffer)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 8, uvBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
    }

    private fun createTexture2D(width: Int, height: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        return ids[0]
    }

    private fun createFbo(texture: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenFramebuffers(1, ids, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, ids[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) { "framebuffer incomplete: $status" }
        return ids[0]
    }

    private fun floatBufferOf(data: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(data)
        buffer.position(0)
        return buffer
    }

    /** Compiled + linked shader program with cached attribute / uniform locations. */
    private class GlProgram(vertexSource: String, fragmentSource: String) {
        val id: Int
        private val locations = HashMap<String, Int>()

        init {
            val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw IllegalStateException("link failed: $log")
            }
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            id = program
        }

        fun attr(name: String): Int = GLES20.glGetAttribLocation(id, name)

        fun u(name: String): Int = locations.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }

        private fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw IllegalStateException("shader failed: $log")
            }
            return shader
        }
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec4 aPos;
            attribute vec2 aUv;
            uniform mat4 uSt;
            varying vec2 vUv;
            void main() {
                gl_Position = aPos;
                vUv = (uSt * vec4(aUv, 0.0, 1.0)).xy;
            }
        """

        const val FRAGMENT_COPY_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vUv;
            uniform samplerExternalOES uTex;
            void main() {
                gl_FragColor = texture2D(uTex, vUv);
            }
        """

        // AMD FidelityFX CAS (contrast adaptive sharpening), 5-tap version
        const val FRAGMENT_CAS = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            uniform vec2 uTexel;
            uniform float uSharp;
            void main() {
                vec3 e = texture2D(uTex, vUv).rgb;
                if (uSharp <= 0.001) {
                    gl_FragColor = vec4(e, 1.0);
                    return;
                }
                vec3 b = texture2D(uTex, vUv + vec2(0.0, -uTexel.y)).rgb;
                vec3 d = texture2D(uTex, vUv + vec2(-uTexel.x, 0.0)).rgb;
                vec3 f = texture2D(uTex, vUv + vec2(uTexel.x, 0.0)).rgb;
                vec3 h = texture2D(uTex, vUv + vec2(0.0, uTexel.y)).rgb;
                vec3 mn = min(min(min(d, e), min(f, b)), h);
                vec3 mx = max(max(max(d, e), max(f, b)), h);
                vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0));
                float peak = -1.0 / mix(8.0, 5.0, uSharp);
                vec3 w = amp * peak;
                vec3 rcpW = 1.0 / (1.0 + 4.0 * w);
                vec3 col = clamp((w * (b + d + f + h) + e) * rcpW, 0.0, 1.0);
                gl_FragColor = vec4(col, 1.0);
            }
        """

        // Block matching: 5x5 candidate motions, 3x3 luma patch, result = motion (rg) + match error (b)
        const val FRAGMENT_FLOW = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uPrev;
            uniform sampler2D uCur;
            uniform vec2 uStep;
            uniform vec2 uPatch;
            float luma(vec3 c) {
                return dot(c, vec3(0.299, 0.587, 0.114));
            }
            void main() {
                float best = 1000.0;
                vec2 bestD = vec2(0.0);
                for (int j = -2; j <= 2; j++) {
                    for (int i = -2; i <= 2; i++) {
                        vec2 d = vec2(float(i), float(j)) * uStep;
                        float sad = 0.0;
                        for (int v = -1; v <= 1; v++) {
                            for (int u = -1; u <= 1; u++) {
                                vec2 o = vec2(float(u), float(v)) * uPatch;
                                sad += abs(luma(texture2D(uPrev, vUv - d + o).rgb) - luma(texture2D(uCur, vUv + d + o).rgb));
                            }
                        }
                        sad = sad / 9.0 + 0.01 * (abs(float(i)) + abs(float(j)));
                        if (sad < best) {
                            best = sad;
                            bestD = d;
                        }
                    }
                }
                vec2 enc = bestD / (2.0 * uStep) * 0.5 + 0.5;
                gl_FragColor = vec4(enc, clamp(best * 4.0, 0.0, 1.0), 1.0);
            }
        """

        const val FRAGMENT_INTERP = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uPrev;
            uniform sampler2D uCur;
            uniform sampler2D uFlow;
            uniform vec2 uStep;
            void main() {
                vec4 f = texture2D(uFlow, vUv);
                vec2 d = (f.rg * 2.0 - 1.0) * 2.0 * uStep;
                float confidence = 1.0 - clamp(f.b * 1.5, 0.0, 1.0);
                d *= confidence;
                vec3 a = texture2D(uPrev, vUv - d).rgb;
                vec3 b = texture2D(uCur, vUv + d).rgb;
                gl_FragColor = vec4(mix(a, b, 0.5), 1.0);
            }
        """
    }
}
