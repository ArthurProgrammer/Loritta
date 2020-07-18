package com.mrpowergamerbr.loritta

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.salomonbrys.kotson.*
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mrpowergamerbr.loritta.commands.CommandManager
import com.mrpowergamerbr.loritta.dao.Profile
import com.mrpowergamerbr.loritta.dao.ProfileSettings
import com.mrpowergamerbr.loritta.dao.ServerConfig
import com.mrpowergamerbr.loritta.listeners.*
import com.mrpowergamerbr.loritta.network.Databases
import com.mrpowergamerbr.loritta.tables.*
import com.mrpowergamerbr.loritta.threads.RaffleThread
import com.mrpowergamerbr.loritta.threads.RemindersThread
import com.mrpowergamerbr.loritta.threads.UpdateStatusThread
import com.mrpowergamerbr.loritta.utils.*
import com.mrpowergamerbr.loritta.utils.config.GeneralConfig
import com.mrpowergamerbr.loritta.utils.config.GeneralDiscordConfig
import com.mrpowergamerbr.loritta.utils.config.GeneralDiscordInstanceConfig
import com.mrpowergamerbr.loritta.utils.config.GeneralInstanceConfig
import com.mrpowergamerbr.loritta.utils.debug.DebugLog
import com.mrpowergamerbr.loritta.utils.locale.Gender
import com.mrpowergamerbr.loritta.utils.temmieyoutube.TemmieYouTube
import com.mrpowergamerbr.loritta.website.LorittaWebsite
import kotlinx.coroutines.*
import mu.KotlinLogging
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.utils.ChunkingFilter
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import net.perfectdreams.loritta.api.platform.PlatformFeature
import net.perfectdreams.loritta.dao.Payment
import net.perfectdreams.loritta.platform.discord.DiscordEmoteManager
import net.perfectdreams.loritta.platform.discord.LorittaDiscord
import net.perfectdreams.loritta.platform.discord.commands.DiscordCommandManager
import net.perfectdreams.loritta.platform.discord.utils.BucketedController
import net.perfectdreams.loritta.platform.discord.utils.RateLimitChecker
import net.perfectdreams.loritta.tables.*
import net.perfectdreams.loritta.tables.servers.CustomGuildCommands
import net.perfectdreams.loritta.tables.servers.Giveaways
import net.perfectdreams.loritta.tables.servers.ServerRolePermissions
import net.perfectdreams.loritta.tables.servers.moduleconfigs.*
import net.perfectdreams.loritta.twitch.TwitchAPI
import net.perfectdreams.loritta.utils.*
import net.perfectdreams.loritta.utils.payments.PaymentReason
import net.perfectdreams.mercadopago.MercadoPago
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Loritta's main class, where everything (and anything) can happen!
 *
 * @author MrPowerGamerBR
 */
class Loritta(discordConfig: GeneralDiscordConfig, discordInstanceConfig: GeneralDiscordInstanceConfig, config: GeneralConfig, instanceConfig: GeneralInstanceConfig) : LorittaDiscord(discordConfig, discordInstanceConfig, config, instanceConfig) {
	// ===[ STATIC ]===
	companion object {
		// ===[ LORITTA ]===
		@JvmField
		var FOLDER = "/home/servers/loritta/" // Pasta usada na Loritta
		@JvmField
		var ASSETS = "/home/servers/loritta/assets/" // Pasta de assets da Loritta
		@JvmField
		var TEMP = "/home/servers/loritta/temp/" // Pasta usada para coisas temporarias
		@JvmField
		var LOCALES = "/home/servers/loritta/locales/" // Pasta usada para as locales
		@JvmField
		var FRONTEND = "/home/servers/loritta/frontend/" // Pasta usada para as locales

		// ===[ UTILS ]===
		@JvmStatic
		val RANDOM = SplittableRandom() // Um splittable RANDOM global, para não precisar ficar criando vários (menos GC)
		@JvmStatic
		var GSON = Gson() // Gson
		@JvmStatic
		val JSON_PARSER = JsonParser() // Json Parser
		@JvmStatic
		lateinit var youtube: TemmieYouTube // API key do YouTube, usado em alguns comandos

		private val logger = KotlinLogging.logger {}
	}

