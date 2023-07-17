package org.hildan.socketio

import kotlinx.serialization.json.*

/**
 * A Socket.IO packet, as defined in the [Socket.IO protocol](https://socket.io/docs/v4/socket-io-protocol).
 *
 * Exact payload types can be checked against the
 * [reference implementation](https://github.com/socketio/socket.io-parser/blob/87236baf87cdbe32ae01e7dc53320474520ce82f/lib/index.ts#L280).
 */
sealed class SocketIOPacket {

    /**
     * The namespace this packet belongs to, useful when multiplexing.
     * The default namespace is "/".
     */
    abstract val namespace: String

    /**
     * Used during the connection to a namespace.
     */
    data class Connect(
        override val namespace: String,
        /**
         * Since v5, CONNECT messages can have a payload as a JSON object.
         */
        val payload: JsonObject?,
    ) : SocketIOPacket()

    /**
     * Used during the connection to a namespace.
     */
    data class ConnectError(
        override val namespace: String,
        /**
         * A description of the error. It was a JSON string literal before v5, and is a JSON object since v5.
         */
        val errorData: JsonElement?,
    ) : SocketIOPacket()

    /**
     * Used when disconnecting from a namespace.
     */
    data class Disconnect(
        override val namespace: String,
    ) : SocketIOPacket()

    /**
     * A parent for packet types having a payload.
     */
    sealed class Message : SocketIOPacket() {
        /**
         * An ID used to match an [Event] with the corresponding [Ack].
         * When an [ackId] is present in an [Event] packet, it means an [Ack] packet is expected by the sender.
         */
        abstract val ackId: Int?

        /**
         * The payload of the message, which must be a non-empty array.
         * Usually the first element of the array is the event type (string or int), and the rest is the actual data.
         */
        abstract val payload: JsonArray
    }

    /**
     * Used to send data to the other side.
     */
    data class Event(
        override val namespace: String,
        override val ackId: Int?,
        override val payload: JsonArray,
    ): Message()

    /**
     * Used to acknowledge the event with the corresponding [ackId].
     */
    data class Ack(
        override val namespace: String,
        override val ackId: Int,
        override val payload: JsonArray,
    ): Message()
}