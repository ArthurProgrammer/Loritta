package net.perfectdreams.loritta.parallax.api.packet

import kotlinx.serialization.Serializable

@Serializable
class ParallaxAckSendMessagePacket(
        val message: String
) : ParallaxPacket