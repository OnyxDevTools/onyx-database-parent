package com.onyx.client

import com.onyx.buffer.BufferPool
import com.onyx.buffer.NetworkBufferPool
import com.onyx.client.base.RequestToken
import com.onyx.client.serialization.ServerSerializer
import com.onyx.extension.withBuffer
import java.nio.ByteBuffer

class Message(var messageId:Short = 0) {

    var numberOfPackets:Short = 0
    val packets:MutableList<Packet> = ArrayList()

    companion object {
        val PACKET_METADATA_SIZE = java.lang.Short.BYTES * 2
        val MESSAGE_METADATA_SIZE = java.lang.Short.BYTES
    }
}

fun ByteBuffer.toMessage(request: RequestToken) : Message {
    return withBuffer(this) {
        val message = Message(request.token)

        // Divide the buffer into individual packets
        while (this.hasRemaining()) {
            val packetBuffer = NetworkBufferPool.allocate()
            packetBuffer.position(if(message.packets.isEmpty()) (Message.PACKET_METADATA_SIZE + Message.MESSAGE_METADATA_SIZE) else Message.PACKET_METADATA_SIZE)

            while (this.hasRemaining() && packetBuffer.hasRemaining())
                packetBuffer.put(this.get())

            packetBuffer.flip()
            val packet = Packet(packetBuffer.limit().toShort(), message.messageId, packetBuffer)
            packet.write(packetBuffer)
            packetBuffer.rewind()

            message.packets.add(packet)
        }

        // Update the number of packets
        message.numberOfPackets = message.packets.size.toShort() // Add 1 for the message metadata packet
        val firstPacket = message.packets.first()
        firstPacket.packetBuffer.position(Message.PACKET_METADATA_SIZE)
        firstPacket.packetBuffer.putShort(message.numberOfPackets)
        firstPacket.packetBuffer.rewind()

        return@withBuffer message
    }
}

fun Message.toByteBuffer():ByteBuffer {
    val messageBuffer = if(this.packets.size == 1) NetworkBufferPool.allocate() else BufferPool.allocateAndLimit(this.packets.size * NetworkBufferPool.bufferSize)
    this.packets.forEachIndexed { index, packet ->
        NetworkBufferPool.withBuffer(packet.packetBuffer) {
            it.position(if(index == 0) (Message.PACKET_METADATA_SIZE + Message.MESSAGE_METADATA_SIZE) else Message.PACKET_METADATA_SIZE)
            messageBuffer.put(it)
        }
    }
    messageBuffer.flip()
    return messageBuffer
}

fun Message.toRequest(serializer:ServerSerializer):RequestToken {
    val buffer = toByteBuffer()
    if(this.packets.size > 1) {
        return withBuffer(buffer) {
            serializer.deserialize(buffer, RequestToken()) as RequestToken
        }
    } else {
        return NetworkBufferPool.withBuffer(buffer) {
            serializer.deserialize(buffer, RequestToken()) as RequestToken

        }
    }
}