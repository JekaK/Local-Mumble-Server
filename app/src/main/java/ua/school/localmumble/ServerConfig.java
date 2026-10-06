package ua.school.localmumble;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ServerConfig {
    private ServerConfig() {}

    static File write(Context context, int port, int maxUsers, int bandwidth, String password)
            throws IOException {
        File directory = new File(context.getFilesDir(), "server");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Не вдалося створити каталог сервера");
        }

        File certificate = new File(directory, "certificate.crt");
        File privateKey = new File(directory, "private_key.key");
        File config = new File(directory, "umurmur.conf");
        if (password != null && (password.length() > 64 || password.chars().anyMatch(c -> c < 32))) {
            throw new IOException("Пароль має містити до 64 символів без керівних символів");
        }

        String body = "max_bandwidth = " + bandwidth + ";\n"
                + "welcometext = \"Локальний шкільний голосовий сервер\";\n"
                + "certificate = \"" + escape(certificate.getAbsolutePath()) + "\";\n"
                + "private_key = \"" + escape(privateKey.getAbsolutePath()) + "\";\n"
                + "password = \"" + escape(password) + "\";\n"
                + "max_users = " + maxUsers + ";\n"
                + "bindport = " + port + ";\n"
                + "bindaddr = \"0.0.0.0\";\n"
                + "username = \"\";\n"
                + "allow_textmessage = false;\n"
                + "show_addresses = false;\n"
                + "channels = (\n"
                + "  { name = \"Клас\"; parent = \"\"; description = \"Локальний голосовий канал\"; }\n"
                + ");\n"
                + "default_channel = \"Клас\";\n";

        try (FileOutputStream output = new FileOutputStream(config, false)) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        return config;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
