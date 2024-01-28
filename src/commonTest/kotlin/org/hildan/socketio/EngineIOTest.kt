package org.hildan.socketio

import kotlinx.io.bytestring.*
import kotlinx.serialization.json.*
import kotlin.test.*

class EngineIOTest {

    @Test
    fun decodeSocketIO_message() {
        val packet = EngineIO.decodeSocketIO(textFrame = """42/admin,1["tellme"]""")
        val expected = EngineIOPacket.Message(
            payload = SocketIOPacket.Event(
                namespace = "/admin",
                ackId = 1,
                payload = buildJsonArray { add("tellme") },
            ),
        )
        assertEquals(expected, packet)
    }

    @Test
    fun decodeWsText_open() {
        val jsonHandshake = """{
          "sid": "lv_VI97HAXpY6yYWAAAC",
          "upgrades": ["websocket"],
          "pingInterval": 25000,
          "pingTimeout": 20000,
          "maxPayload": 1000000
        }
        """.trimIndent()
        val packets = EngineIO.decodeWsFrame(text = "0$jsonHandshake", deserializePayload = { it })
        val expectedOpenPacket = EngineIOPacket.Open(
            sid = "lv_VI97HAXpY6yYWAAAC",
            upgrades = listOf("websocket"),
            pingInterval = 25000,
            pingTimeout = 20000,
            maxPayload = 1000000,
        )
        assertEquals(expectedOpenPacket, actual = packets)
    }

    @Test
    fun decodeWsText_close() {
        val packets = EngineIO.decodeWsFrame(text = "1", deserializePayload = { it })
        assertEquals(EngineIOPacket.Close, actual = packets)
    }

    @Test
    fun decodeWsText_ping() {
        val packets = EngineIO.decodeWsFrame(text = "2", deserializePayload = { it })
        assertEquals(EngineIOPacket.Ping(payload = null), actual = packets)
    }

    @Test
    fun decodeWsText_ping_withPayload() {
        val packets = EngineIO.decodeWsFrame(text = "2ping-payload", deserializePayload = { it })
        assertEquals(EngineIOPacket.Ping(payload = "ping-payload"), actual = packets)
    }

    @Test
    fun decodeWsText_pong() {
        val packets = EngineIO.decodeWsFrame(text = "3", deserializePayload = { it })
        assertEquals(EngineIOPacket.Pong(payload = null), actual = packets)
    }

    @Test
    fun decodeWsText_pong_withPayload() {
        val packets = EngineIO.decodeWsFrame(text = "3ping-payload", deserializePayload = { it })
        assertEquals(EngineIOPacket.Pong(payload = "ping-payload"), actual = packets)
    }

    @Test
    fun decodeWsText_message_text() {
        val packets = EngineIO.decodeWsFrame(text = "4hello, world!", deserializePayload = { it })
        val expected = EngineIOPacket.Message(payload = "hello, world!")
        assertEquals(expected, actual = packets)
    }

    @Test
    fun decodeWsText_message_jsonString() {
        val packets = EngineIO.decodeWsFrame(
            text = """4"hello, world!"""",
            deserializePayload = { Json.parseToJsonElement(it) },
        )
        val expected = EngineIOPacket.Message(payload = JsonPrimitive("hello, world!"))
        assertEquals(expected, actual = packets)
    }

    @Test
    fun decodeWsText_message_jsonObject() {
        val packets = EngineIO.decodeWsFrame(
            text = """4{"message":"hello, world!"}""",
            deserializePayload = { Json.parseToJsonElement(it) },
        )
        val expected = EngineIOPacket.Message(payload = buildJsonObject { put("message", "hello, world!") })
        assertEquals(expected, actual = packets)
    }

    @Test
    fun decodeWsText_upgrade() {
        val packets = EngineIO.decodeWsFrame(text = "5", deserializePayload = { it })
        assertEquals(EngineIOPacket.Upgrade, actual = packets)
    }

    @Test
    fun decodeWsText_noop() {
        val packets = EngineIO.decodeWsFrame(text = "6", deserializePayload = { it })
        assertEquals(EngineIOPacket.Noop, actual = packets)
    }

