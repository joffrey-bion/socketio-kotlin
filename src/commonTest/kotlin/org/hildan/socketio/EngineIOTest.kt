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
    fun encodeSocketIO_message() {
        val packet = EngineIOPacket.Message(
            payload = SocketIOPacket.Event(
                namespace = "/admin",
                ackId = 1,
                payload = buildJsonArray { add("tellme") },
            ),
        )
        val actualTextFrame = EngineIO.encodeSocketIO(packet)
        assertEquals("""42/admin,1["tellme"]""", actualTextFrame)
    }

    @Test
    fun decodeWsText_open() {
        val jsonHandshake = """{"sid":"lv_VI97HAXpY6yYWAAAC","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":20000,"maxPayload":1000000}"""
        val encodedData = "0$jsonHandshake"
        val packet = EngineIOPacket.Open(
            sid = "lv_VI97HAXpY6yYWAAAC",
            upgrades = listOf("websocket"),
            pingInterval = 25000,
            pingTimeout = 20000,
            maxPayload = 1000000,
        )
        assertWsTextCodec(packet = packet, encodedData = encodedData)
    }

    @Test
    fun decodeWsText_close() {
        assertWsTextCodec(packet = EngineIOPacket.Close, encodedData = "1")
    }

    @Test
    fun decodeWsText_ping() {
        assertWsTextCodec(packet = EngineIOPacket.Ping(payload = null), encodedData = "2")
    }

    @Test
    fun decodeWsText_ping_withPayload() {
        assertWsTextCodec(packet = EngineIOPacket.Ping(payload = "ping-payload"), encodedData = "2ping-payload")
    }

    @Test
    fun decodeWsText_pong() {
        assertWsTextCodec(packet = EngineIOPacket.Pong(payload = null), encodedData = "3")
    }

    @Test
    fun decodeWsText_pong_withPayload() {
        assertWsTextCodec(packet = EngineIOPacket.Pong(payload = "pong-payload"), encodedData = "3pong-payload")
    }

    @Test
    fun decodeWsText_message_text() {
        assertWsTextCodec(packet = EngineIOPacket.Message(payload = "hello, world!"), encodedData = "4hello, world!")
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
        assertWsTextCodec(packet = EngineIOPacket.Upgrade, encodedData = "5")
    }

    @Test
    fun decodeWsText_noop() {
        assertWsTextCodec(packet = EngineIOPacket.Noop, encodedData = "6")
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

    private fun assertWsTextCodec(packet: EngineIOPacket<String>, encodedData: String) {
        assertEquals(packet, EngineIO.decodeWsFrame(encodedData, deserializePayload = { it }))
        assertEquals(encodedData, EngineIO.encodeWsFrame(packet, serializePayload = { it }))
    }
}
