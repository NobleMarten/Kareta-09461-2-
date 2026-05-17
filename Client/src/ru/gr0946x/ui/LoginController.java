package ru.gr0946x.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import ru.gr0946x.net.Client;
import ru.gr0946x.net.MessageType;

import java.io.IOException;
import java.util.function.BiConsumer;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginBtn;
    @FXML private Button registerBtn;
    @FXML private Label errorLabel;

    private Client client;
    // Храним ссылку, чтобы корректно снять слушатель
    private BiConsumer<String, MessageType> dataListener;

    @FXML
    private void onLogin() {
        sendAuth("LOGIN");
    }

    @FXML
    private void onRegister() {
        sendAuth("REGISTER");
    }

    private void sendAuth(String command) {
        var username = usernameField.getText().trim();
        var password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Заполните все поля");
            return;
        }

        setControlsDisabled(true);
        showError(null);

        try {
            client = new Client("localhost", 9460);
            dataListener = (content, type) -> Platform.runLater(() -> onData(content, type));
            client.addDataListener(dataListener);
            client.start();
            client.sendData(command + ":" + username + ":" + password);
        } catch (IOException e) {
            setControlsDisabled(false);
            showError("Не удалось подключиться к серверу");
        }
    }

    private void onData(String content, MessageType type) {
        switch (type) {
            case INFO -> {
                if (content.startsWith("AUTH_OK:")) {
                    var username = content.substring("AUTH_OK:".length());
                    openChat(username);
                }
            }
            case ERROR -> {
                setControlsDisabled(false);
                showError(content);
                // Снимаем слушатель — следующая попытка создаст нового клиента
                client.removeDataListener(dataListener);
            }
            default -> { /* REQUEST и MESSAGE до авторизации игнорируем */ }
        }
    }

    private void openChat(String username) {
        client.removeDataListener(dataListener);
        try {
            var loader = new FXMLLoader(getClass().getResource("chat.fxml"));
            var scene = new Scene(loader.load(), 860, 560);
            ChatController chat = loader.getController();
            chat.setup(client, username);

            var stage = (Stage) usernameField.getScene().getWindow();
            stage.setTitle("Kareta Chat — " + username);
            stage.setResizable(true);
            stage.setScene(scene);
        } catch (IOException e) {
            showError("Ошибка открытия чата: " + e.getMessage());
            setControlsDisabled(false);
        }
    }

    private void showError(String msg) {
        if (msg == null || msg.isBlank()) {
            errorLabel.setVisible(false);
        } else {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
        }
    }

    private void setControlsDisabled(boolean disabled) {
        loginBtn.setDisable(disabled);
        registerBtn.setDisable(disabled);
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
    }
}
