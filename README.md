# NAnt Script Builder

Лабораторні роботи з дисципліни «Технології розроблення програмного забезпечення»
(КПІ ім. Ігоря Сікорського, ФІОТ), варіант 16. Виконав: Савицький Д.В., група ІА-41.

Візуальна утиліта для створення скриптів збірки NAnt:

- редагування XML-тексту скрипта з підсвічуванням синтаксису та автодоповненням;
- конструювання скрипта перетягуванням цілей, завдань і властивостей з палітри;
- автоматичне перетворення рішень Visual Studio (`.sln`) у скрипти збірки
  з урахуванням залежностей між проєктами;
- зберігання скриптів у базі даних SQLite та експорт у файл `.build`;
- кешування списку скриптів із показником роботи кешу (шаблон Proxy);
- перевірка скрипта ланцюжком валідаторів зі звітом про помилки й попередження
  (шаблон Chain of Responsibility).

## Технології

Java 21, Swing (FlatLaf, RSyntaxTextArea, AutoComplete), SQLite (sqlite-jdbc), JUnit 5, Gradle,
PlantUML.

## Структура проєкту

| Шлях | Вміст |
|---|---|
| `src/main/java/ua/kpi/trpz/nantbuilder/model` | класи предметної області |
| `src/main/java/ua/kpi/trpz/nantbuilder/db` | підключення до SQLite, транзакції |
| `src/main/java/ua/kpi/trpz/nantbuilder/repository` | шаблон Repository: інтерфейси та JDBC-реалізації |
| `src/main/java/ua/kpi/trpz/nantbuilder/repository/cache` | шаблон Proxy: кешувальний замісник репозиторію |
| `src/main/java/ua/kpi/trpz/nantbuilder/service` | бізнес-логіка, серіалізація XML, імпорт `.sln` |
| `src/main/java/ua/kpi/trpz/nantbuilder/service/validation` | шаблон Chain of Responsibility: ланцюжок перевірок скрипта |
| `src/main/java/ua/kpi/trpz/nantbuilder/ui` | форми Swing |
| `src/main/resources/schema.sql` | структура бази даних |
| `docs/uml` | UML-діаграми (джерела PlantUML і PNG) |
| `docs/screenshots` | скріншоти форм |
| `samples/DemoShop` | приклад рішення `.sln` для імпорту |

## Лабораторні роботи

| Робота | Тема | Тег |
|---|---|---|
| ЛР №2 | Основи проектування | `lab2` |
| ЛР №3 | Основи проектування розгортання | `lab3` |
| ЛР №4 | Вступ до патернів проектування (шаблон Proxy) | `lab4` |
| ЛР №5 | Патерни проектування (шаблон Chain of Responsibility) | `lab5` |

## Збірка та запуск

Потрібні JDK 21 і Gradle 8.5 або новіший.

```
gradle test          # модульні тести
gradle run           # запуск застосунку
gradle screenshots   # оновити скріншоти форм у docs/screenshots
```

База даних створюється автоматично у `%USERPROFILE%\.nant-script-builder\nantbuilder.db`.
Для перевірки імпорту можна відкрити `samples/DemoShop/DemoShop.sln`.

Діаграми перегенеровуються командою (з каталогу `docs/uml`):

```
java -jar plantuml.jar -charset UTF-8 -tpng -o png *.puml
```