	// ===[ LORITTA ]===
	// All features!!! :3
	override val supportedFeatures = PlatformFeature.values().toMutableList()

	var lorittaShards = LorittaShards() // Shards da Loritta
	val coroutineExecutor = createThreadPool("Coroutine Executor Thread %d")
	val coroutineDispatcher = coroutineExecutor.asCoroutineDispatcher() // Coroutine Dispatcher

	fun createThreadPool(name: String): ExecutorService {
		return if (FeatureFlags.isEnabled(this, FeatureFlags.Names.USE_FIXED_THREAD_POOL))
			Executors.newFixedThreadPool(16, ThreadFactoryBuilder().setNameFormat(name).build())
		else
			Executors.newCachedThreadPool(ThreadFactoryBuilder().setNameFormat(name).build())
	}

	val legacyCommandManager = CommandManager(this) // Nosso command manager
	val commandManager = DiscordCommandManager(this)
	var messageInteractionCache = Caffeine.newBuilder().maximumSize(1000L).expireAfterAccess(3L, TimeUnit.MINUTES).build<Long, MessageInteractionFunctions>().asMap()

	var ignoreIds = mutableSetOf<Long>() // IDs para serem ignorados nesta sessão
	val userCooldown = Caffeine.newBuilder().expireAfterAccess(30L, TimeUnit.SECONDS).maximumSize(100).build<Long, Long>().asMap()
	val apiCooldown = Caffeine.newBuilder().expireAfterAccess(30L, TimeUnit.SECONDS).maximumSize(100).build<String, Long>().asMap()

	var discordListener = DiscordListener(this) // Vamos usar a mesma instância para todas as shards
	var eventLogListener = EventLogListener(this) // Vamos usar a mesma instância para todas as shards
	var messageListener = MessageListener(this)
	var voiceChannelListener = VoiceChannelListener(this)
	var channelListener = ChannelListener(this)
	var builder: DefaultShardManagerBuilder

	lateinit var raffleThread: RaffleThread
	lateinit var bomDiaECia: BomDiaECia

	lateinit var website: LorittaWebsite

	var newWebsite: net.perfectdreams.loritta.website.LorittaWebsite? = null
	var newWebsiteThread: Thread? = null

	var twitch = TwitchAPI(config.twitch.clientId, config.twitch.clientSecret)
	var twitch2 = TwitchAPI(config.twitch2.clientId, config.twitch2.clientSecret)
	var twitch3 = TwitchAPI(config.twitch3.clientId, config.twitch3.clientSecret)
	var twitch4 = TwitchAPI(config.twitch4.clientId, config.twitch4.clientSecret)
	var twitch5 = TwitchAPI(config.twitch5.clientId, config.twitch5.clientSecret)
	var twitch6 = TwitchAPI(config.twitch6.clientId, config.twitch6.clientSecret)
	var twitch7 = TwitchAPI(config.twitch7.clientId, config.twitch7.clientSecret)
	var twitch8 = TwitchAPI(config.twitch8.clientId, config.twitch8.clientSecret)
	val connectionManager = ConnectionManager()
	val mercadoPago = MercadoPago(
			clientId = config.mercadoPago.clientId,
			clientSecret = config.mercadoPago.clientSecret
	)
	var patchData = PatchData()
	var sponsors: List<Sponsor> = listOf()
	val cachedRetrievedArtists = CacheBuilder.newBuilder().expireAfterWrite(7, TimeUnit.DAYS)
			.build<Long, Optional<CachedUserInfo>>()
	val tweetTracker = TweetTracker(this)
	var bucketedController: BucketedController? = null
	val rateLimitChecker = RateLimitChecker(this)

