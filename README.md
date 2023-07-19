# socketio-kotlin

[![Maven central version](https://img.shields.io/maven-central/v/org.hildan.socketio/socketio-kotlin.svg)](https://search.maven.org/artifact/org.hildan.socketio/socketio-kotlin)
[![Github Build](https://img.shields.io/github/actions/workflow/status/joffrey-bion/socketio-kotlin/build.yml?branch=main&logo=github)](https://github.com/joffrey-bion/socketio-kotlin/actions/workflows/build.yml)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/joffrey-bion/socketio-kotlin/blob/main/LICENSE)

A Kotlin parser for Socket.IO / Engine.IO packet decoding 

## Setup

Add the dependency:

```kotlin
dependencies {
    implementation("org.hildan.socketio:socketio-kotlin:$version")
}
```

## Usage

```kotlin
val encodedTextPacket: String = TODO("get an encoded packet, for instance a web socket frame body")

val engineIOPacket = EngineIO.decodeSocketIO(encodedTextPacket)

when (engineIOPacket) {
    is EngineIOPacket.Open,
    is EngineIOPacket.Close,
    is EngineIOPacket.Upgrade,
    is EngineIOPacket.Noop,
    is EngineIOPacket.Ping,
    is EngineIOPacket.Pong -> TODO("process transport Engine.IO packet")
    is EngineIOPacket.Message -> when (val socketIOPacket = engineIoPacket.payload) {
        is SocketIOPacket.Connect,
        is SocketIOPacket.ConnectError,
        is SocketIOPacket.Disconnect -> TODO("process transport Socket.IO packet")
        is SocketIOPacket.Event,
        is SocketIOPacket.Ack -> {
            val namespace = socketIOPacket.namespace
            val eventType = socketIOPacket.payload[0].jsonPrimitive.content
            val eventPayload = socketIOPacket.payload[1].jsonObject
            println("Received Socket.IO event $eventType at $namespace: $eventPayload")
        }
    }
}
```
