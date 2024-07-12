package org.hildan.socketio

import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * The Socket.IO decoder, following the [Socket.IO protocol](https://socket.io/docs/v4/socket-io-protocol).
 */
object SocketIO {

    private val packetFormatRegex = Regex("""(?<packetType>\d)((?<nBinaryAttachments>\d+)-)?((?<namespace>/[^,]+),)?(?<ackId>\d+)?(?<payload>.*)?""")

    /**
     * Decodes the given [encodedData] into a [SocketIOPacket].
     *
     * The [encodedData] must be the pure Socket.IO packet data, not wrapped in an Engine.IO packet.
     *
     * Binary packet types are not supported. Binary attachments are passed in subsequent web socket frames
     * (1 frame per attachment), and thus cannot be handled in this single-frame decoding function.
     *
     * @throws InvalidSocketIOPacketException if the given [encodedData] is not a valid Socket.IO packet
     */
    fun decode(encodedData: String): SocketIOPacket = parseRawPacket(encodedData).toSocketIOPacket()

    private fun parseRawPacket(encodedData: String): RawPacket {
        val match = packetFormatRegex.matchEntire(encodedData)
            ?: throw InvalidSocketIOPacketException(encodedData, "Invalid Socket.IO packet: $encodedData")
        return RawPacket(
            encodedData = encodedData,
            packetType = match.groups["packetType"]?.value?.toInt()
                ?: error("Internal error: Socket.IO format regex was matched but the packetType group is missing"),
            nBinaryAttachments = match.groups["nBinaryAttachments"]?.value?.toInt() ?: 0,
            namespace = match.groups["namespace"]?.value ?: "/",
            ackId = match.groups["ackId"]?.value?.toInt(),
            payload = match.groups["payload"]?.value?.takeIf { it.isNotBlank() }?.let { parsePayload(encodedData, it) }
        )
    }

    private fun parsePayload(encodedData: String, payload: String) = try {
        Json.parseToJsonElement(payload)
    } catch (e: SerializationException) {
        throw InvalidSocketIOPacketException(encodedData, "The payload is not valid JSON: $payload", cause = e)
    }

    private fun RawPacket.toSocketIOPacket(): SocketIOPacket = when (packetType) {
        0 -> SocketIOPacket.Connect(namespace, payload = connectPayload())
        1 -> SocketIOPacket.Disconnect(namespace)
        2 -> SocketIOPacket.Event(namespace, ackId, payload = messagePayload())
        3 -> SocketIOPacket.Ack(namespace, ackId = mandatoryAckId(), payload = messagePayload())
        4 -> SocketIOPacket.ConnectError(namespace, errorData = payload)
        5, 6 -> throw IllegalArgumentException("Binary Socket.IO packets are not supported")
        else -> invalid("Unknown Socket.IO packet type $packetType")
    }
}

private data class RawPacket(
    val encodedData: String,
    val packetType: Int,
    val nBinaryAttachments: Int,
    val namespace: String,
    val ackId: Int?,
    val payload: JsonElement?,
) {
    init {
        require(nBinaryAttachments == 0) { "Socket.IO packets with binary attachments are not supported" }
    }
}

// This is not clearly documented in the protocol spec, but the payload for CONNECT has been added in v5 and the
// official socket.io-parser v4 (for protocol v5) has the following validation which expects JSON object in this case:
// https://github.com/socketio/socket.io-parser/blob/164ba2a11edc34c2f363401e9768f9a8541a8b89/lib/index.ts#L285-L305
private fun RawPacket.connectPayload(): JsonObject? {
    if (payload == null) {
        return null
    }
    return payload as? JsonObject ?: invalid("The payload for CONNECT packets must be a JSON object")
}

// Reference: https://socket.io/docs/v4/socket-io-protocol#sending-and-receiving-data
private fun RawPacket.messagePayload(): JsonArray {
    if (payload == null) {
        invalid("The payload for EVENT and ACK packets is mandatory")
    }
    if (payload !is JsonArray) {
        invalid("The payload for EVENT and ACK packets must be a JSON array")
    }
    if (payload.isEmpty()) {
        invalid("The array payload for EVENT and ACK packets must not be empty")
    }
    return payload
}

private fun RawPacket.mandatoryAckId() = ackId ?: invalid("ACK packet without an Ack ID")

private fun RawPacket.invalid(message: String): Nothing = throw InvalidSocketIOPacketException(encodedData, message)

/**
 * An exception thrown when some encoded data doesn't represent a valid Socket.IO packet as defined by the
 * [Socket.IO protocol](https://socket.io/docs/v4/socket-io-protocol).
 */
class InvalidSocketIOPacketException(
    val encodedData: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
