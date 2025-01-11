package org.hildan.socketio

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

class SocketIOTest {

    @Test
    fun invalidPacket_shouldFail() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("garbage")
        }
        assertEquals("garbage", exception.encodedData)
        assertEquals("Invalid Socket.IO packet: garbage", exception.message)
    }

    @Test
    fun unknownPacketType_shouldFail() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("9/oops")
        }
        assertEquals("9/oops", exception.encodedData)
        assertEquals("Unknown Socket.IO packet type 9", exception.message)
    }

    @Test
    fun connect() {
        val encodedData = "0"
        val packet = SocketIOPacket.Connect(namespace = "/", payload = null)
        assertCodec(packet, encodedData)
    }

    @Test
    fun connect_withData() {
        val encodedData = """0/admin,{"sid":"oSO0OpakMV_3jnilAAAA"}"""
        val packet = SocketIOPacket.Connect(
            namespace = "/admin",
            payload = buildJsonObject { put("sid", "oSO0OpakMV_3jnilAAAA") },
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun connect_withNonObjectPayload_shouldFail() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("""0/admin,["sid","oSO0OpakMV_3jnilAAAA"]""")
        }
        assertEquals("The payload for CONNECT packets must be a JSON object", exception.message)
        assertEquals("""0/admin,["sid","oSO0OpakMV_3jnilAAAA"]""", exception.encodedData)
    }

    @Test
    fun disconnect() {
        val encodedData = "1"
        val packet = SocketIOPacket.Disconnect(namespace = "/")
        assertCodec(packet, encodedData)
    }

    @Test
    fun disconnect_withNamespace() {
        val encodedData = "1/admin,"
        val packet = SocketIOPacket.Disconnect(namespace = "/admin")
        assertCodec(packet, encodedData)
    }

    @Test
    fun event() {
        val encodedData = """2["foo"]"""
        val packet = SocketIOPacket.Event(
            namespace = "/",
            ackId = null,
            payload = buildJsonArray { add("foo") },
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun event_withNamespace() {
        val encodedData = """2/admin,["bar"]"""
        val packet = SocketIOPacket.Event(
            namespace = "/admin",
            ackId = null,
            payload = buildJsonArray { add("bar") },
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun event_withAckId() {
        val encodedData = """212["foo"]"""
        val packet = SocketIOPacket.Event(
            namespace = "/",
            ackId = 12,
            payload = buildJsonArray { add("foo") },
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun event_withNamespaceAndAckId() {
        val encodedData = """2/something,42["foo"]"""
        val packet = SocketIOPacket.Event(
            namespace = "/something",
            ackId = 42,
            payload = buildJsonArray { add("foo") },
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun event_withoutPayload_shouldFail() {
        val e1 = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("2")
        }
        val e2 = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("212")
        }
        val e3 = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("2/name,")
        }
        val e4 = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("2/name,42")
        }
        listOf(e1, e2, e3, e4).forEach {
            assertEquals("The payload for EVENT and ACK packets is mandatory", it.message)
        }
    }

    @Test
    fun event_emptyArrayPayload_shouldFail() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("""2[]""")
        }
        assertEquals("The array payload for EVENT packets must not be empty", exception.message)
        assertEquals("2[]", exception.encodedData)
    }

    @Test
    fun event_namespaceWithoutComma_shouldFailWithInvalidPayload() {
        // without comma, the '/admin' is considered to be the payload
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("2/admin")
        }
        assertEquals("The payload for EVENT and ACK packets must be a JSON array", exception.message)
        assertEquals("2/admin", exception.encodedData)
    }

    @Test
    fun event_objectPayload_shouldFailWithInvalidPayload() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("2/admin,{}")
        }
        assertEquals("The payload for EVENT and ACK packets must be a JSON array", exception.message)
        assertEquals("2/admin,{}", exception.encodedData)
    }

    @Test
    fun event_textPayload_shouldFailWithInvalidPayload() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("2/admin,sometext")
        }
        // Should be invalid JSON insead, but https://github.com/Kotlin/kotlinx.serialization/issues/2511
        assertEquals("The payload for EVENT and ACK packets must be a JSON array", exception.message)
        assertEquals("2/admin,sometext", exception.encodedData)
    }

    @Test
    fun event_textPayload_shouldFailWithInvalidJson() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("2/admin,some text")
        }
        assertEquals("The payload is not valid JSON: some text", exception.message)
        assertEquals("2/admin,some text", exception.encodedData)
        assertIs<SerializationException>(exception.cause)
    }

    @Test
    fun ack_empty() {
        val encodedData = """342[]"""
        val packet = SocketIOPacket.Ack(
            namespace = "/",
            ackId = 42,
            payload = buildJsonArray {},
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun ack_withPayload() {
        val encodedData = """342["bar"]"""
        val packet = SocketIOPacket.Ack(
            namespace = "/",
            ackId = 42,
            payload = buildJsonArray { add("bar") },
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun ack_withNamespace() {
        val encodedData = """3/admin,13["bar"]"""
        val packet = SocketIOPacket.Ack(
            namespace = "/admin",
            ackId = 13,
            payload = buildJsonArray { add("bar") },
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun ack_withoutId_shouldFail() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("""3/admin,["bar"]""")
        }
        assertEquals("ACK packet without an Ack ID", exception.message)
        assertEquals("""3/admin,["bar"]""", exception.encodedData)
    }

    @Test
    fun ack_namespaceWithoutComma_shouldFailWithMissingId() {
        // without comma, the '/admin' is considered to be the payload
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("3/admin42")
        }
        assertEquals("ACK packet without an Ack ID", exception.message)
        assertEquals("3/admin42", exception.encodedData)
    }

    @Test
    fun ack_objectPayload_shouldFailWithInvalidPayload() {
        val exception = assertFailsWith<InvalidSocketIOPacketException> {
            SocketIO.decode("3/admin,42{}")
        }
        assertEquals("The payload for EVENT and ACK packets must be a JSON array", exception.message)
        assertEquals("3/admin,42{}", exception.encodedData)
    }

    @Test
    fun connectError() {
        val encodedData = """4{"message":"Not authorized"}"""
        val packet = SocketIOPacket.ConnectError(
            namespace = "/",
            errorData = buildJsonObject { put("message", "Not authorized") },
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun binaryEvent() {
        val encodedData = """52-["baz",{"_placeholder":true,"num":0},{"type":"other_data"},{"_placeholder":true,"num":1}]"""
        val packet = SocketIOPacket.BinaryEvent(
            namespace = "/",
            ackId = null,
            nBinaryAttachments = 2,
            payload = listOf(
                PayloadElement.Json(JsonPrimitive("baz")),
                PayloadElement.AttachmentRef(attachmentIndex = 0),
                PayloadElement.Json(buildJsonObject { put("type", "other_data") }),
                PayloadElement.AttachmentRef(attachmentIndex = 1),
            ),
        )
        assertCodec(packet, encodedData)
    }

    @Test
    fun binaryAck() {
        val encodedData = """61-15["bar",{"_placeholder":true,"num":0}]"""
        val packet = SocketIOPacket.BinaryAck(
            namespace = "/",
            ackId = 15,
            nBinaryAttachments = 1,
            payload = listOf(
                PayloadElement.Json(JsonPrimitive("bar")),
                PayloadElement.AttachmentRef(attachmentIndex = 0),
            ),
        )
        assertCodec(packet, encodedData)
    }

    private fun assertCodec(packet: SocketIOPacket, encodedData: String) {
        assertEquals(packet, SocketIO.decode(encodedData))
        assertEquals(encodedData, SocketIO.encode(packet))
    }
}