	init {
		LorittaLauncher.loritta = this
		FOLDER = instanceConfig.loritta.folders.root
		ASSETS = instanceConfig.loritta.folders.assets
		TEMP = instanceConfig.loritta.folders.temp
		LOCALES = instanceConfig.loritta.folders.locales
		FRONTEND = instanceConfig.loritta.website.folder

		val dispatcher = Dispatcher()
		dispatcher.maxRequestsPerHost = discordConfig.discord.maxRequestsPerHost

		val okHttpBuilder = OkHttpClient.Builder()
				.dispatcher(dispatcher)
				.connectTimeout(discordConfig.okHttp.connectTimeout, TimeUnit.SECONDS) // O padrão de timeouts é 10 segundos, mas vamos aumentar para evitar problemas.
				.readTimeout(discordConfig.okHttp.readTimeout, TimeUnit.SECONDS)
				.writeTimeout(discordConfig.okHttp.writeTimeout, TimeUnit.SECONDS)
				.protocols(listOf(Protocol.HTTP_1_1)) // https://i.imgur.com/FcQljAP.png

		builder = DefaultShardManagerBuilder.create(discordConfig.discord.clientToken, discordConfig.discord.intents)
				.disableCache(CacheFlag.values().toList())
				.enableCache(discordConfig.discord.cacheFlags)
				.setChunkingFilter(ChunkingFilter.NONE) // No chunking policy because trying to load all members is hard
				.setMemberCachePolicy(MemberCachePolicy.ALL) // Cache all members!!
				.apply {
					if (loritta.discordConfig.shardController.enabled) {
						logger.info { "Using shard controller (for bots with \"sharding for very large bots\" to manage shards!" }
						bucketedController = BucketedController(discordConfig.shardController.buckets)
						this.setSessionController(bucketedController)
					}
				}
				.setShardsTotal(discordConfig.discord.maxShards)
				.setShards(lorittaCluster.minShard.toInt(), lorittaCluster.maxShard.toInt())
				.setStatus(discordConfig.discord.status)
				.setBulkDeleteSplittingEnabled(false)
				.setHttpClientBuilder(okHttpBuilder)
				.addEventListeners(
						discordListener,
						eventLogListener,
						messageListener,
						voiceChannelListener,
						channelListener
				)
	}

	val lorittaCluster: GeneralConfig.LorittaClusterConfig
		get() {
			return config.clusters.first { it.id == instanceConfig.loritta.currentClusterId }
		}

	val lorittaInternalApiKey: GeneralConfig.LorittaConfig.WebsiteConfig.AuthenticationKey
		get() {
			return config.loritta.website.apiKeys.first { it.description == "Loritta Internal Key" }
		}

	// Inicia a Loritta
	fun start() {
		logger.info { "Creating folders..." }
		File(FOLDER).mkdirs()
		File(ASSETS).mkdirs()
		File(TEMP).mkdirs()
		File(LOCALES).mkdirs()
		File(FRONTEND).mkdirs()
		File(loritta.instanceConfig.loritta.folders.plugins).mkdirs()
		File(loritta.instanceConfig.loritta.folders.fanArts).mkdirs()

		logger.info { "Success! Loading locales..." }

		loadLocales()
		loadLegacyLocales()

		youtube = TemmieYouTube()

		logger.info { "Success! Loading fan arts..." }
		if (loritta.isMaster) // Apenas o master cluster deve carregar as fan arts, os outros clusters irão carregar pela API
			loadFanArts()

		logger.info { "Success! Loading emotes..." }

		Emotes.emoteManager = DiscordEmoteManager().also { it.loadEmotes() }

		logger.info { "Success! Connecting to the database..." }

		initPostgreSql()

		// Vamos criar todas as instâncias necessárias do JDA para nossas shards
		logger.info { "Sucesso! Iniciando Loritta (Discord Bot)..." }

		val shardManager = builder.build()
		lorittaShards.shardManager = shardManager

		logger.info { "Sucesso! Iniciando plugins da Loritta..." }

		pluginManager.loadPlugins()

		logger.info("Sucesso! Iniciando Loritta (Website)...")

		website = LorittaWebsite(this, instanceConfig.loritta.website.url, instanceConfig.loritta.website.folder) // Apenas para rodar o init, que preenche uns companion objects marotos
		startWebServer()

		logger.info { "Sucesso! Iniciando threads da Loritta..." }

		logger.info { "Iniciando Update Status Thread..." }
		UpdateStatusThread().start() // Iniciar thread para atualizar o status da Loritta

		logger.info { "Iniciando Tasks..." }
		LorittaTasks.startTasks()

		logger.info { "Iniciando threads de reminders..." }
		RemindersThread().start()

		logger.info { "Iniciando bom dia & cia..." }
		bomDiaECia = BomDiaECia()

		if (loritta.isMaster) { // Apenas o cluster principal deve criar a stream, para evitar que tenha várias streams logando ao mesmo tempo (e tomando rate limit)
			logger.info { "Iniciando streams de tweets..." }
			tweetTracker.updateStreams()
		}

		logger.info { "Carregando raffle..." }
		val raffleFile = File(FOLDER, "raffle.json")

		if (raffleFile.exists()) {
			val json = JSON_PARSER.parse(raffleFile.readText()).obj

			RaffleThread.started = json["started"].long
			RaffleThread.lastWinnerId = json["lastWinnerId"].nullString
			RaffleThread.lastWinnerPrize = json["lastWinnerPrize"].nullInt ?: 0
			val userIdArray = json["userIds"].nullArray

			if (userIdArray != null)
				RaffleThread.userIds = GSON.fromJson(userIdArray)
		}

		raffleThread = RaffleThread()
		raffleThread.start()

		DebugLog.startCommandListenerThread()

		GlobalScope.launch(coroutineDispatcher) {
			connectionManager.updateProxies()
		}
		// Ou seja, agora a Loritta está funcionando, Yay!
	}

