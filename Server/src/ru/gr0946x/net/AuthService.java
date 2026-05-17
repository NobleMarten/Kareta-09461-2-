package ru.gr0946x.net;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Регистрация и аутентификация пользователей через BCrypt.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Регистрирует нового пользователя.
     *
     * @return зарегистрированный пользователь, или empty если имя уже занято
     */
    public Optional<User> register(String username, String plainPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("Регистрация отклонена: имя '{}' уже занято", username);
            return Optional.empty();
        }
        var hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        userRepository.register(username, hash);
        log.info("Пользователь '{}' зарегистрирован", username.toLowerCase());
        return userRepository.findByUsername(username);
    }

    /**
     * Проверяет логин и пароль.
     *
     * @return пользователь из БД, или empty при неверных данных
     */
    public Optional<User> login(String username, String plainPassword) {
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("Вход отклонён: пользователь '{}' не найден", username);
            return Optional.empty();
        }
        var user = userOpt.get();
        if (!BCrypt.checkpw(plainPassword, user.password())) {
            log.warn("Вход отклонён: неверный пароль для '{}'", username);
            return Optional.empty();
        }
        log.info("Пользователь '{}' вошёл", username);
        return Optional.of(user);
    }
}
