package ru.gr0946x.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import ru.gr0946x.net.Client;
import ru.gr0946x.net.MessageType;

import java.util.Arrays;
import java.util.function.BiConsumer;

public class ChatController {

    @FXML private ListView<String> userList;
    @FXML private ListView<String> messageList;
    @FXML private TextField messageInput;
    @FXML private TextField searchField;
    @FXML private Label chatLabel;
    @FXML private Button broadcastBtn;

    private Client client;
    private String myUsername;
    private String selectedUser = null; // null = broadcast

    private final ObservableList<String> users = FXCollections.observableArrayList();
    private final ObservableList<String> messages = FXCollections.observableArrayList();

    private BiConsumer<String, MessageType> dataListener;

    /**
     * Вызывается из LoginController после успешной аутентификации.
     * Подключает слушателей и загружает начальные данные.
     */
    public void setup(Client client, String myUsername) {
        this.client = client;
        this.myUsername = myUsername;

        userList.setItems(users);
        messageList.setItems(messages);

        // Выбор пользователя из списка → переход в личный чат
        userList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        selectedUser = newVal;
                        chatLabel.setText("Личный чат с " + newVal);
                        loadHistory(newVal);
                    }
                }
        );

        // Слушатель входящих данных (вызывается в потоке Communicator → переброс в FX-поток)
        dataListener = (content, type) -> Platform.runLater(() -> handleData(content, type));
        client.addDataListener(dataListener);

        // Обработка разрыва соединения
        client.addCloseListener(() -> Platform.runLater(this::onDisconnect));

        // Запрашиваем список онлайн и историю broadcast
        client.sendData("USERS");
        client.sendData("HISTORY");
    }

    // -------------------------------------------------------------------------
    // Обработка входящих данных
    // -------------------------------------------------------------------------

    private void handleData(String content, MessageType type) {
        switch (type) {
            case MESSAGE -> {
                // Формат: "отправитель:текст"
                var parts = content.split(":", 2);
                if (parts.length == 2) {
                    messages.add(parts[0] + ": " + parts[1]);
                    scrollToBottom();
                }
            }
            case INFO -> {
                if (content.startsWith("USERS:")) {
                    updateUserList(content.substring("USERS:".length()));
                }
                // HISTORY_END и SEARCH_END — не требуют действий в UI
            }
            case ERROR -> messages.add("⚠ " + content);
            default -> { }
        }
    }

    private void updateUserList(String csv) {
        users.clear();
        if (!csv.isBlank()) {
            Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(u -> !u.equalsIgnoreCase(myUsername)) // себя не показываем
                    .forEach(users::add);
        }
    }

    private void onDisconnect() {
        messages.add("— Соединение с сервером разорвано —");
        messageInput.setDisable(true);
        searchField.setDisable(true);
    }

    // -------------------------------------------------------------------------
    // Действия пользователя
    // -------------------------------------------------------------------------

    @FXML
    private void onBroadcast() {
        selectedUser = null;
        userList.getSelectionModel().clearSelection();
        chatLabel.setText("Общий чат");
        loadHistory(null);
    }

    @FXML
    private void onSend() {
        var text = messageInput.getText().trim();
        if (text.isEmpty()) return;
        if (selectedUser == null) {
            client.sendData("MESSAGE:" + text);
        } else {
            client.sendData("PRIVATE:" + selectedUser + ":" + text);
        }
        messageInput.clear();
    }

    @FXML
    private void onSearch() {
        var keyword = searchField.getText().trim();
        if (keyword.isEmpty()) return;
        messages.clear();
        messages.add("— Результаты поиска: «" + keyword + "» —");
        if (selectedUser == null) {
            client.sendData("SEARCH:" + keyword);
        } else {
            client.sendData("SEARCH_IN:" + selectedUser + ":" + keyword);
        }
        searchField.clear();
    }

    // -------------------------------------------------------------------------
    // Вспомогательные методы
    // -------------------------------------------------------------------------

    /** Загружает историю: null = broadcast, иначе личный чат с username. */
    private void loadHistory(String username) {
        messages.clear();
        if (username == null) {
            client.sendData("HISTORY");
        } else {
            client.sendData("HISTORY:" + username);
        }
    }

    private void scrollToBottom() {
        if (!messages.isEmpty()) {
            messageList.scrollTo(messages.size() - 1);
        }
    }
}
