package ua.school.localmumble.core

import java.io.File
import java.util.concurrent.CountDownLatch

/** Development/test launcher. Android embeds the same server class directly. */
fun main(args: Array<String>) {
    require(args.size <= 3) { "Usage: server-core [port] [password] [identity-directory]" }
    val server = LocalVoiceServer(
        ServerOptions(port = args.getOrNull(0)?.toInt() ?: 64738, password = args.getOrNull(1).orEmpty()),
        File(args.getOrNull(2) ?: "build/host-identity"), ::println,
        { error -> System.err.println(error.javaClass.simpleName); kotlin.system.exitProcess(1) },
    )
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    server.start()
    CountDownLatch(1).await()
}
