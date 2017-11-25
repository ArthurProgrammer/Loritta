package com.mrpowergamerbr.loritta.commands.vanilla.`fun`

import com.mrpowergamerbr.loritta.Loritta
import com.mrpowergamerbr.loritta.commands.CommandBase
import com.mrpowergamerbr.loritta.commands.CommandCategory
import com.mrpowergamerbr.loritta.commands.CommandContext
import com.mrpowergamerbr.loritta.userdata.LorittaProfile
import com.mrpowergamerbr.loritta.utils.Constants
import com.mrpowergamerbr.loritta.utils.LoriReply
import com.mrpowergamerbr.loritta.utils.LorittaUtils
import com.mrpowergamerbr.loritta.utils.loritta
import com.mrpowergamerbr.loritta.utils.makeRoundedCorners
import com.mrpowergamerbr.loritta.utils.onReactionAdd
import com.mrpowergamerbr.loritta.utils.onResponseByAuthor
import com.mrpowergamerbr.loritta.utils.save
import net.dv8tion.jda.core.EmbedBuilder
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileInputStream
import javax.imageio.ImageIO

class TamagotchiCommand : CommandBase("tamagotchi") {
	override fun getCategory(): CommandCategory {
		return CommandCategory.MAGIC
	}

	override fun run(context: CommandContext) {
		if ((context.args.isNotEmpty() && context.args[0] == "reset") || context.userHandle.id != Loritta.config.ownerId) {
			context.lorittaUser.profile.tamagotchi = null
			loritta save context.lorittaUser.profile
			context.reply(
					":^)"
			)
			return
		}

		if (context.lorittaUser.profile.tamagotchi == null) {
			handlePetCreation(context)
		} else {
			handleGameplay(context)
		}
	}

	fun handlePetCreation(context: CommandContext) {
		// Usuário quer um Tamagotchi
		val message = context.reply(
				LoriReply(
						message = "Olá! Você quer um bichinho virtual?",
						prefix = "<:fluffy:372454445721845761>"
				)
		)

		message.addReaction("vieirinha:339905091425271820").complete()

		message.onReactionAdd(context, {
			if (it.user.id != context.userHandle.id) {
				return@onReactionAdd
			}

			if (it.reactionEmote.name == "vieirinha") {
				message.delete().complete()

				val newName = context.reply(
						LoriReply(
								message = "Qual será o nome do seu novo bichinho virtual!",
								prefix = "\uD83E\uDD5A"
						)
				)

				newName.onResponseByAuthor(context, {
					val name = it.message.content

					val petGender = context.reply(
							LoriReply(
									message = "Qual será o gênero de `${name}`?"
							)
					)

					petGender.onReactionAdd(context, {
						val gender = LorittaProfile.TamagotchiPet.PetGender.MALE

						petGender.delete().complete()

						val petType = context.reply(
								LoriReply(
										message = "Como será o `${name}`?"
								)
						)

						petType.onResponseByAuthor(context, {
							if (it.message.emotes.isEmpty()) {
								context.reply(
										LoriReply(
												message = "Por favor, envie um emoji!",
												prefix = Constants.ERROR
										)
								)
								return@onResponseByAuthor
							}

							val emoji = it.message.emotes[0]
							val emoteId = emoji.imageUrl.split("/").last()

							val image = LorittaUtils.downloadImage(emoji.imageUrl)

							ImageIO.write(image, "png", File(Loritta.FOLDER, "pets/$emoteId"))

							petType.delete().complete()

							val pet = LorittaProfile.TamagotchiPet(name, gender, emoteId)
							val profile = loritta.getLorittaProfileForUser(context.userHandle.id)
							profile.tamagotchi = pet
							loritta save profile
							handleGameplay(context)
						})
					})

					newName.delete().complete()
				})
			}
		})
	}

	fun getPet(context: CommandContext): LorittaProfile.TamagotchiPet {
		val profile = loritta.getLorittaProfileForUser(context.userHandle.id)

		val pet = profile.tamagotchi!!

		if (pet.lastUpdate == 0L)
			pet.lastUpdate = System.currentTimeMillis()

		val diff = System.currentTimeMillis() - pet.lastUpdate

		// calcular a perda de necessidades
		// + ou - 2 horas para o pet morrer sem interação do usuário
		var hungerDown = 9f // 0.00013f
		var happinessDown = 9f

		if (pet.upgrades.contains(LorittaProfile.TamagotchiPet.PetUpgrades.FAN_ART)) {
			happinessDown -= 1f
		}

		pet.hunger -= (diff / 1000) * (hungerDown * 0.001f)
		pet.happiness -= (diff / 1000) * (happinessDown * 0.001f)

		return pet
	}

	fun applyPetChanges(context: CommandContext, tamagotchi: LorittaProfile.TamagotchiPet): Boolean {
		val profile = loritta.getLorittaProfileForUser(context.userHandle.id)

		tamagotchi.lastUpdate = System.currentTimeMillis()

		profile.tamagotchi = tamagotchi

		loritta save profile
		return false
	}

	fun handleGameplay(context: CommandContext) {
		val pet = getPet(context)
		applyPetChanges(context, pet)
		val base = BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB)
		val playfield = ImageIO.read(File(Loritta.FOLDER, "playfield.png"))
		val playfieldGraphics = playfield.graphics
		val graphics = base.graphics
		val wrapper = ImageIO.read(File(Loritta.FOLDER, "tamagotchi.png"))

		if (pet.upgrades.contains(LorittaProfile.TamagotchiPet.PetUpgrades.FAN_ART)) {
			val upgrade = ImageIO.read(File(Loritta.FOLDER, "upgrade_fanart.png"))
			playfieldGraphics.drawImage(upgrade, 0, 0, null)
		}