	fun initPostgreSql() {
		logger.info("Iniciando PostgreSQL...")

		transaction(Databases.loritta) {
			SchemaUtils.createMissingTablesAndColumns(
					StoredMessages,
					Profiles,
					UserSettings,
					Reminders,
					Reputations,
					Dailies,
					Marriages,
					RegisterConfigs,
					Mutes,
					Warns,
					GuildProfiles,
					Timers,
					Giveaways,
					ReactionOptions,
					ServerConfigs,
					DonationKeys,
					Payments,
					ShipEffects,
					BotVotes,
					StoredMessages,
					StarboardMessages,
					Sponsors,
					EconomyConfigs,
					ExecutedCommandsLog,
					BlacklistedGuilds,
					RolesByExperience,
					LevelAnnouncementConfigs,
					LevelConfigs,
					AuditLog,
					ExperienceRoleRates,
					BomDiaECiaWinners,
					TrackedTwitterAccounts,
					TrackedRssFeeds,
					DefaultRssFeeds,
					SonhosTransaction,
					TrackedYouTubeAccounts,
					TrackedTwitchAccounts,
					CachedYouTubeChannelIds,
					SonhosBundles,
					Backgrounds,
					Sets,
					DailyShops,
					DailyShopItems,
					BackgroundPayments,
					CachedDiscordUsers,
					SentYouTubeVideoIds,
					SpicyStacktraces,
					BannedIps,
					WhitelistedTransactionIds,
					Requires2FAChecksUsers,
					StarboardConfigs,
					MiscellaneousConfigs,
					EventLogConfigs,
					AutoroleConfigs,
					InviteBlockerConfigs,
					ServerRolePermissions,
					WelcomerConfigs,
					CustomGuildCommands,
					MemberCounterChannelConfigs,
					ModerationConfigs,
					WarnActions,
					ModerationPunishmentMessagesConfig,
					BannedUsers
			)
		}
	}

	fun startWebServer() {
		// Carregar os blog posts
		loritta.newWebsiteThread = thread(true, name = "Website Thread") {
			val nWebsite = net.perfectdreams.loritta.website.LorittaWebsite(loritta)
			nWebsite.loadBlogPosts()
			loritta.newWebsite = nWebsite
			nWebsite.start()
		}
	}

	fun stopWebServer() {
		loritta.newWebsite?.stop()
		loritta.newWebsiteThread?.interrupt()
	}

	/**
	 * Gets an user's profile background
	 *
	 * @param id the user's ID
	 * @return the background image
	 */
	suspend fun getUserProfileBackground(id: Long) = getUserProfileBackground(getOrCreateLorittaProfile(id))

