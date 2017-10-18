package com.onyx.client

import com.onyx.buffer.BufferPool
import com.onyx.extension.withBuffer
import java.nio.ByteBuffer

class Message(var messageId:Short = 0) {

    var numberOfPackets:Short = 0
    private val packets:MutableList<Packet> = ArrayList()

    companion object {

        private val PACKET_SIZE = 15*1024

        private val MESSAGE_TYPE:Byte = 1
        private val PACKET_TYPE:Byte = 0

        private val PACKET_METADATA_SIZE = (java.lang.Short.BYTES * 2) + java.lang.Byte.BYTES
        private val MESSAGE_METADATA_SIZE = (java.lang.Short.BYTES * 2)

        fun toMessage(buffer: ByteBuffer) : Message {
            return withBuffer(buffer) {
                val message = Message(0)

                // Divide the buffer into individual packets
                while (buffer.hasRemaining()) {
                    val packetBuffer = BufferPool.allocateAndLimit(PACKET_SIZE + PACKET_METADATA_SIZE)
                    packetBuffer.position(PACKET_METADATA_SIZE)
                    while (buffer.hasRemaining() && packetBuffer.hasRemaining())
                        packetBuffer.put(buffer.get())

                    packetBuffer.flip()
                    val packet = Packet(PACKET_TYPE, message.messageId, packetBuffer.limit().toShort(), packetBuffer)
                    packet.write(packetBuffer)
                    packetBuffer.rewind()

                    message.packets.add(packet)
                }

                // Add default packet for message metadata
                message.numberOfPackets = (message.packets.size + 1).toShort() // Add 1 for the message metadata packet

                val packetBuffer = BufferPool.allocateAndLimit(MESSAGE_METADATA_SIZE + MESSAGE_METADATA_SIZE)
                val packet = Packet(MESSAGE_TYPE, message.messageId, packetBuffer.limit().toShort(), packetBuffer)
                packet.write(packetBuffer)
                message.write(packetBuffer)
                packetBuffer.rewind()

                // Add a index 0
                message.packets.add(0, packet)

                return@withBuffer message
            }
        }


        fun toByteBuffer(message:Message):ByteBuffer {
            if(message.packets.size != message.numberOfPackets.toInt()) {
                // TODO: Throw an exception
            }

            val messageBuffer = BufferPool.allocate((message.packets.size - 1 * PACKET_SIZE))
            message.packets.forEachIndexed { index, packet ->
                withBuffer(packet.packetBuffer) {
                    if (index != 0) {
                        packet.packetBuffer.position(PACKET_METADATA_SIZE)
                        messageBuffer.put(packet.packetBuffer)
                    }
                }
            }
            messageBuffer.flip()
            return messageBuffer
        }
    }
}

fun Message.write(buffer:ByteBuffer) {
    buffer.putShort(this.messageId)
    buffer.putShort(this.numberOfPackets)
}

fun Message.read(buffer: ByteBuffer) {
    messageId = buffer.short
    numberOfPackets = buffer.short
}