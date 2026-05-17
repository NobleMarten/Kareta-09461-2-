package ru.gr0946x.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Обрабатывает одно клиентское соединение: аутентификацию, сообщения,
 * историю, поиск и список пользователей.
 *
 * Протокол клиент → сервер (строки):
 *   LOGIN:имя:пароль
 *   REGISTER:имя:пароль
 *   MESSAGE:текст                  — широковещательное сообщение
 *   PRIVATE:получатель:текст       — личное сообщение
 *   HISTORY                        — последние 20 сообщений broadcast
 *   HISTORY:имя                    — последние 20 в чате с пользователем
 *   SEARCH:ключевое_слово          — поиск в broadcast
 *   SEARCH_IN:имя:ключевое_слово   — поиск в личном чате
 *   USERS                          — список онлайн-пользователей
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private static final String SEP = ProtocolConstants.COMMAND_SEPARATOR;

    private final Communicator communicator;
    private final Server server;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    private volatile User currentUser = null;
    // Блокирует поток run() до разрыва соединения
    private final CountDownLatch disconnectLatch = new CountDownLatch(1);

    public ClientHandler(Socket socket,
                         Server server,
                         AuthService authService,
                         UserRepository userRepository,
                         MessageRepository messageRepository) throws IOException {
        this.communicator = new Communicator(socket);
        this.server = server;
        this.authService = authService;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void run() {
        communicator.addDataListener(this::parseData);
        communicator.addCloseListener(this::handleDisconnect);
        communicator.start();
        send(MessageType.REQUEST, "Введите LOGIN:имя:пароль или REGISTER:имя:пароль");
        try {
            disconnectLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Маршрутизация
    // -------------------------------------------------------------------------

    private void parseData(String raw) {
        var parts = raw.split(SEP, 2);
        var cmd = parts[0].trim().toUpperCase();
        var content = parts.length > 1 ? parts[1] : "";

        if (currentUser == null) {
            switch (cmd) {
                case "LOGIN"    -> handleLogin(content);
                case "REGISTER" -> handleRegister(content);
                default -> {
                    send(MessageType.ERROR, "Сначала выполните LOGIN или REGISTER");
                    send(MessageType.REQUEST, "Введите LOGIN:имя:пароль или REGISTER:имя:пароль");
                }
            }
        } else {
            switch (cmd) {
                case "MESSAGE"   -> handleBroadcast(content);
                case "PRIVATE"   -> handlePrivate(content);
                case "HISTORY"   -> handleHistory(content.trim());
                case "SEARCH"    -> handleSearch(content);
                case "SEARCH_IN" -> handleSearchIn(content);
                case "USERS"     -> sendRaw(server.buildUserListMessage());
                default          -> send(MessageType.ERROR, "Неизвестная команда: " + cmd);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Аутентификация
    // -------------------------------------------------------------------------

    private void handleLogin(String content) {
        var creds = content.split(SEP, 2);
        if (creds.length < 2 || creds[0].isBlank()) {
            send(MessageType.ERROR, "Формат: LOGIN:имя:пароль");
            return;
        }
        authService.login(creds[0].trim(), creds[1])
                .ifPresentOrElse(
                        this::completeAuth,
                        () -> {
                            send(MessageType.ERROR, "Неверное имя или пароль");
                            send(MessageType.REQUEST, "Введите LOGIN:имя:пароль или REGISTER:имя:пароль");
                        }
                );
    }

    private void handleRegister(String content) {
        var creds = content.split(SEP, 2);
        if (creds.length < 2 || creds[0].isBlank()) {
            send(MessageType.ERROR, "Формат: REGISTER:имя:пароль");
            return;
        }
        var username = creds[0].trim();
        if (!Character.isLetter(username.charAt(0))) {
            send(MessageType.ERROR, "Имя должно начинаться с буквы");
            send(MessageType.REQUEST, "Введите LOGIN:имя:пароль или REGISTER:имя:пароль");
            return;
        }
        authService.register(username, creds[1])
                .ifPresentOrElse(
                        this::completeAuth,
                        () -> {
                            send(MessageType.ERROR, "Имя '" + username + "' уже занято");
                            send(MessageType.REQUEST, "Введите LOGIN:имя:пароль или REGISTER:имя:пароль");
                        }
                );
    }

    private void completeAuth(User user) {
        currentUser = user;
        send(MessageType.INFO, "AUTH_OK:" + user.username());
        server.addOnlineUser(user.username(), this);
        log.info("Пользователь '{}' аутентифицирован", user.username());
    }

    // -------------------------------------------------------------------------
    // Сообщения
    // -------------------------------------------------------------------------

    private void handleBroadcast(String content) {
        if (content.isBlank()) return;
        messageRepository.save(currentUser.id(), null, content);
        server.sendToAll(MessageType.MESSAGE + SEP + currentUser.username() + SEP + content);
    }

    private void handlePrivate(String content) {
        var parts = content.split(SEP, 2);
        if (parts.length < 2 || parts[0].isBlank()) {
            send(MessageType.ERROR, "Формат: PRIVATE:получатель:сообщение");
            return;
        }
        var toUsername = parts[0].trim().toLowerCase();
        var text = parts[1];

        var recipientOpt = userRepository.findByUsername(toUsername);
        if (recipientOpt.isEmpty()) {
            send(MessageType.ERROR, "Пользователь '" + toUsername + "' не найден");
            return;
        }
        var recipient = recipientOpt.get();
        messageRepository.save(currentUser.id(), recipient.id(), text);

        var msg = MessageType.MESSAGE + SEP + currentUser.username() + SEP + text;
        sendRaw(msg);                         // отправителю (эхо)
        server.sendToUser(toUsername, msg);   // получателю (если онлайн)
    }

    // -------------------------------------------------------------------------
    // История
    // -------------------------------------------------------------------------

    private void handleHistory(String username) {
        List<Message> history;
        if (username.isEmpty()) {
            history = messageRepository.getLast20Broadcast();
        } else {
            var otherOpt = userRepository.findByUsername(username);
            if (otherOpt.isEmpty()) {
                send(MessageType.ERROR, "Пользователь '" + username + "' не найден");
                return;
            }
            history = messageRepository.getLast20(currentUser.id(), otherOpt.get().id());
        }
        sendMessages(history);
        send(MessageType.INFO, "HISTORY_END");
    }

    // -------------------------------------------------------------------------
    // Поиск
    // -------------------------------------------------------------------------

    private void handleSearch(String keyword) {
        if (keyword.isBlank()) {
            send(MessageType.ERROR, "Укажите ключевое слово");
            return;
        }
        sendMessages(messageRepository.searchInBroadcast(keyword));
        send(MessageType.INFO, "SEARCH_END");
    }

    private void handleSearchIn(String content) {
        var parts = content.split(SEP, 2);
        if (parts.length < 2 || parts[0].isBlank()) {
            send(MessageType.ERROR, "Формат: SEARCH_IN:пользователь:слово");
            return;
        }
        var otherOpt = userRepository.findByUsername(parts[0].trim());
        if (otherOpt.isEmpty()) {
            send(MessageType.ERROR, "Пользователь '" + parts[0].trim() + "' не найден");
            return;
        }
        sendMessages(messageRepository.searchInChat(currentUser.id(), otherOpt.get().id(), parts[1]));
        send(MessageType.INFO, "SEARCH_END");
    }

    // -------------------------------------------------------------------------
    // Утилиты
    // -------------------------------------------------------------------------

    /** Отправляет список сообщений в формате MESSAGE:отправитель:текст */
    private void sendMessages(List<Message> messages) {
        for (var m : messages) {
            var senderName = userRepository.findById(m.senderId())
                    .map(User::username).orElse("?");
            sendRaw(MessageType.MESSAGE + SEP + senderName + SEP + m.content());
        }
    }

    private void send(MessageType type, String content) {
        sendRaw(type + SEP + content);
    }

    /** Доступен пакетно — Server вызывает для прямой отправки клиенту. */
    void sendRaw(String data) {
        communicator.sendData(data);
    }

    private void handleDisconnect() {
        if (currentUser != null) {
            server.removeOnlineUser(currentUser.username());
            log.info("Пользователь '{}' отключился", currentUser.username());
        }
        disconnectLatch.countDown();
    }

    public void stop() {
        communicator.stop();
    }
}
