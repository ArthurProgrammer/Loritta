package com.mrpowergamerbr.loritta.commands.vanilla.`fun`

import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.commands.CommandBase
import com.mrpowergamerbr.loritta.commands.CommandCategory
import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.utils.ImageUtils
import com.mrpowergamerbr.loritta.utils.LorittaUtils
import com.mrpowergamerbr.loritta.utils.f
import com.mrpowergamerbr.loritta.utils.locale.BaseLocale
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.entities.User
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

class ShipCommand : CommandBase("ship") {
    override fun getDescription(locale: BaseLocale): String {
        return locale.SHIP_DESCRIPTION.f()
    }

	override fun getExample(): List<String> {
		return listOf("@Loritta @SparklyBot")
	}

	override fun getCategory(): CommandCategory {
		return CommandCategory.FUN
	}

	override fun getUsage(): String {
		return "<usuário 1> <usuário 2>"
	}

	override fun needsToUploadFiles(): Boolean {
		return true
	}

    override fun run(context: CommandContext, locale: BaseLocale) {
		val user1 = LorittaUtils.getUserFromContext(context, 0)
		val user2 = LorittaUtils.getUserFromContext(context, 1)
		
		if (user1 != null && user2 != null) {
			var texto = context.getAsMention(true) + "\n💖 **${context.locale["SHIP_NEW_COUPLE"]}** 💖\n"

			texto += "`${user1.name}`\n`${user2.name}`\n"

			var name1 = user1.name.substring(0..(user1.name.length / 2))
			var name2 = user2.name.substring(user2.name.length / 2..user2.name.length - 1)
			var shipName = name1 + name2

			// Para motivos de cálculos, nós iremos criar um "real ship name"
			// Que é só o nome do ship... mas em ordem alfabética!
			var realShipName = shipName
			if (1 > user2.name.compareTo(user1.name)) {
				var reversedMentionedUsers = mutableListOf<User>()
				reversedMentionedUsers.add(user2)
				reversedMentionedUsers.add(user1)
				name1 = reversedMentionedUsers[0].name.substring(0..(reversedMentionedUsers[0].name.length / 2))
				name2 = reversedMentionedUsers[1].name.substring(reversedMentionedUsers[1].name.length / 2..reversedMentionedUsers[1].name.length - 1)
				realShipName = name1 + name2
			}

			var random = SplittableRandom(realShipName.hashCode().toLong() + 1)

			var percentage = random.nextInt(0, 101)

			// Loritta presa amanhã por manipulação de resultados
			if (user1.id == Loritta.config.clientId || user2.id == Loritta.config.clientId) {
				if (user1.id != Loritta.config.ownerId && user2.id != Loritta.config.ownerId) {
					percentage = random.nextInt(0, 51)
				}
				if (user1.id == "273192139460968449" || user2.id == "273192139460968449") {
					percentage = 0
				}
			}

			var friendzone: String

			friendzone = if (random.nextBoolean()) {
				user1.name
			} else {
				user2.name
			}

			var messages = listOf("Isto nunca deverá aparecer!")
			if (percentage >= 90) {
				messages = context.locale.SHIP_valor90
			} else if (percentage >= 80) {
				messages = context.locale.SHIP_valor80
			} else if (percentage >= 70) {
				messages = context.locale.SHIP_valor70
			} else if (percentage >= 60) {
				messages = context.locale.SHIP_valor60
			} else if (percentage >= 50) {
				messages = context.locale.SHIP_valor50
			} else if (percentage >= 40) {
				messages = context.locale.SHIP_valor40
			} else if (percentage >= 30) {
				messages = context.locale.SHIP_valor30
			} else if (percentage >= 20) {
				messages = context.locale.SHIP_valor20
			} else if (percentage >= 10) {
				messages = context.locale.SHIP_valor10
			} else if (percentage >= 0) {
				messages = context.locale.SHIP_valor0
			}

			var emoji: BufferedImage
			if (percentage >= 50) {
				emoji = ImageIO.read(File(Loritta.ASSETS + "heart.png"))
			} else if (percentage >= 30) {
				emoji = ImageIO.read(File(Loritta.ASSETS + "shrug.png"))
			} else {
				emoji = ImageIO.read(File(Loritta.ASSETS + "crying.png"))
			}

			var resizedEmoji = emoji.getScaledInstance(100, 100, BufferedImage.SCALE_SMOOTH)

			var message = messages[random.nextInt(messages.size)]
			message = message.replace("%user%", friendzone)
			message = message.replace("%ship%", "`$shipName`")
			texto += "$message"

			var avatar1Old = LorittaUtils.downloadImage(user1.effectiveAvatarUrl + "?size=128")
			var avatar2Old = LorittaUtils.downloadImage(user2.effectiveAvatarUrl + "?size=128")

			var avatar1 = avatar1Old
			var avatar2 = avatar2Old

			if (avatar1.height != 128 && avatar1.width != 128) {
				avatar1 = ImageUtils.toBufferedImage(avatar1.getScaledInstance(128, 128, BufferedImage.SCALE_SMOOTH))
			}

			if (avatar2.height != 128 && avatar2.width != 128) {
				avatar2 = ImageUtils.toBufferedImage(avatar2.getScaledInstance(128, 128, BufferedImage.SCALE_SMOOTH))
			}

			var image = BufferedImage(384, 128, BufferedImage.TYPE_INT_ARGB)
			var graphics = image.graphics
			graphics.drawImage(avatar1, 0, 0, null)
			graphics.drawImage(resizedEmoji, 142, 10, null)
			graphics.drawImage(avatar2, 256, 0, null)

			var embed = EmbedBuilder()
			embed.setColor(Color(255, 132, 188))

			var text = "[`"
			for (i in 0..100 step 10) {
				if (percentage >= i) {
					text += "█"
				} else {
					text += "."
				}
			}
			text += "`]"
			embed.setDescription("**$percentage%** $text")
			embed.setImage("attachment://ships.png")
			var msgBuilder = MessageBuilder().append(texto)
			msgBuilder.setEmbed(embed.build())
			context.sendFile(image, "ships.png", msgBuilder.build())
		} else {
			this.explain(context)
		}
    }
}