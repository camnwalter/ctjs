package com.chattriggers.ctjs

import com.chattriggers.ctjs.commands.CTCommand
import com.chattriggers.ctjs.engine.module.ModuleManager
import com.chattriggers.ctjs.minecraft.libs.renderer.Image
import com.chattriggers.ctjs.minecraft.objects.Sound
import com.chattriggers.ctjs.minecraft.wrappers.Player
import com.chattriggers.ctjs.triggers.TriggerType
import com.chattriggers.ctjs.utils.Config
import com.chattriggers.ctjs.utils.Initializer
import com.chattriggers.ctjs.utils.console.printTraceToConsole
import com.google.gson.Gson
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import java.io.File
import java.net.URL
import java.net.URLConnection
import java.security.MessageDigest
import java.util.*
import kotlin.concurrent.thread

class CTJS : ClientModInitializer {
    override fun onInitializeClient() {
        Initializer.initializers.forEach(Initializer::init)

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            CTCommand.register(dispatcher)
        }

        Config.loadData()

        // Ensure that reportHashedUUID always runs on a separate thread
        // TODO: Do we still need an option to disable threaded loading?
        // TODO: Put setup/asmPass somewhere else
        if (Config.threadedLoading) {
            thread {
                try {
                    ModuleManager.setup()
                    ModuleManager.asmPass()
                    ModuleManager.entryPass()
                    reportHashedUUID()
                } catch (e: Throwable) {
                    e.printTraceToConsole()
                }
            }
        } else {
            ModuleManager.setup()
            ModuleManager.asmPass()
            ModuleManager.entryPass()
            thread { reportHashedUUID() }
        }

        Runtime.getRuntime().addShutdownHook(Thread(TriggerType.GameLoad::triggerAll))
    }

    private fun reportHashedUUID() {
        val uuid = Player.getUUID().encodeToByteArray()
        val salt = (System.getProperty("user.name") ?: "").encodeToByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        val hashedUUID = md.digest(uuid)
        val hash = Base64.getUrlEncoder().encodeToString(hashedUUID)

        val url = "${WEBSITE_ROOT}/api/statistics/track?hash=$hash&version=${Reference.MOD_VERSION}"
        val connection = makeWebRequest(url)
        connection.getInputStream()
    }

    companion object {
        const val DEFAULT_MODULES_FOLDER = "./config/ChatTriggers/modules"
        const val WEBSITE_ROOT = "https://www.chattriggers.com"
        internal val images = mutableListOf<Image>()
        internal val sounds = mutableListOf<Sound>()

        val configLocation = File("./config")
        val assetsDir = File(configLocation, "ChatTriggers/images/").apply { mkdirs() }

        internal val gson = Gson()

        internal fun makeWebRequest(url: String): URLConnection = URL(url).openConnection().apply {
            setRequestProperty("User-Agent", "Mozilla/5.0 (ChatTriggers)")
            connectTimeout = 3000
            readTimeout = 3000
        }
    }
}