	/**
	 * Loads the server configuration of a guild
	 *
	 * @param guildId the guild's ID
	 * @return        the server configuration
	 */
	fun getOrCreateServerConfig(guildId: Long, loadFromCache: Boolean = false): ServerConfig {
		if (loadFromCache)
			cachedServerConfigs.getIfPresent(guildId)?.let { return it }

		return transaction(Databases.loritta) {
			_getOrCreateServerConfig(guildId)
		}
	}

	/**
	 * Loads the server configuration of a guild in a coroutine
	 *
	 * @param guildId the guild's ID
	 * @return        the server configuration
	 */
	suspend fun getOrCreateServerConfigAsync(guildId: Long, loadFromCache: Boolean = false): ServerConfig {
		if (loadFromCache)
			cachedServerConfigs.getIfPresent(guildId)?.let { return it }

		return newSuspendedTransaction(Dispatchers.IO, Databases.loritta) { _getOrCreateServerConfig(guildId) }
	}

	/**
	 * Loads the server configuration of a guild, deferred
	 *
	 * @param guildId the guild's ID
	 * @return        the server configuration
	 */
	suspend fun getOrCreateServerConfigDeferred(guildId: Long, loadFromCache: Boolean = false): Deferred<ServerConfig> {
		if (loadFromCache)
			cachedServerConfigs.getIfPresent(guildId)?.let { return GlobalScope.async(coroutineDispatcher) { it } }

		val job = suspendedTransactionAsync(Dispatchers.IO, Databases.loritta) { _getOrCreateServerConfig(guildId) }

		return job
	}

	private fun _getOrCreateServerConfig(guildId: Long): ServerConfig {
		val result = ServerConfig.findById(guildId) ?: ServerConfig.new(guildId) {}

		if (loritta.config.caches.serverConfigs.maximumSize != 0L)
			cachedServerConfigs.put(guildId, result)

		return result
	}

	fun getLorittaProfile(userId: String): Profile? {
		return getLorittaProfile(userId.toLong())
	}

	/**
	 * Loads the profile of an user
	 *
	 * @param userId the user's ID
	 * @return       the user profile
	 */
	fun getLorittaProfile(userId: Long) = transaction(Databases.loritta) { _getLorittaProfile(userId) }

	/**
	 * Loads the profile of an user in a coroutine
	 *
	 * @param userId the user's ID
	 * @return       the user profile
	 */
	suspend fun getLorittaProfileAsync(userId: Long) = newSuspendedTransaction { _getLorittaProfile(userId) }

	/**
	 * Loads the profile of an user deferred
	 *
	 * @param userId the user's ID
	 * @return       the user profile
	 */
	suspend fun getLorittaProfileDeferred(userId: Long) = suspendedTransactionAsync { _getLorittaProfile(userId) }

	fun _getLorittaProfile(userId: Long) = Profile.findById(userId)

	fun getOrCreateLorittaProfile(userId: String): Profile {
		return getOrCreateLorittaProfile(userId.toLong())
	}

	fun getOrCreateLorittaProfile(userId: Long): Profile {
		val sqlProfile = transaction(Databases.loritta) { Profile.findById(userId) }
		if (sqlProfile != null)
			return sqlProfile

		val profileSettings = transaction(Databases.loritta) {
			ProfileSettings.new {
				gender = Gender.UNKNOWN
				boughtProfiles = arrayOf()
			}
		}

		return transaction(Databases.loritta) {
			Profile.new(userId) {
				xp = 0
				lastMessageSentAt = 0L
				lastMessageSentHash = 0
				money = 0
				isAfk = false
				settings = profileSettings
			}
		}
	}

	fun getActiveMoneyFromDonations(userId: Long): Double {
		return transaction(Databases.loritta) { _getActiveMoneyFromDonations(userId) }
	}

	suspend fun getActiveMoneyFromDonationsAsync(userId: Long): Double {
		return loritta.newSuspendedTransaction { _getActiveMoneyFromDonations(userId) }
	}

	private fun _getActiveMoneyFromDonations(userId: Long): Double {
		return Payment.find {
			(Payments.expiresAt greaterEq System.currentTimeMillis()) and
					(Payments.reason eq PaymentReason.DONATION) and
					(Payments.userId eq userId)
		}.sumByDouble { it.money.toDouble() }
	}
}