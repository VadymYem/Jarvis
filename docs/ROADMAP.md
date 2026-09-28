# Roadmap

## 0.2 — Native foundation

- [x] AuthorChe UI і теми
- [x] pluggable LLM endpoint
- [x] локальні GGUF-моделі через llama.cpp/JNI
- [x] нативний Gemini API
- [x] профілі OpenAI/GPT, Groq і сумісного API
- [x] Android Keystore для ключа
- [x] agent loop і журнал
- [x] Accessibility tools
- [x] Shizuku UserService
- [x] локальні нагадування
- [x] CI debug build + unit tests

## 0.3 — Надійність виконання

- [x] брендинг Jarvis | AuthorAi
- [x] голосовий ввід і озвучення відповідей
- [x] локальний router швидких голосових команд
- [x] Android Assistant role та запуск слухання через системний жест
- [x] команда пошуку телефона з `Downloads/was-wollen-wir-trinken.mp3`
- [x] локальний профіль еталонного голосу
- [x] поштовий app-конектор для скриньки й безпечних чернеток
- [x] Termux RUN_COMMAND із результатом виконання
- [x] білий список ADB-операцій і сценарії з `~/.jarvis/actions/`
- [ ] Room-сховище задач і відновлення після перезапуску
- [ ] structured tool calling для OpenAI, Gemini та сумісних API
- [ ] автоматичний вибір моделі та multi-key failover
- [ ] validator/reflection після кожної візуальної дії
- [ ] gestures за координатами як останній fallback
- [ ] планувальник точних і повторюваних автоматизацій
- [ ] інтеграційні тести на Android Emulator

## 0.4 — Повністю автономний агент

- [ ] Room-черга довготривалих задач на самому пристрої
- [ ] відновлення локальної задачі після перезапуску Android
- [ ] локальна пам'ять і пошук без зовнішнього сервера
- [ ] OAuth-конектори Gmail і Outlook для пошуку, читання, чернеток і надсилання
- [ ] конектори календаря, хмарних дисків та месенджерів
- [ ] правила автоматизацій: тригер, умови, дозволені дії та журнал
- [ ] менеджер завантаження рекомендованих GGUF-моделей
- [ ] оцінка доступної RAM і рекомендація квантизації
- [ ] Vulkan backend після стабільного тестування на різних GPU

## 0.5 — Vision і голос

- [ ] MediaProjection зі згодою на кожну сесію
- [ ] приватний screenshot-on-demand замість постійного стриму
- [ ] локальний OCR/vision fallback
- [ ] локальний wake word із видимим foreground-індикатором
- [ ] справжній локальний clone-TTS для української, німецької й англійської
- [ ] видимий recorder голосових нотаток
- [ ] локальна транскрипція та резюме
- [ ] керована політика видалення сирого аудіо

## 1.0 — Production

- [ ] підписана release-збірка
- [ ] Play/F-Droid policy audit
- [ ] повна українська, англійська й німецька локалізація
- [ ] accessibility UX audit
- [ ] security review і reproducible builds
