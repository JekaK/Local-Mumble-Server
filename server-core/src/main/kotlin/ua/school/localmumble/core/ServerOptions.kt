package ua.school.localmumble.core

data class ServerOptions(
    val port: Int = 64738,
    val maxUsers: Int = 30,
    val bandwidth: Int = 48000,
    val password: String = "",
) {
    init {
        require(port == 0 || port in 1024..65535) { "Порт має бути від 1024 до 65535" }
        require(maxUsers in 2..100) { "Кількість учасників має бути від 2 до 100" }
        require(bandwidth in 8000..128000) { "Ліміт має бути від 8000 до 128000 біт/с" }
        require(password.length <= 64 && password.none { it.isISOControl() }) {
            "Пароль: до 64 символів без керівних символів"
        }
    }
}
