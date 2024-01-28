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
        val packet = SocketIO.decode("0")
        assertEquals(SocketIOPacket.Connect(namespace = "/", payload = null), packet)
    }

    @Test
    fun connect_withData() {
        val packet = SocketIO.decode("""0/admin,{"sid":"oSO0OpakMV_3jnilAAAA"}""")
        val expected = SocketIOPacket.Connect(
            namespace = "/admin",
            payload = buildJsonObject { put("sid", "oSO0OpakMV_3jnilAAAA") },
        )
        assertEquals(expected, packet)
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
        val packet = SocketIO.decode("1")
        val expected = SocketIOPacket.Disconnect(namespace = "/")
        assertEquals(expected, packet)
    }

    @Test
    fun disconnect_withNamespace() {
        val packet = SocketIO.decode("1/admin,")
        val expected = SocketIOPacket.Disconnect(namespace = "/admin")
        assertEquals(expected, packet)
    }

    @Test
    fun event() {
        val packet = SocketIO.decode("""2["foo"]""")
        val expected = SocketIOPacket.Event(
            namespace = "/",
            ackId = null,
            payload = buildJsonArray { add("foo") },
        )
        assertEquals(expected, packet)
    }

    @Test
    fun event_withNamespace() {
        val packet = SocketIO.decode("""2/admin,["bar"]""")
        val expected = SocketIOPacket.Event(
            namespace = "/admin",
            ackId = null,
            payload = buildJsonArray { add("bar") },
        )
        assertEquals(expected, packet)
    }

    @Test
    fun event_withAckId() {
        val packet = SocketIO.decode("""212["foo"]""")
        val expected = SocketIOPacket.Event(
            namespace = "/",
            ackId = 12,
            payload = buildJsonArray { add("foo") },
        )
        assertEquals(expected, packet)
    }

    @Test
    fun event_withNamespaceAndAckId() {
        val packet = SocketIO.decode("""2/something,42["foo"]""")
        val expected = SocketIOPacket.Event(
            namespace = "/something",
            ackId = 42,
            payload = buildJsonArray { add("foo") },
        )
        assertEquals(expected, packet)
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
        assertEquals("The array payload for EVENT and ACK packets must not be empty", exception.message)
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
    fun ack() {
        val packet = SocketIO.decode("""3/admin,13["bar"]""")
        val expected = SocketIOPacket.Ack(
            namespace = "/admin",
            ackId = 13,
            payload = buildJsonArray { add("bar") },
        )
        assertEquals(expected, packet)
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
    fun connectError() {
        val packet = SocketIO.decode("""4{"message":"Not authorized"}""")
        val expected = SocketIOPacket.ConnectError(
            namespace = "/",
            errorData = buildJsonObject { put("message", "Not authorized") },
        )
        assertEquals(expected, packet)
    }

    @Test
    fun binaryEvent_unsupported() {
        val exception = assertFailsWith<IllegalArgumentException> {
            SocketIO.decode("""51-["baz",{"_placeholder":true,"num":0}]""")
        }
        assertEquals("Socket.IO packets with binary attachments are not supported", exception.message)
    }

    @Test
    fun binaryAck_unsupported() {
        val exception = assertFailsWith<IllegalArgumentException> {
            SocketIO.decode("""61-15["bar",{"_placeholder":true,"num":0}]""")
        }
        assertEquals("Socket.IO packets with binary attachments are not supported", exception.message)
    }
}
