/**
 * Точка входа. Отдельный класс нужен потому что IntelliJ и JVM
 * проверяют наличие JavaFX-модулей ЗАРАНЕЕ если main-класс
 * наследует Application — до того как VM-опции успевают применить
 * module-path. Запуск через этот класс обходит раннюю проверку.
 */
public class AppLauncher {
    public static void main(String[] args) {
        App.main(args);
    }
}
