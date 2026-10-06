plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("ua.school.localmumble.core.HostServerMainKt") }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { useJUnit(); testLogging { events("passed", "failed", "skipped") } }
