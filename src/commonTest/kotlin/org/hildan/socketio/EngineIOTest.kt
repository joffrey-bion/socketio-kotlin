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
    fun wsTextCodec_open() {
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
    fun wsTextCodec_close() {
        assertWsTextCodec(packet = EngineIOPacket.Close, encodedData = "1")
    }

    @Test
    fun wsTextCodec_ping() {
        assertWsTextCodec(packet = EngineIOPacket.Ping(payload = null), encodedData = "2")
    }

    @Test
    fun wsTextCodec_ping_withPayload() {
        assertWsTextCodec(packet = EngineIOPacket.Ping(payload = "ping-payload"), encodedData = "2ping-payload")
    }

    @Test
    fun wsTextCodec_pong() {
        assertWsTextCodec(packet = EngineIOPacket.Pong(payload = null), encodedData = "3")
    }

    @Test
    fun wsTextCodec_pong_withPayload() {
        assertWsTextCodec(packet = EngineIOPacket.Pong(payload = "pong-payload"), encodedData = "3pong-payload")
    }

    @Test
    fun wsTextCodec_message_text() {
        assertWsTextCodec(packet = EngineIOPacket.Message(payload = "hello, world!"), encodedData = "4hello, world!")
    }

    @Test
    fun wsTextCodec_message_jsonString() {
        val packets = EngineIO.decodeWsFrame(
            text = """4"hello, world!"""",
            deserializePayload = { Json.parseToJsonElement(it) },
        )
        val expected = EngineIOPacket.Message(payload = JsonPrimitive("hello, world!"))
        assertEquals(expected, actual = packets)
    }

    @Test
    fun wsTextCodec_message_jsonObject() {
        val packets = EngineIO.decodeWsFrame(
            text = """4{"message":"hello, world!"}""",
            deserializePayload = { Json.parseToJsonElement(it) },
        )
        val expected = EngineIOPacket.Message(payload = buildJsonObject { put("message", "hello, world!") })
        assertEquals(expected, actual = packets)
    }

    @Test
    fun wsTextCodec_upgrade() {
        assertWsTextCodec(packet = EngineIOPacket.Upgrade, encodedData = "5")
    }

    @Test
    fun wsTextCodec_noop() {
        assertWsTextCodec(packet = EngineIOPacket.Noop, encodedData = "6")
    }

    @Test
    fun wsTextCodec_binaryNotAllowed() {
        val exception = assertFailsWith<InvalidEngineIOPacketException> {
            EngineIO.decodeWsFrame(text = "bAQIDBA==", deserializePayload = { it })
        }
        assertEquals("bAQIDBA==", actual = exception.encodedData)
        assertEquals("Unexpected binary payload in web socket text frame", actual = exception.message)
    }

    @Test
    fun wsBinaryCodec() {
        val data = ByteString(0x1, 0x2, 0x3, 0x4)
        val packet = EngineIOPacket.BinaryData(payload = data)
        assertEquals(packet, EngineIO.decodeWsFrame(data))
        assertEquals(data, EngineIO.encodeWsFrame(packet))
    }

    @Test
    fun wsTextCodec_emptyPacketFails() {
        val exception = assertFailsWith<InvalidEngineIOPacketException> {
            EngineIO.decodeWsFrame(text = "", deserializePayload = { it })
        }
        assertEquals("", exception.encodedData)
        assertEquals("The Engine.IO packet is empty", exception.message)
    }

    @Test
    fun wsTextCodec_invalidPacket() {
        val exception = assertFailsWith<InvalidEngineIOPacketException> {
            EngineIO.decodeWsFrame(text = "garbage", deserializePayload = { it })
        }
        assertEquals("garbage", exception.encodedData)
        assertEquals("Unknown Engine.IO packet type 'g'", exception.message)
    }

    @Test
    fun httpBatchCodec_open() {
        val jsonHandshake = """{"sid":"lv_VI97HAXpY6yYWAAAC","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":20000,"maxPayload":1000000}"""
        val expectedOpenPacket = EngineIOPacket.Open(
            sid = "lv_VI97HAXpY6yYWAAAC",
            upgrades = listOf("websocket"),
            pingInterval = 25000,
            pingTimeout = 20000,
            maxPayload = 1000000,
        )
        assertHttpBatchCodec(listOf(expectedOpenPacket), "0$jsonHandshake")
    }

    @Test
    fun httpBatchCodec_message() {
        val packets = listOf(EngineIOPacket.Message(payload = "hello, world!"))
        assertHttpBatchCodec(packets, "4hello, world!")
    }

    @Test
    fun httpBatchCodec_base64Binary() {
        val packets = listOf(EngineIOPacket.BinaryData(payload = ByteString(0x1, 0x2, 0x3, 0x4)))
        assertHttpBatchCodec(packets, encodedData = "bAQIDBA==")
    }

    @Test
    fun httpBatchCodec_batch() {
        val packets = listOf(
            EngineIOPacket.Message(payload = "hello"),
            EngineIOPacket.Ping(payload = null),
            EngineIOPacket.Message(payload = "world!"),
        )
        assertHttpBatchCodec(packets, encodedData = "4hello\u001E2\u001E4world!")
    }

    @Test
    fun httpBatchCodec_batch_base64Binary() {
        val packets = listOf(
            EngineIOPacket.Message(payload = "hello"),
            EngineIOPacket.BinaryData(payload = ByteString(0x1, 0x2, 0x3, 0x4)),
        )
        assertHttpBatchCodec(packets, encodedData = """4hellobAQIDBA==""")
    }

    private fun assertWsTextCodec(packet: EngineIOPacket<String>, encodedData: String) {
        assertEquals(packet, EngineIO.decodeWsFrame(encodedData, deserializePayload = { it }))
        assertEquals(encodedData, EngineIO.encodeWsFrame(packet, serializePayload = { it }))
    }

    private fun assertHttpBatchCodec(packets: List<EngineIOPacket<String>>, encodedData: String) {
        assertEquals(packets, EngineIO.decodeHttpBatch(encodedData, deserializePayload = { it }))
        assertEquals(encodedData, EngineIO.encodeHttpBatch(packets, serializePayload = { it }))
    }
}
