package org.hildan.socketio

import kotlinx.io.bytestring.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.*

/**
 * The Engine.IO decoder, following the [Engine.IO protocol](https://socket.io/docs/v4/engine-io-protocol).
 */
object EngineIO {

    /**
     * Decodes the given [textFrame] as a single [EngineIOPacket].
     *
     * If this packet is a [EngineIOPacket.Message] packet, the payload text is deserialized as a [SocketIOPacket].
     *
     * This is meant to be used in web socket mode, where each web socket frame contains a single Engine.IO packet.
     * When using HTTP long-polling with batched packets, use [decodeHttpBatch] instead.
     */
    fun decodeSocketIO(textFrame: String): EngineIOPacket<SocketIOPacket> = decodeWsFrame(textFrame, SocketIO::decode)

    /**
     * Decodes the given web socket [text] frame as a single [EngineIOPacket].
     *
     * If this packet is a [EngineIOPacket.Message] packet, the payload text is deserialized using the provided
     * [deserializePayload] function.
     *
     * This is meant to be used in web socket mode, where each web socket frame contains a single Engine.IO packet.
     * When using HTTP long-polling with batched packets, use [decodeHttpBatch] instead.
     */
    fun <T> decodeWsFrame(text: String, deserializePayload: (String) -> T): EngineIOPacket<T> = decodeSinglePacket(
        encodedData = text,
        deserializeTextPayload = deserializePayload,
        deserializeBinaryPayload = {
            // Base64-encoded binary payloads are supported with 'b' prefix in long-polling mode.
            // In web socket mode, binary messages must be sent as separate binary frames.
            throw InvalidEngineIOPacketException(text, "Unexpected binary payload in web socket text frame")
        },
    )

    /**
     * Decodes the given binary web socket frame's [bytes] as a single [EngineIOPacket.Message].
     *
     * As specified by the protocol, binary messages are just sent as-is, so there is no real decoding involved in this
     * function, it just wraps the bytes in a packet type for consistency.
     *
     * This is meant to be used in web socket mode, where each web socket frame contains a single Engine.IO packet.
     * When using HTTP long-polling with batched packets, use [decodeHttpBatch] instead.
     */
    fun <T> decodeWsFrame(bytes: ByteString, deserializePayload: (ByteString) -> T): EngineIOPacket.Message<T> =
        EngineIOPacket.Message(deserializePayload(bytes))

    /**
     * Decodes the given [batch] text as a batch of [EngineIOPacket]s.
     *
     * If a packet is a [EngineIOPacket.Message] packet, the payload text is deserialized using the provided
     * [deserializeTextPayload] function. If the payload is binary, is it deserialized using [deserializeBinaryPayload]
     * instead. By default, [deserializeBinaryPayload] will decode the binary data as UTF-8 text.
     *
     * This is meant to be used in HTTP long-polling mode, where packets are batched in a single HTTP response.
     * When using web sockets, use [decodeWsFrame] on each frame instead.
     */
    fun <T> decodeHttpBatch(
        batch: String,
        deserializeTextPayload: (String) -> T,
        deserializeBinaryPayload: (ByteString) -> T = { deserializeTextPayload(it.decodeToString()) },
    ): List<EngineIOPacket<T>> {
        // Splitting on the "record-separator" character as defined by the protocol:
        // https://socket.io/docs/v4/engine-io-protocol#http-long-polling-1
        return batch.split("\u001e").map {
            decodeSinglePacket(it, deserializeTextPayload, deserializeBinaryPayload)
        }
    }

    // Base64-encoded binary payloads are supported with 'b' prefix in long-polling mode.
    // In web socket mode, binary messages should be sent as separate binary frames.
    @OptIn(ExperimentalEncodingApi::class)
    private fun <T> decodeSinglePacket(
        encodedData: String,
        deserializeTextPayload: (String) -> T,
        deserializeBinaryPayload: (ByteString) -> T,
    ): EngineIOPacket<T> {
        if (encodedData.isBlank()) {
            throw InvalidEngineIOPacketException(encodedData, "The Engine.IO packet is empty")
        }
        val payload = encodedData.drop(1)
        return when (val packetType = encodedData[0]) {
            '0' -> Json.decodeFromString<EngineIOPacket.Open>(payload)
            '1' -> EngineIOPacket.Close
            '2' -> EngineIOPacket.Ping(payload = payload.takeIf { it.isNotEmpty() })
            '3' -> EngineIOPacket.Pong(payload = payload.takeIf { it.isNotEmpty() })
            '4' -> EngineIOPacket.Message(payload = deserializeTextPayload(payload))
            '5' -> EngineIOPacket.Upgrade
            '6' -> EngineIOPacket.Noop
            'b' -> EngineIOPacket.Message(payload = deserializeBinaryPayload(Base64.decodeToByteString(payload)))
            else -> throw InvalidEngineIOPacketException(encodedData, "Unknown Engine.IO packet type '$packetType'")
        }
    }
}

/**
 * An exception thrown when encoded data doesn't represent a valid Engine.IO packet as defined by the
 * [Engine.IO protocol](https://socket.io/docs/v4/engine-io-protocol).
 */
class InvalidEngineIOPacketException(val encodedData: String, message: String) : Exception(message)
