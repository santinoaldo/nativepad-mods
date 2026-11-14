package com.handicaps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen shader passes on top of Minecraft's frame.
 *
 * Everything is done with raw OpenGL inside glPushAttrib/glPopAttrib, so Minecraft's own GL state cache
 * (GlStateManager) never sees a change and nothing leaks into the rest of the frame.
 */
final class PostFx {

    private static final String VERT = "#version 120\n"
            + "varying vec2 uv;\n"
            + "void main() { uv = gl_Vertex.xy * 0.5 + 0.5; gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0); }\n";

    /** World pass: drunk wobble, near-sighted blur, short-sight fog. Uses the depth buffer for distance. */
    private static final String WORLD_FRAG = "#version 120\n"
            + "uniform sampler2D colorTex;\n"
            + "uniform sampler2D depthTex;\n"
            + "uniform vec2 texel;\n"
            + "uniform float nearPlane;\n"
            + "uniform float farPlane;\n"
            + "uniform vec2 tanHalf;\n"
            + "uniform float fogOn; uniform float fogStart; uniform float fogEnd; uniform vec3 fogColor;\n"
            + "uniform float blurOn; uniform float clearDist; uniform float blurRange; uniform float blurMax;\n"
            + "uniform float drunkOn; uniform float drunkAmt; uniform float time;\n"
            + "varying vec2 uv;\n"
            + "float worldDist(vec2 p) {\n"
            + "  float d = texture2D(depthTex, p).r;\n"
            + "  if (d >= 0.99999) return 1.0e6;\n" // sky
            + "  float z = d * 2.0 - 1.0;\n"
            + "  float lin = 2.0 * nearPlane * farPlane / (farPlane + nearPlane - z * (farPlane - nearPlane));\n"
            + "  vec2 ndc = p * 2.0 - 1.0;\n"
            + "  return lin * length(vec3(ndc * tanHalf, 1.0));\n"
            + "}\n"
            + "void main() {\n"
            + "  vec2 p = uv;\n"
            + "  if (drunkOn > 0.5) {\n"
            + "    p += drunkAmt * 0.010 * vec2(sin(p.y * 9.0 + time * 1.3), cos(p.x * 8.0 + time * 1.1));\n"
            + "    p = clamp(p, vec2(0.001), vec2(0.999));\n"
            + "  }\n"
            + "  float dist = worldDist(p);\n"
            + "  vec3 col = texture2D(colorTex, p).rgb;\n"
            + "  if (blurOn > 0.5) {\n"
            + "    float r = clamp((dist - clearDist) / max(blurRange, 0.1), 0.0, 1.0) * blurMax;\n"
            + "    if (r > 0.4) {\n"
            + "      vec3 acc = col;\n"
            + "      float n = 1.0;\n"
            + "      for (int i = 0; i < 16; i++) {\n"
            + "        float a = float(i) * 2.39996;\n" // golden angle spiral
            + "        float rr = sqrt((float(i) + 0.5) / 16.0) * r;\n"
            + "        acc += texture2D(colorTex, clamp(p + vec2(cos(a), sin(a)) * rr * texel, vec2(0.0), vec2(1.0))).rgb;\n"
            + "        n += 1.0;\n"
            + "      }\n"
            + "      col = acc / n;\n"
            + "    }\n"
            + "  }\n"
            + "  if (drunkOn > 0.5) {\n"
            + "    vec2 off = vec2(sin(time * 0.7), cos(time * 0.9)) * 0.008 * drunkAmt;\n"
            + "    vec3 ghost = texture2D(colorTex, clamp(p + off, vec2(0.0), vec2(1.0))).rgb;\n"
            + "    col = mix(col, ghost, 0.35 * min(drunkAmt, 1.0));\n"
            + "  }\n"
            + "  if (fogOn > 0.5) {\n"
            + "    float f = clamp((dist - fogStart) / max(fogEnd - fogStart, 0.01), 0.0, 1.0);\n"
            + "    col = mix(col, fogColor, f);\n"
            + "  }\n"
            + "  gl_FragColor = vec4(col, 1.0);\n"
            + "}\n";

    /** Grayscale over the finished frame (HUD and menus included). */
    private static final String GRAY_FRAG = "#version 120\n"
            + "uniform sampler2D colorTex;\n"
            + "uniform float amount;\n"
            + "varying vec2 uv;\n"
            + "void main() {\n"
            + "  vec3 c = texture2D(colorTex, uv).rgb;\n"
            + "  float g = dot(c, vec3(0.299, 0.587, 0.114));\n"
            + "  gl_FragColor = vec4(mix(c, vec3(g), amount), 1.0);\n"
            + "}\n";

    /** Tunnel vision: black ring drawn with alpha blending. */
    private static final String TUNNEL_FRAG = "#version 120\n"
            + "uniform float aspect;\n"
            + "uniform float inner;\n"
            + "uniform float outer;\n"
            + "varying vec2 uv;\n"
            + "void main() {\n"
            + "  vec2 d = (uv - 0.5) * vec2(aspect, 1.0);\n"
            + "  float a = smoothstep(inner, outer, length(d));\n"
            + "  gl_FragColor = vec4(0.0, 0.0, 0.0, a);\n"
            + "}\n";

    private final Minecraft mc = Minecraft.getMinecraft();
    private int worldProg, grayProg, tunnelProg;
    private int colorTex, depthTex, texW, texH;
    private boolean initialized, failed;
    String error;
    private final Map<String, Integer> uniforms = new HashMap<String, Integer>();

    boolean available() {
        if (failed || !OpenGlHelper.shadersSupported) return false;
        if (!initialized) init();
        return !failed;
    }

