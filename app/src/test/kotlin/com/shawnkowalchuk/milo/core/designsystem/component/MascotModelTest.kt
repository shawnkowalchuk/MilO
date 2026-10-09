package com.shawnkowalchuk.milo.core.designsystem.component

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app's copy of the mascot's model is made by hand, by a script outside the build
 * (`design/mascot/scripts/export_milo.py`), and committed. This holds it to what the code
 * expects: a model that is written again with a clip renamed, or with a clip too many, fails
 * here and not on the phone, where a missing clip is no greeting and no error.
 *
 * A .glb file is a 12-byte header, then a chunk of JSON that describes the model, then its
 * numbers. Only the JSON is read.
 */
class MascotModelTest {
    private val file = File("src/main/assets/$MODEL")
    private val described: JsonObject by lazy {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("the file starts with 'glTF'", GLTF_MAGIC, bytes.getInt(0))
        assertEquals("glTF version", 2, bytes.getInt(4))
        assertEquals("the first chunk is JSON", JSON_CHUNK, bytes.getInt(16))
        val json = ByteArray(bytes.getInt(12))
        bytes.position(20)
        bytes.get(json)
        Json.parseToJsonElement(json.decodeToString()).jsonObject
    }

    private val clips get() = described.getValue("animations").jsonArray.map { it.jsonObject }

    @Test
    fun `it carries the clips the code plays, and no other`() {
        assertEquals(
            MascotClip.entries.map { it.named },
            clips.map { it.getValue("name").jsonPrimitive.content },
        )
    }

    @Test
    fun `the wave is two seconds long`() {
        val wave = clips.single {
            it.getValue("name").jsonPrimitive.content == MascotClip.WAVE.named
        }
        val accessors = described.getValue("accessors").jsonArray
        // A clip is as long as the last moment any of its curves has a value for.
        val length =
            wave.getValue("samplers").jsonArray.maxOf { sampler ->
                val times = accessors[sampler.jsonObject.getValue("input").jsonPrimitive.int]
                times.jsonObject.getValue("max").jsonArray.single().jsonPrimitive.float
            }
        assertEquals(2f, length, 0.001f)
    }

    @Test
    fun `it needs no picture files, and stays small`() {
        assertTrue("no images", described["images"] == null)
        assertTrue("${file.length()} bytes", file.length() < MOST_BYTES)
    }

    private companion object {
        /** "glTF", and "JSON", as the four bytes of a little-endian number. */
        const val GLTF_MAGIC = 0x46546C67
        const val JSON_CHUNK = 0x4E4F534A

        /** Shawn's aim for the whole model with every clip is 3 MB; the app's copy has one. */
        const val MOST_BYTES = 2_000_000L
    }
}
