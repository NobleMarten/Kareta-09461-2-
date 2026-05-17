package ru.gr0946x.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private boolean isActive;

    // username (lowercase) → обработчик клиента
    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    private final AuthService authService;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public Server(int port) {
        // Инициализация БД происходит здесь через UserRepository (вызывает DatabaseConfig)
        userRepository = new UserRepository();
        messageRepository = new MessageRepository();
        authService = new AuthService(userRepository);

        isActive = true;
        new Thread(() -> {
            try (var serverSocket = new ServerSocket(port)) {
                log.info("Сервер запущен на порту {}", port);
                while (isActive) {
                    try {
                        var socket = serverSocket.accept();
                        log.info("Новое подключение: {}", socket.getInetAddress());
                        var handler = new ClientHandler(
                                socket, this, authService, userRepository, messageRepository
                        );
                        new Thread(handler).start();
                    } catch (Exception e) {
                        // Не останавливаем сервер из-за одного клиента
                        log.error("Ошибка при подключении клиента", e);
                    }
                }
            } catch (IOException e) {
                log.error("Ошибка запуска сервера", e);
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Управление онлайн-пользователями
    // -------------------------------------------------------------------------

    public void addOnlineUser(String username, ClientHandler handler) {
        onlineUsers.put(username, handler);
        log.info("Онлайн: {} пользователей", onlineUsers.size());
        broadcastUserList();
    }

    public void removeOnlineUser(String username) {
        onlineUsers.remove(username);
        log.info("Онлайн: {} пользователей", onlineUsers.size());
        broadcastUserList();
    }

    // -------------------------------------------------------------------------
    // Рассылка
    // -------------------------------------------------------------------------

    /** Рассылает обновлённый список онлайн-пользователей всем подключённым. */
    public void broadcastUserList() {
        var msg = buildUserListMessage();
        onlineUsers.values().forEach(h -> h.sendRaw(msg));
    }

    /** Строит строку со списком пользователей для отправки клиенту. */
    String buildUserListMessage() {
        return MessageType.INFO
                + ProtocolConstants.COMMAND_SEPARATOR
                + "USERS:"
                + String.join(",", onlineUsers.keySet());
    }

    /** Отправляет сообщение всем аутентифицированным пользователям. */
    public void sendToAll(String data) {
        onlineUsers.values().forEach(h -> h.sendRaw(data));
    }

    /**
     * Отправляет сообщение конкретному пользователю.
     *
     * @return true если пользователь онлайн и сообщение доставлено
     */
    public boolean sendToUser(String username, String data) {
        var handler = onlineUsers.get(username.toLowerCase());
        if (handler == null) return false;
        handler.sendRaw(data);
        return true;
    }
}
