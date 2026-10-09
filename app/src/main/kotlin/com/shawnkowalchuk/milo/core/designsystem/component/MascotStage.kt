package com.shawnkowalchuk.milo.core.designsystem.component

import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.Buffer

/**
 * The mascot's stage (ADR-005): Filament's engine with the model, two lights and a camera,
 * drawing into one [TextureView]. Nothing is drawn behind the mascot, so whatever lies under
 * the view shows through.
 *
 * It keeps no time of its own. [draw] is told which moment of which clip to show, and is
 * called once for every frame by whoever holds the stage (`Mascot.kt`).
 *
 * Everything here belongs to the main thread, as Filament asks. The engine holds memory of
 * the graphics chip that Android does not count and does not free: [close] must be called.
 *
 * @param target the view that is drawn into. It must not be opaque.
 * @param model the bytes of a .glb file.
 * @throws IllegalStateException if the phone gives Filament no engine, or the bytes are not a
 * model it can read.
 */
internal class MascotStage(target: TextureView, model: Buffer) {
    init {
        // Both only load their native library, and only the first time.
        Filament.init()
        Gltfio.init()
    }

    private val engine = Engine.create()
    private val renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    private val materials = UbershaderProvider(engine)
    private val loader = AssetLoader(engine, materials, EntityManager.get())
    private val resources = ResourceLoader(engine)
    private val asset =
        checkNotNull(loader.createAsset(model)) { "The mascot's model could not be read" }

    // Filament has no clips to give until the model's parts are loaded (the init block below).
    private val animator: Animator
    private val lights = listOf(KEY_LIGHT, FILL_LIGHT).map(::light)
    private val ambient =
        IndirectLight
            .Builder()
            .irradiance(1, floatArrayOf(1f, 1f, 1f))
            .intensity(AMBIENT_LUX)
            .build(engine)

    // Filament's own tone curve is made for photographs and greys the mascot's flat colours.
    private val grading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
    private val surface = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private var swapChain: SwapChain? = null

    init {
        resources.loadResources(asset)
        asset.releaseSourceData()
        animator = asset.instance.animator
        scene.addEntities(asset.entities)
        scene.indirectLight = ambient

        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.setColorGrading(grading)
        view.multiSampleAntiAliasingOptions =
            view.multiSampleAntiAliasingOptions.apply { enabled = true }
        // Every frame starts from nothing: see-through where the mascot is not.
        renderer.clearOptions = renderer.clearOptions.apply { clear = true }
        camera.lookAt(EYE_X, EYE_Y, EYE_Z, LOOK_AT_X, LOOK_AT_Y, 0.0, 0.0, 1.0, 0.0)

        surface.isOpaque = false
        surface.renderCallback = SurfaceEvents()
        // For a view that already has its surface, Filament's helper does not ask the view how
        // large it is: it reports the size it was told to wish for, and that is 0 by 0 unless
        // it is said here. The stage is built after the model is read, so the view has it.
        if (target.width > 0 && target.height > 0) {
            surface.setDesiredSize(target.width, target.height)
        }
        surface.attachTo(target)
    }

    /** How long the clip of that name is, in seconds, or null if the model has no such clip. */
    fun lengthOf(clip: String): Float? = indexOf(clip)?.let(animator::getAnimationDuration)

    /**
     * Puts the mascot in the pose it has [seconds] into the clip and draws it.
     *
     * @param frameTimeNanos the time of the frame being drawn, as Android's frame clock gives it.
     * @return true if Filament took the picture. It takes none while the view has no surface
     * yet, for a clip the model does not have, and while it is still busy with the last two.
     */
    fun draw(clip: String, seconds: Float, frameTimeNanos: Long): Boolean {
        val index = indexOf(clip) ?: return false
        animator.applyAnimation(index, seconds)
        animator.updateBoneMatrices()
        val chain = swapChain ?: return false
        if (!surface.isReadyToRender || !renderer.beginFrame(chain, frameTimeNanos)) return false
        renderer.render(view)
        renderer.endFrame()
        return true
    }

    /** Gives back everything the stage holds. The stage cannot be used afterwards. */
    fun close() {
        surface.detach()
        dropSwapChain()
        scene.removeEntities(asset.entities)
        loader.destroyAsset(asset)
        resources.destroy()
        loader.destroy()
        materials.destroyMaterials()
        materials.destroy()
        lights.forEach { light ->
            engine.destroyEntity(light)
            EntityManager.get().destroy(light)
        }
        engine.destroyIndirectLight(ambient)
        engine.destroyView(view)
        engine.destroyColorGrading(grading)
        engine.destroyRenderer(renderer)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    private fun indexOf(clip: String): Int? =
        (0 until animator.animationCount).firstOrNull { animator.getAnimationName(it) == clip }

    private fun light(from: LightFrom): Int {
        val entity = EntityManager.get().create()
        LightManager
            .Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 1f, 1f)
            .intensity(from.lux)
            .direction(from.x, from.y, from.z)
            .castShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
        return entity
    }

    private fun dropSwapChain() {
        swapChain?.let { chain ->
            engine.destroySwapChain(chain)
            // The surface is about to go: Filament must have finished with it first.
            engine.flushAndWait()
        }
        swapChain = null
    }

    /** What the view's surface reports: it is there, it has a size, it is gone. */
    private inner class SurfaceEvents : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(window: Surface) {
            dropSwapChain()
            swapChain = engine.createSwapChain(window, surface.swapChainFlags)
        }

        override fun onDetachedFromSurface() = dropSwapChain()

        override fun onResized(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            view.viewport = Viewport(0, 0, width, height)
            val aspect = width.toDouble() / height
            camera.setProjection(FIELD_OF_VIEW, aspect, NEAR, FAR, Camera.Fov.VERTICAL)
        }
    }

    /** A light as far away as the sun: how bright it is and the way its light travels. */
    private class LightFrom(val lux: Float, val x: Float, val y: Float, val z: Float)

    private companion object {
        // The model stands on the ground at the origin, about 2 units tall, facing +Z. The
        // camera looks at its middle from the front, a little to the right and from above, as
        // the picture of the mascot does, and far enough to keep a raised arm in a square view.
        const val EYE_X = 0.35
        const val EYE_Y = 1.3
        const val EYE_Z = 6.2
        const val LOOK_AT_X = -0.2
        const val LOOK_AT_Y = 1.02
        const val FIELD_OF_VIEW = 24.0
        const val NEAR = 0.5
        const val FAR = 30.0

        // With the camera's own exposure, a light of 100,000 lux is full daylight.
        val KEY_LIGHT = LightFrom(lux = 90_000f, x = 0.45f, y = -0.5f, z = -0.74f)
        val FILL_LIGHT = LightFrom(lux = 30_000f, x = -0.7f, y = -0.1f, z = -0.7f)
        const val AMBIENT_LUX = 35_000f
    }
}