    @Test
    fun decodeWsText_binaryNotAllowed() {
        val exception = assertFailsWith<InvalidEngineIOPacketException> {
            EngineIO.decodeWsFrame(text = "bAQIDBA==", deserializePayload = { it })
        }
        assertEquals("bAQIDBA==", actual = exception.encodedData)
        assertEquals("Unexpected binary payload in web socket text frame", actual = exception.message)
    }

    @Test
    fun decodeWsBinary() {
        val packets = EngineIO.decodeWsFrame(bytes = ByteString(0x1, 0x2, 0x3, 0x4), deserializePayload = { it })
        val expected = EngineIOPacket.Message(payload = ByteString(0x1, 0x2, 0x3, 0x4))
        assertEquals(expected, actual = packets)
    }

    @Test
    fun decodeWsText_emptyPacketFails() {
        val exception = assertFailsWith<InvalidEngineIOPacketException> {
            EngineIO.decodeWsFrame(text = "", deserializePayload = { it })
        }
        assertEquals("", exception.encodedData)
        assertEquals("The Engine.IO packet is empty", exception.message)
    }

    @Test
    fun decodeWsText_invalidPacket() {
        val exception = assertFailsWith<InvalidEngineIOPacketException> {
            EngineIO.decodeWsFrame(text = "garbage", deserializePayload = { it })
        }
        assertEquals("garbage", exception.encodedData)
        assertEquals("Unknown Engine.IO packet type 'g'", exception.message)
    }

    @Test
    fun decodeHttp_open() {
        val jsonHandshake = """{
          "sid": "lv_VI97HAXpY6yYWAAAC",
          "upgrades": ["websocket"],
          "pingInterval": 25000,
          "pingTimeout": 20000,
          "maxPayload": 1000000
        }
        """.trimIndent()
        val packets = EngineIO.decodeHttpBatch(batch = "0$jsonHandshake", deserializeTextPayload = { it })
        val expectedOpenPacket = EngineIOPacket.Open(
            sid = "lv_VI97HAXpY6yYWAAAC",
            upgrades = listOf("websocket"),
            pingInterval = 25000,
            pingTimeout = 20000,
            maxPayload = 1000000,
        )
        assertEquals(listOf(expectedOpenPacket), actual = packets)
    }

    @Test
    fun decodeHttp_message() {
        val packets = EngineIO.decodeHttpBatch(batch = "4hello, world!", deserializeTextPayload = { it })
        val expected = listOf(EngineIOPacket.Message(payload = "hello, world!"))
        assertEquals(expected, actual = packets)
    }

    @Test
    fun decodeHttp_base64Binary() {
        val packets = EngineIO.decodeHttpBatch(
            batch = "bAQIDBA==",
            deserializeTextPayload = { it },
            deserializeBinaryPayload = { it },
        )
        val expected = listOf(EngineIOPacket.Message(payload = ByteString(0x1, 0x2, 0x3, 0x4)))
        assertEquals(expected, actual = packets)
    }

    @Test
    fun decodeHttp_batch() {
        val packets = EngineIO.decodeHttpBatch(batch = "4hello\u001E2\u001E4world!", deserializeTextPayload = { it })
        val expected = listOf(
            EngineIOPacket.Message(payload = "hello"),
            EngineIOPacket.Ping(payload = null),
            EngineIOPacket.Message(payload = "world!"),
        )
        assertEquals(expected, actual = packets)
    }

    @Test
    fun decodeHttp_batch_base64Binary() {
        val packets = EngineIO.decodeHttpBatch(
            batch = """4hellobAQIDBA==""",
            deserializeTextPayload = { it },
            deserializeBinaryPayload = { it },
        )
        val expected = listOf(
            EngineIOPacket.Message(payload = "hello"),
            EngineIOPacket.Message(payload = ByteString(0x1, 0x2, 0x3, 0x4)),
        )
        assertEquals(expected, actual = packets)
    }
}