		val petImage = ImageIO.read(File(Loritta.FOLDER, "pets/${pet.petType}"))
		val petImage32 = petImage.getScaledInstance(64, 64, BufferedImage.SCALE_SMOOTH)

		playfieldGraphics.drawImage(petImage32, 163, 109, null)
		graphics.drawImage(playfield, 5, 5, null)
		graphics.drawImage(wrapper, 0, 0, null)

		val minecraftia = Font.createFont(Font.TRUETYPE_FONT,
				FileInputStream(File(Loritta.FOLDER + "Volter__28Goldfish_29.ttf"))) // A fonte para colocar os discriminators

		val minecraftia8 = minecraftia.deriveFont(9f)
		val minecraftia16 = minecraftia.deriveFont(18f)

		graphics.font = minecraftia16
		graphics.drawString("Sobre", 5, 206)
		graphics.drawString("Necessidades", 160, 206)

		graphics.font = minecraftia8
		graphics.drawString("Nome: ${pet.petName}", 5, 216)
		graphics.drawString("Gênero: ${pet.gender}", 5, 226)

		graphics.drawString("Fome", 160, 216)

		graphics.color = Color.BLACK
		graphics.fillRect(160, 218, 64, 6)
		graphics.color = Color.GREEN
		graphics.fillRect(160, 218, (64 * pet.hunger).toInt(), 6)

		graphics.color = Color.WHITE
		graphics.drawString("Diversão", 160, 233)

		graphics.color = Color.BLACK
		graphics.fillRect(160, 235, 64, 6)
		graphics.color = Color.GREEN
		graphics.fillRect(160, 235, (64 * pet.happiness).toInt(), 6)

		val message = context.sendFile(base.makeRoundedCorners(14), "tamagotchi.png", "Tamagotchi Test\nHunger: ${pet.hunger}\nFun: ${pet.happiness}\nUpgrades: ${pet.upgrades.joinToString(", ")}")

		message.onReactionAdd(context, {
			if (it.user.id != context.userHandle.id) {
				return@onReactionAdd
			}

			message.delete().complete()

			if (it.reactionEmote.name == "\uD83C\uDF57") {
				// Comida
				handleFood(context)
			}

			if (it.reactionEmote.name == "⏫") {
				// Upgrades
				handleUpgrades(context)
			}

			if (it.reactionEmote.name == "\uD83C\uDF1F") {
				pet.hunger = 1f
				pet.happiness = 1f
				applyPetChanges(context, pet)
				handleGameplay(context)
			}
		})
		message.addReaction("\uD83C\uDF57").complete()
		message.addReaction("⏫").complete()
		message.addReaction("\uD83C\uDF1F").complete()
	}

	fun handleFood(context: CommandContext) {
		val pet = getPet(context)

		val foodMap = mutableMapOf(
				"\uD83C\uDF5E" to Pair(0.025f, 5),
				"\uD83C\uDF6A" to Pair(0.05f, 7),
				"\uD83C\uDF5C" to Pair(0.075f, 14)
		)

		val message = context.sendMessage(context.getAsMention(true), EmbedBuilder().apply {
			setTitle("Comidas...")
			appendDescription("\uD83C\uDF5E - Um simples pão ~ 5 sonhos\n")
			appendDescription("\uD83C\uDF6A - Cookie, não é biscoito e nem bolacha ~ 7 sonhos\n")
			appendDescription("\uD83C\uDF5C - Miojo, para quando você quer uma comida rápida ~ 14 sonhos\n")
		}.build())

		message.onReactionAdd(context, {
			if (it.user.id != context.userHandle.id) {
				return@onReactionAdd
			}

			message.delete().complete()

			if (foodMap.containsKey(it.reactionEmote.name)) {
				val pair = foodMap[it.reactionEmote.name]!!

				pet.hunger += pair.first

				applyPetChanges(context, pet)
				handleGameplay(context)
			}
		})

		foodMap.keys.forEach {
			message.addReaction(it).complete()
		}
	}

	fun handleUpgrades(context: CommandContext) {
		val pet = getPet(context)


		val upgradeMap = mutableMapOf(
				"\uD83D\uDDBC" to Pair(5, LorittaProfile.TamagotchiPet.PetUpgrades.FAN_ART),
				"\uD83D\uDCFA" to Pair(25, LorittaProfile.TamagotchiPet.PetUpgrades.TELEVISION),
				"\uD83D\uDCE1" to Pair(25, LorittaProfile.TamagotchiPet.PetUpgrades.ANTENNA)
		)

		val message = context.sendMessage(context.getAsMention(true), EmbedBuilder().apply {
			setTitle("Upgrades...")
			appendDescription("\uD83D\uDDBC - Um quadro da Loritta ~ 5 sonhos\n")
			appendDescription("\uD83D\uDCFA - Uma televisão para a sua casa\n")
			appendDescription("\uD83D\uDCE1 - TV aberta é do passado, o negócio é TV fechada!\n")
		}.build())

		message.onReactionAdd(context, {
			if (it.user.id != context.userHandle.id) {
				return@onReactionAdd
			}

			message.delete().complete()

			if (upgradeMap.containsKey(it.reactionEmote.name)) {
				val pair = upgradeMap[it.reactionEmote.name]!!

				pet.upgrades.add(pair.second)

				applyPetChanges(context, pet)
				handleGameplay(context)
			}
		})

		upgradeMap.keys.forEach {
			message.addReaction(it).complete()
		}
	}
}

fun main(args: Array<String>) {
	while (true) {
		println("Quanto?")
		val fall = readLine()!!.toFloat()

		var wow = 1f

		var seconds = 0
		while (wow > 0f) {
			seconds += 1
			wow -= fall
		}

		println("Irá demorar $seconds segundos (${seconds / 60} minutos) até acabar...")
	}
}