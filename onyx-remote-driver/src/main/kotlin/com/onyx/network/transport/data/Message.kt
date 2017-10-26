package com.onyx.network.transport.data

import com.onyx.buffer.BufferPool
import com.onyx.buffer.NetworkBufferPool
import com.onyx.network.serialization.ServerSerializer
import com.onyx.extension.withBuffer
import java.nio.ByteBuffer

/**
 * Message is the transport message that travels to and from a network
 * client / server.  It will chunk itself into different packages.  The package
 * size is determined by the transport engine.
 *
 * The message enables un-ordered packets to be set to and from the server.
 * @since 2.0.0
 */
class Message(var messageId:Short = 0) {

    var numberOfPackets:Short = 0
    val packets:MutableList<Packet> = ArrayList()

    companion object {
        val PACKET_METADATA_SIZE = java.lang.Short.BYTES + Integer.BYTES
        val MESSAGE_METADATA_SIZE = java.lang.Short.BYTES
    }
}

/**
 * Converts a byte buffer to a network transport message and associates
 * it to a request token.
 *
 * @param request The request token is used to correlate the request id
 * @return Message with allocated packages
 *
 * @since 2.0.0
 */
fun ByteBuffer.toMessage(request: RequestToken) : Message {
    // Recycle this when done
    return withBuffer(this) {
        val message = Message(request.token)

        // Divide the buffer into individual packets
        while (this.hasRemaining()) {

            // Allocate a buffer from the network pool
            val packetBuffer = NetworkBufferPool.allocate()
            packetBuffer.position(if(message.packets.isEmpty()) (Message.PACKET_METADATA_SIZE + Message.MESSAGE_METADATA_SIZE) else Message.PACKET_METADATA_SIZE)

            while (this.hasRemaining() && packetBuffer.hasRemaining())
                packetBuffer.put(this.get())

            packetBuffer.flip()
            val packet = Packet(packetBuffer.limit(), message.messageId, packetBuffer)

            // write packet metadata at the beginning
            packet.write(packetBuffer)
            packetBuffer.rewind()

            message.packets.add(packet)
        }

        // Update the number of packets
        message.numberOfPackets = message.packets.size.toShort() // Add 1 for the message metadata packet
        val firstPacket = message.packets.first()
        firstPacket.packetBuffer.position(Message.PACKET_METADATA_SIZE)
        // Set the number of packets at the beginning of the buffer
        firstPacket.packetBuffer.putShort(message.numberOfPackets)
        firstPacket.packetBuffer.rewind()

        return@withBuffer message
    }
}

/**
 * Convert a message to a byte buffer that is ready to deserialize.
 * Tis will exclude the packet and message metadata and only contain the original
 * object's data to pass directly into the ServerSerializer.
 *
 * @since 2.0.0
 */
fun Message.toByteBuffer():ByteBuffer {
    val messageBuffer = if(this.packets.size == 1) NetworkBufferPool.allocate() else BufferPool.allocateAndLimit(this.packets.size * NetworkBufferPool.bufferSize)
    this.packets.forEachIndexed { index, packet ->

        // Recycle buffer.  This requires it was originally allocated as a network buffer
        NetworkBufferPool.withBuffer(packet.packetBuffer) {
            // Skip packet metadata
            it.position(if(index == 0) (Message.PACKET_METADATA_SIZE + Message.MESSAGE_METADATA_SIZE) else Message.PACKET_METADATA_SIZE)
            messageBuffer.put(it)
        }
    }
    messageBuffer.flip()
    return messageBuffer
}

/**
 * Convert the message to a RequestToken
 *
 * @param serializer Serializer to use to deserialize request token
 * @return Request token correlated to this message
 *
 * @since 2.0.0
 */
fun Message.toRequest(serializer: ServerSerializer): RequestToken {
    val buffer = toByteBuffer()
    return if(this.packets.size > 1) {
        withBuffer(buffer) {
            serializer.deserialize(buffer, RequestToken()) as RequestToken
        }
    } else {
        NetworkBufferPool.withBuffer(buffer) {
            serializer.deserialize(buffer, RequestToken()) as RequestToken
        }
    }
}