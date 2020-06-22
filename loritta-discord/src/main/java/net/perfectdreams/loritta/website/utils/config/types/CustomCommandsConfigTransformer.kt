package net.perfectdreams.loritta.website.utils.config.types

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mrpowergamerbr.loritta.dao.ServerConfig
import com.mrpowergamerbr.loritta.utils.loritta
import kotlinx.serialization.builtins.list
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.entities.Guild
import net.perfectdreams.loritta.serializable.CustomCommand
import net.perfectdreams.loritta.tables.servers.CustomGuildCommands
import net.perfectdreams.loritta.utils.CustomCommandCodeType
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select

object CustomCommandsConfigTransformer : ConfigTransformer {
    override val payloadType: String = "custom_commands"
    override val configKey: String = "customCommands"

    override suspend fun toJson(guild: Guild, serverConfig: ServerConfig): JsonElement {
        val customCommands = loritta.newSuspendedTransaction {
            CustomGuildCommands.select {
                CustomGuildCommands.guild eq serverConfig.id.value
            }.map {
                CustomCommand(
                        it[CustomGuildCommands.label],
                        it[CustomGuildCommands.code]
                )
            }
        }

        return JsonParser.parseString(Json.stringify(CustomCommand.serializer().list, customCommands))
    }

    override suspend fun fromJson(guild: Guild, serverConfig: ServerConfig, payload: JsonObject) {
        loritta.newSuspendedTransaction {
            // First we delete all of them...
            CustomGuildCommands.deleteWhere {
                CustomGuildCommands.guild eq serverConfig.id
            }

            // And now we reinsert the new commands
            val entries = payload["entries"].array

            for (entry in entries) {
                val label = entry["label"].string
                val code = entry["code"].string

                CustomGuildCommands.insert {
                    it[CustomGuildCommands.guild] = serverConfig.id
                    it[CustomGuildCommands.enabled] = true
                    it[CustomGuildCommands.label] = label
                    it[CustomGuildCommands.codeType] = CustomCommandCodeType.KOTLIN
                    it[CustomGuildCommands.code] = code
                }
            }
        }
    }
}