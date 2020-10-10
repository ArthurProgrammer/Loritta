package net.perfectdreams.loritta.plugin.helpinghands

import net.perfectdreams.loritta.platform.discord.LorittaDiscord
import net.perfectdreams.loritta.platform.discord.plugin.LorittaDiscordPlugin
import net.perfectdreams.loritta.plugin.helpinghands.commands.CoinFlipBetCommand
import net.perfectdreams.loritta.plugin.helpinghands.commands.DailyInactivityTaxExecutor
import net.perfectdreams.loritta.plugin.helpinghands.commands.RepListCommand
import net.perfectdreams.loritta.plugin.helpinghands.commands.TestCommand
import net.perfectdreams.loritta.plugin.helpinghands.utils.DailyInactivityTaxUtils

class HelpingHandsPlugin(name: String, loritta: LorittaDiscord) : LorittaDiscordPlugin(name, loritta) {
	override fun onEnable() {
		registerCommands(
				CoinFlipBetCommand(this),
				RepListCommand(this),
				TestCommand(this)
		)

		if (loritta.isMaster)
			launch(DailyInactivityTaxUtils.createAutoInactivityTask())
		this.loriToolsExecutors += DailyInactivityTaxExecutor
	}

	override fun onDisable() {
		super.onDisable()
	}
}