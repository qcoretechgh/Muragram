# Muragram 📱

![Icon](icon.png)

[English](#english) | [Русский](#русский)

---

<a name="english"></a>
## English

**Muragram** is a Telegram Client built specifically for the legendary Android era. Our application is optimized for devices running **Android 2.3 Gingerbread (API 9)** and higher.

We believe that old devices shouldn't become "bricks". Muragram brings back full communication features to vintage smartphones, tablets, and TV boxes, maintaining high performance and modern messenger capabilities.

### ✨ Key Features

*   **Full Communication**: Private chats and groups.
*   **Multimedia**: Send and receive images, files, and voice messages.
*   **Voice Messages**: Record and listen to audio messages even on low-end hardware.
*   **Video Streaming**: Support for streaming video with seeking (Range-Requests).
*   **Smart Search**: Search in chats, users, and global search by usernames.
*   **Adaptivity**: Special interface for tablets (Master-Detail layout).
*   **Security**: Support for phone number authorization and two-factor authentication (2FA).
*   **QR Authorization**: Convenient login for TV boxes without the need for typing.
*   **Editing & Replies**: Quoting messages (Reply) and editing sent text.

### 🛠 Tech Stack

The project is written considering the strict limitations of old Android versions:
*   **Language**: Pure Java (Java 1.8 compatibility).
*   **Minimum Version**: Android 2.3 (API 9).
*   **Networking**: Efficient use of `HttpURLConnection` with manual socket management (to prevent hangs on Gingerbread).
*   **JSON**: Using native `org.json` to save RAM (crucial for devices with 256-512 MB RAM).
*   **Protocols**: Optimized for HTTP (due to lack of TLS 1.2/1.3 support in legacy systems).
*   **Architecture**: Classic Thread + Handler / AsyncTask approach for maximum stability.

### 🚀 Getting Started & Build

1.  Clone the repository:
    ```bash
    git clone https://github.com/qcoretechgh/Muragram.git
    ```
2.  Open the project in **Android Studio**.
3.  Ensure you have Android SDK for API 30 installed (Compile SDK).
4.  Build APK: `./gradlew assembleDebug`.

### 👨‍💻 For Developers (Debug Menu)

A hidden service menu is available for debugging network requests, logs, and changing servers.
*   **On Phone**: Type the code `*#*#68724726#*#*` in the dialer (or just within the app if you have a keyboard).
*   **On Tablet**: Long press on the "Muragram" logo in the top panel.


---

<a name="русский"></a>
## Русский

**Muragram** — это клиент Telegram, созданный специально для легендарной эпохи Android. Наше приложение оптимизировано для работы на устройствах с **Android 2.3 Gingerbread (API 9)** и выше.

Мы верим, что старые устройства не должны превращаться в "кирпичи". Muragram возвращает возможность полноценного общения на винтажные смартфоны, планшеты и ТВ-боксы, сохраняя высокую скорость работы и современные возможности мессенджера.

### ✨ Основные возможности

*   **Полноценное общение**: Личные чаты и группы.
*   **Мультимедиа**: Отправка и получение изображений, файлов и голосовых сообщений.
*   **Голосовые сообщения**: Запись и прослушивание аудиосообщений даже на слабых устройствах.
*   **Видео-стриминг**: Поддержка потокового видео с возможностью перемотки (Range-Requests).
*   **Умный поиск**: Поиск по чатам, пользователям и глобальный поиск по юзернеймам.
*   **Адаптивность**: Специальный интерфейс для планшетов (Master-Detail layout).
*   **Безопасность**: Поддержка авторизации по номеру телефона и двухфакторной аутентификации (2FA).
*   **QR-авторизация**: Удобный вход для ТВ-приставок без необходимости ввода текста.
*   **Редактирование и ответы**: Цитирование сообщений (Reply) и редактирование отправленного текста.

### 🛠 Технический стек

Проект написан с учетом жестких ограничений старых версий Android:
*   **Язык**: Чистая Java (совместимость с Java 1.8).
*   **Минимальная версия**: Android 2.3 (API 9).
*   **Сеть**: Эффективное использование `HttpURLConnection` с ручным управлением сокетами (для предотвращения зависаний на Gingerbread).
*   **JSON**: Использование нативного `org.json` для экономии оперативной памяти (актуально для устройств с 256-512 МБ ОЗУ).
*   **Протоколы**: Оптимизированная работа по HTTP (из-за отсутствия поддержки TLS 1.2/1.3 в старых версиях системы).
*   **Архитектура**: Классический подход Thread + Handler / AsyncTask для максимальной стабильности.

### 🚀 Развертывание и сборка

1.  Клонируйте репозиторий:
    ```bash
    git clone https://github.com/qcoretechgh/Muragram.git
    ```
2.  Откройте проект в **Android Studio**.
3.  Убедитесь, что у вас установлен Android SDK для API 30 (Compile SDK).
4.  Соберите APK: `./gradlew assembleDebug`.

### 👨‍💻 Для разработчиков (Debug Menu)

В приложении скрыто сервисное меню для отладки сетевых запросов, логов и смены сервера.
*   **На телефоне**: Введите в номеронабирателе (или просто в приложении при наличии клавиатуры) код: `*#*#68724726#*#*`.
*   **На планшете**: Удерживайте палец на логотипе "Muragram" в верхней панели.

---
*Сделано с любовью к ретро-технологиям и чистой Java.*