    private void init() {
        initialized = true;
        try {
            worldProg = link(VERT, WORLD_FRAG);
            grayProg = link(VERT, GRAY_FRAG);
            tunnelProg = link(VERT, TUNNEL_FRAG);
            colorTex = GL11.glGenTextures();
            depthTex = GL11.glGenTextures();
        } catch (RuntimeException e) {
            failed = true;
            error = e.getMessage();
            Handicaps.LOG.error("Handicaps shaders failed to load", e);
        }
    }

    // ---- passes ----

    void worldPass(boolean fog, float fogStart, float fogEnd, float[] fogColor,
                   boolean blur, float clearDist, float blurRange, float blurMax,
                   boolean drunk, float drunkAmt, float time) {
        int[] size = frameSize();
        begin();
        try {
            copyColor(size[0], size[1]);
            copyDepth(size[0], size[1]);
            GL20.glUseProgram(worldProg);
            set1i(worldProg, "colorTex", 0);
            set1i(worldProg, "depthTex", 1);
            GL20.glUniform2f(loc(worldProg, "texel"), 1f / size[0], 1f / size[1]);
            GL20.glUniform1f(loc(worldProg, "nearPlane"), 0.05f);
            // Minecraft 1.8.9's far plane: render distance in blocks * sqrt(2).
            GL20.glUniform1f(loc(worldProg, "farPlane"), mc.gameSettings.renderDistanceChunks * 16 * 1.4142135f);
            float tanV = (float) Math.tan(Math.toRadians(mc.gameSettings.fovSetting) / 2);
            GL20.glUniform2f(loc(worldProg, "tanHalf"), tanV * size[0] / (float) size[1], tanV);
            GL20.glUniform1f(loc(worldProg, "fogOn"), fog ? 1 : 0);
            GL20.glUniform1f(loc(worldProg, "fogStart"), fogStart);
            GL20.glUniform1f(loc(worldProg, "fogEnd"), fogEnd);
            GL20.glUniform3f(loc(worldProg, "fogColor"), fogColor[0], fogColor[1], fogColor[2]);
            GL20.glUniform1f(loc(worldProg, "blurOn"), blur ? 1 : 0);
            GL20.glUniform1f(loc(worldProg, "clearDist"), clearDist);
            GL20.glUniform1f(loc(worldProg, "blurRange"), blurRange);
            GL20.glUniform1f(loc(worldProg, "blurMax"), blurMax);
            GL20.glUniform1f(loc(worldProg, "drunkOn"), drunk ? 1 : 0);
            GL20.glUniform1f(loc(worldProg, "drunkAmt"), drunkAmt);
            GL20.glUniform1f(loc(worldProg, "time"), time);
            quad(false);
        } finally {
            end();
        }
    }

    void grayscalePass(float amount) {
        int[] size = frameSize();
        begin();
        try {
            copyColor(size[0], size[1]);
            GL20.glUseProgram(grayProg);
            set1i(grayProg, "colorTex", 0);
            GL20.glUniform1f(loc(grayProg, "amount"), amount);
            quad(false);
        } finally {
            end();
        }
    }

    void tunnelPass(float inner, float outer) {
        int[] size = frameSize();
        begin();
        try {
            GL20.glUseProgram(tunnelProg);
            GL20.glUniform1f(loc(tunnelProg, "aspect"), size[0] / (float) size[1]);
            GL20.glUniform1f(loc(tunnelProg, "inner"), inner);
            GL20.glUniform1f(loc(tunnelProg, "outer"), outer);
            quad(true);
        } finally {
            end();
        }
    }

    // ---- GL plumbing ----

    private int savedProgram, savedActiveTex;

    private void begin() {
        savedProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        savedActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);
    }

    private void end() {
        GL20.glUseProgram(savedProgram);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL13.glActiveTexture(savedActiveTex);
        // Restore Minecraft's texture binding on the main unit (GlStateManager thinks it's still bound).
        net.minecraft.client.renderer.GlStateManager.bindTexture(0);
    }

    private int[] frameSize() {
        Framebuffer fb = mc.getFramebuffer();
        if (OpenGlHelper.isFramebufferEnabled() && fb != null) return new int[]{fb.framebufferWidth, fb.framebufferHeight};
        return new int[]{mc.displayWidth, mc.displayHeight};
    }

    private void ensureTextures(int w, int h) {
        if (w == texW && h == texH) return;
        texW = w;
        texH = h;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        params();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        params();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
    }

    private static void params() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private void copyColor(int w, int h) {
        ensureTextures(w, h);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
    }

    private void copyDepth(int w, int h) {
        ensureTextures(w, h);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static void quad(boolean blend) {
        if (blend) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(-1, -1);
        GL11.glVertex2f(1, -1);
        GL11.glVertex2f(1, 1);
        GL11.glVertex2f(-1, 1);
        GL11.glEnd();
    }

    private int loc(int prog, String name) {
        String key = prog + ":" + name;
        Integer l = uniforms.get(key);
        if (l == null) {
            l = GL20.glGetUniformLocation(prog, name);
            uniforms.put(key, l);
        }
        return l;
    }

    private void set1i(int prog, String name, int v) {
        GL20.glUniform1i(loc(prog, name), v);
    }

    private static int link(String vert, String frag) {
        int v = compile(GL20.GL_VERTEX_SHADER, vert);
        int f = compile(GL20.GL_FRAGMENT_SHADER, frag);
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Shader link failed: " + GL20.glGetProgramInfoLog(p, 4096));
        }
        return p;
    }

    private static int compile(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Shader compile failed: " + GL20.glGetShaderInfoLog(s, 4096));
        }
        return s;
    }
}
