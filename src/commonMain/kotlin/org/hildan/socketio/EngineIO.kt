package org.hildan.socketio

import kotlinx.serialization.json.*
import kotlin.io.encoding.*

/**
 * The Engine.IO decoder, following the [Engine.IO protocol](https://socket.io/docs/v4/engine-io-protocol).
 */
object EngineIO {

    /**
     * Decodes the given [text] frame as a single [EngineIOPacket].
     *
     * If this packet is a [EngineIOPacket.Message] packet, the payload text is deserialized as a [SocketIOPacket].
     *
     * This is meant to be used in web socket mode, where each web socket frame contains a single Engine.IO packet.
     * When using HTTP long-polling with batched packets, use [decodeBatch] instead.
     */
    fun decodeSocketIO(text: String): EngineIOPacket<SocketIOPacket> = decodeFrame(text) { SocketIO.decode(it) }

    /**
     * Decodes the given [text] frame as a single [EngineIOPacket].
     *
     * If this packet is a [EngineIOPacket.Message] packet, the payload text is deserialized using the provided
     * [deserializePayload] function.
     *
     * This is meant to be used in web socket mode, where each web socket frame contains a single Engine.IO packet.
     * When using HTTP long-polling with batched packets, use [decodeBatch] instead.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun <T> decodeFrame(text: String, deserializePayload: (String) -> T): EngineIOPacket<T> = decodeSingle(
        encodedData = text,
        deserializeTextPayload = deserializePayload,
        deserializeBinaryPayload = { error("Unexpected binary payload in text frame") }
    )

    /**
     * Decodes the given binary frame [bytes] as a single [EngineIOPacket.Message].
     * As specified by the protocol, binary messages are just sent as-is, so there is no real decoding involved in this
     * function, it just wraps the bytes in a packet type for consistency.
     *
     * This is meant to be used in web socket mode, where each web socket frame contains a single Engine.IO packet.
     * When using HTTP long-polling with batched packets, use [decodeBatch] instead.
     */
    @Suppress("unused")
    fun decodeBinaryFrame(bytes: ByteArray): EngineIOPacket.Message<ByteArray> = EngineIOPacket.Message(bytes)

    /**
     * Decodes the given [batch] text as a batch of [EngineIOPacket]s.
     *
     * If a packet is a [EngineIOPacket.Message] packet, the payload text is deserialized using the provided
     * [deserializePayload] function.
     *
     * This is meant to be used in long-polling mode, where packets are batched in a single response.
     * When using web sockets, use [decodeFrame] instead.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun <T> decodeBatch(batch: String, deserializePayload: (String) -> T): List<EngineIOPacket<T>> {
        return batch.split("\u001e").map { decodeFrame(it, deserializePayload) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun <T> decodeSingle(
        encodedData: String,
        deserializeTextPayload: (String) -> T,
        deserializeBinaryPayload: (ByteArray) -> T,
    ): EngineIOPacket<T> {
        if (encodedData.isBlank()) {
            throw InvalidEngineIOPacketException(encodedData)
        }
        val payload = encodedData.drop(1)
        return when (val packetType = encodedData[0]) {
            '0' -> Json.decodeFromString<EngineIOPacket.Open>(payload)
            '1' -> EngineIOPacket.Close
            '2' -> EngineIOPacket.Ping(payload = payload)
            '3' -> EngineIOPacket.Pong(payload = payload)
            '4' -> EngineIOPacket.Message(payload = deserializeTextPayload(payload))
            '5' -> EngineIOPacket.Upgrade
            '6' -> EngineIOPacket.Noop
            'b' -> EngineIOPacket.Message(payload = deserializeBinaryPayload(Base64.decode(payload)))
            else -> error("Unknown Engine.IO packet type $packetType")
        }
    }
}

/**
 * An exception thrown when encoded data doesn't represent a valid Engine.IO packet as defined by the
 * [Engine.IO protocol](https://socket.io/docs/v4/engine-io-protocol).
 */
@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
class InvalidEngineIOPacketException(val encodedData: String) : Exception("Invalid Engine.IO packet: $encodedData")
