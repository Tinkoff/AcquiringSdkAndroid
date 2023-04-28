# Tinkoff Acquiring SDK for Android

[![Maven Central](https://img.shields.io/maven-central/v/ru.tinkoff.acquiring/ui.svg?maxAge=3600)][search.maven]

<img src="images/pay.png" width="320"> <img src="images/attach.png" width="320">

Acquiring SDK позволяет интегрировать [Интернет-Эквайринг Tinkoff][acquiring] в мобильные приложения для платформы Android.

Возможности SDK:
- Прием платежей (в том числе рекуррентных);  
- Сохранение банковских карт клиента; 
- Сканирование и распознавание карт с помощью камеры или NFC;  
- Получение информации о клиенте и сохраненных картах;  
- Управление сохраненными картами;  
- Поддержка локализации;  
- Кастомизация экранов SDK;  
- Интеграция с онлайн-кассами; 
- Поддержка Системы быстрых платежей
- Оплата через Tinkoff Pay
- Совершение оплаты из уведомления

### Требования
Для работы Tinkoff Acquiring SDK необходим Android версии 7.0 и выше (API level 24).

### Подключение
Для подключения SDK добавьте в [_build.gradle_][build-config] вашего проекта следующие зависимости:
```groovy
implementation 'ru.tinkoff.acquiring:ui:$latestVersion'
implementation 'ru.tinkoff.acquiring:threeds-sdk:$latestVersion'
implementation 'ru.tinkoff.acquiring:threeds-wrapper:$latestVersion'
```
Если вы хотите внедрить сканирование с помощью библиотеки Card-IO, то необходимо добавить в [_build.gradle_][build-config]
```groovy
implementation 'ru.tinkoff.acquiring:cardio:$latestVersion'
```

Так же необходимо добавить в [_network-security-config_][network-security-config] содержащий 
сертификаты от минцифр и доп. сертификат от тинькофф. Пример можно посмотреть в `sample` выглядит он так:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="@raw/acq_tinkoff_root_cert" />
            <certificates src="@raw/acq_ministry_of_digital_development_root_cert" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### Подготовка к работе
Для начала работы с SDK вам понадобятся:
* Terminal key
* Public key

Которые выдаются после подключения к [Интернет-Эквайрингу][acquiring]

Подробнее о настройке Личного кабинета можно прочитать [тут](./PersonalAccountSettings.md)

SDK позволяет настроить режим работы (debug/prod). По умолчанию - режим prod.
Чтобы настроить debug режим, установите параметры:
```kotlin
AcquiringSdk.isDeveloperMode = true // используется тестовый URL, деньги с карт не списываются
AcquiringSdk.isDebug = true         // включение логирования запросов
```

Кроме того, в некоторых случаях к запросам к API эквайринга есть возможность добавлять некий токен (подпись запроса).
Задание способа генерации токена в случаях, когда это необходимо, может выглядеть следующим образом
(для более подробной информации см. kDoc `AcquiringSdk.tokenGenerator`): 
```kotlin
AcquiringSdk.tokenGenerator = SampleAcquiringTokenGenerator(password) // генерация токена с использованием пароля
// В целях безопасности не рекомендуется хранить пароль в коде приложения
```

### Пример работы
Для проведения оплаты необходимо зарегистрировать контракт **MainFormContract**#_Contract_, и вызвать метод **ActivityResultLauncher**#_launch_
```kotlin
val tinkoffAcquiring = TinkoffAcquiring(applicationContext, "TERMINAL_KEY", "PUBLIC_KEY") // создание объекта для взаимодействия с SDK и передача данных продавца
val byMainFormPayment = registerForActivityResult(MainFormContract.Contract)
byMainFormPayment.launch(MainFormContract.StartData(options))
```
Метод запустит экран оплаты **MainPaymentFormActivity**. Активити должна быть настроена на обработку конкретного платежа, поэтому в метод необходимо передать настройки проведения оплаты, включающие в себя данные заказа, данные покупателя и опционально параметры кастомизации экрана оплаты.
Так же можно указать модуль для сканирования (свой или **CardScannerDelegate**).
Локализация берется из системы, так же имеется поддержка дневной/ночной темы.
Внешний вид экрана и набор компонентов определяется из доступных методов оплаты, настраивается в личном кабинете.

```kotlin
val paymentOptions = 
        PaymentOptions().setOptions {
            orderOptions {                          // данные заказа
                orderId = "ORDER-ID"                // ID заказа в вашей системе
                amount = Money.ofRubles(1000)       // сумма для оплаты
                title = "НАЗВАНИЕ ПЛАТЕЖА"          // название платежа, видимое пользователю
                description = "ОПИСАНИЕ ПЛАТЕЖА"    // описание платежа, видимое пользователю
                recurrentPayment = false            // флаг определяющий является ли платеж рекуррентным [1]
                successURL  = "URL"                 // URL, куда будет переведен покупатель в случае успешной оплаты (см. полную документацию)
                failURL = "URL"                     // URL, куда будет переведен покупатель в случае неуспешной оплаты (см. полную документацию)
            }   
            customerOptions {                       // данные покупателя
                checkType = CheckType.NO.toString() // тип привязки карты
                customerKey = "CUSTOMER_KEY"        // уникальный ID пользователя для сохранения данных его карты
                email = "batman@gotham.co"          // E-mail клиента для отправки уведомления об оплате
            }   
            featuresOptions {                       // настройки визуального отображения и функций экрана оплаты
                cameraCardScanner =
                    CardScannerDelegateImpl()       // реализация механизма сканирования карт, можно использовать встроенный CardScannerWrapper
            }
        }

```
Результат вызова метода вернется в **MainFormContract.Result**:
- при успешном платеже (_MainFormContract.Success_) возвращается _paymentId_ - идентификатор платежа типа Long, опционально _cardId_ - id карты, с которой проводился платеж, тип String и опционально _rebillId_ - rebillId карты, если был совершен рекуррентный платеж, тип String
- при неуспешном платеже (_MainFormContract.Error_) возвращается ошибка _error_ типа Throwable,(это может быть AcquiringApiException, AcquiringSdkException, AcquiringSdkTimeoutException) и опционально _errorCode_ - номер ошибки из backend (подробнее о возвращаемых ошибках в [документации][full-doc])

Можно передать данные чека, указав параметр **receipt** в методе **PaymentOptions**#_orderOptions_ и передать дополнительные параметры **additionalData**. Эти объекты при их наличии будут переданы на сервер с помощью метода [**API Init**][init-documentation], где можно посмотреть их детальное описание.

```kotlin
val paymentOptions = 
        PaymentOptions().setOptions {
            orderOptions {               
                receipt = myReceipt
                additionalData = dataMap
                // другие параметры заказа
            }
            customerOptions {                    
                // данные покупателя
            }
            featuresOptions {                    
                // настройки визуального отображения и функций экрана оплаты
            }
        }

val byMainFormPayment = registerForActivityResult(MainFormContract.Contract)
byMainFormPayment.launch(MainFormContract.StartData(options))
```
[1] _Рекуррентный платеж_ может производиться для дальнейшего списания средств с сохраненной карты, без ввода ее реквизитов. Эта возможность, например, может использоваться для осуществления платежей по подписке.

[2] _Безопасная клавиатура_ используется вместо системной и обеспечивает дополнительную безопасность ввода, т.к. сторонние клавиатуры на устройстве клиента могут перехватывать данные и отправлять их злоумышленнику.


### Экран привязки карт
Для запуска экрана привязки карт необходимо зарегестирировать **TinkoffAcquiring**#_AttachCard.Contract_. В метод также необходимо передать некоторые параметры - тип привязки, данные покупателя и опционально параметры кастомизации (по-аналогии с экраном оплаты):
```kotlin
val attachCardOptions = 
        AttachCardOptions().setOptions {
            customerOptions {                       // данные покупателя
                customerKey = "CUSTOMER_KEY"        // уникальный ID пользователя для сохранения данных его карты
                checkType = CheckType.NO.toString() // тип привязки карты
                email = "batman@gotham.co"          // E-mail клиента для отправки уведомления о привязке
            }
            featuresOptions {                       // настройки визуального отображения и функций экрана оплаты
                useSecureKeyboard = true
                cameraCardScanner = CameraCardIOScanner()
                theme = themeId
            }
        }

val tinkoffAcquiring = TinkoffAcquiring(applicationContext, "TERMINAL_KEY", "PUBLIC_KEY")
attachCard = registerForActivityResult(AttachCard.Contract) { handle(it) }
attachCard.launch(options)
```
Результат вызова метода вернется в **AttachCard.Result**:
- при успешной привязке (_AttachCard.Success_) возвращается _cardId_ - id карты, которая была привязана, тип String
- при неуспешной привязке (_AttachCard.Error_) возвращается ошибка _error_ типа Throwable (подробнее о возвращаемых ошибках в [документации][full-doc])


### Система быстрых платежей
Включение приема платежей через Систему быстрых платежей осуществляется в Личном кабинете.
При инициализации экрана оплаты SDK проверит наличие возможности оплаты через СБП и в зависимости от результата отобразит


#### Прием оплаты по статическому QR коду через СБП
Чтобы реализовать оплату с помощью статического QR кода на экране приложения, необходимо:
1) Создать соответствующую кнопку приема оплаты в приложении кассира
2) Установить слушатель на клик по кнопке и вызвать в нем метод **TinkoffAcquiring**#_openStaticQrScreen_
Метод openStaticQrScreen принимает параметры: activity, localization - для локализации сообщения на экране, requestCode - для получения ошибки, если таковая возникнет.
Результат оплаты товара покупателем по статическому QR коду не отслеживается в SDK, соответственно в onActivityResult вызывающего экран активити может вернуться только ошибка или отмена (закрытие экрана).

### Tinkoff Pay
Включение приема платежей через Tinkoff Pay осуществляется в Личном кабинете.
#### Включение приема оплаты через Tinkoff Pay по кнопке для покупателя:
При инициализации экрана оплаты SDK проверит наличие возможности оплаты через Tinkoff Pay и в зависимости от результата отобразит

Для определения возможности оплаты через Tinkoff Pay SDK посылает запрос на "https://securepay.tinkoff.ru/v2/GetTerminalPayMethods".

Для отображения кнопки оплаты через Tinkoff Pay внутри вашего приложения (вне экрана оплаты, предоставляемого SDK) необходимо:
1. Самостоятельно вызвать метод определения доступности оплаты через Tinkoff Pay. Для этого можно использовать метод `TinkoffAcquiring.checkTerminalInfo`. Результат преоразовать с помощью расширения `enableTinkoffPay`
2. При наличии возможности оплаты отобразить кнопку оплаты через Tinkoff Pay в вашем приложении в соответствии с Design Guidelines
3. По нажатию на кнопку создать процесс оплаты с помощью метода `TpayProcess#Init`, получить экземпляр процесса `TpayProcess#get`, и стартовать процесс `TpayProcess#start`  (параметр `version` можно получить
из ответа на шаге 1), отслеживать статус процесса оплаты можно через поле `TpayProcess#state`(под капотом используются корутины, если вы используете что-то другое, воспользуйтесь адаптером) и обработать событие `onUiNeeded` и
использовать `state.deepLink` для открытия приложения с формой оплаты.
4. При необходимости, проверить статус платежа при помощи `TinkoffAcquiring.sdk.getState` (с указанием `paymentId` полученном в `state.paymentId` на
предыдущем шаге); время и частота проверки статуса платежа зависит от нужд клиентского приложения и остается на ваше усмотрение (один из вариантов -
проверять статус платежа при возвращении приложения из фона)

### Yandex Pay
AcquiringSdk имеет возможность использовать внутри себя Yandex Pay в качестве хранилища карт.

Если вы хотите использовать Yandex Pay вместе с AcquiringSdk вам необходимо:
1. Получить в личном кабинете [Yandex](https://pay.yandex.ru/ru/docs/psp/android-sdk) значение `YANDEX_CLIENT_ID`
2. Укажите полученный `YANDEX_CLIENT_ID` в сборочном скрипте [_build.gradle_][build-config] в качестве значения в `manifestPlaceholders`:
```groovy
android {
  defaultConfig {
    manifestPlaceholders = [
      // Подставьте ваш yandex_client_id
      YANDEX_CLIENT_ID: "12345678901234567890",
    ]
  }
}
```
3. Добавить в [_build.gradle_][build-config]
```groovy
implementation 'ru.tinkoff.acquiring:yandexpay:$latestVersion'
```
Крайне не рекомендуется использовать `ru.tinkoff.acquiring:yandexpay` вместе с `com.yandex.pay:core` в рамках вашего приложения, так как
могут возникнуть непредвиденные ошибки.

4. Включить прием платежей через Yandex Pay в Личном кабинете.
5. Проверить Доступ функционала Yandex Pay проверяется через метод `TinkoffAcquiring#checkTerminalInfo`, который возвращает данные обо всех методах оплаты,извлечь данные касательно Yandex Pay  расширение `TerminalInfo#mapYandexPayData`.
6. Кнопка Yandex Pay инкапсулирована во фрагменте `YandexButtonFragment`. Размеры фрагмента-кнопки можете создать самостоятельно, однако если рекомендации по минимальной ширине. Фрагмент можно создать с помощью метода `TinkoffAcquiring.createYandexPayButtonFragment`.
После выбора карты процесс оплаты запуститься самостоятельно. Возможности кастомизации можно посмотреть в [pages](https://github.com/Tinkoff/AcquiringSdkAndroid/wiki/Yandex-pay-in-ASDK).

### Дополнительные возможности

#### Локализация
SDK имеет поддержку 2 локализаций, русскую и английскую.

#### Проведение платежа без открытия экрана оплаты
Для проведения платежа без открытия экрана необходимо создать требуемый процесс для оплаты, передать параметры и написать свою логику обработки состояний платежа.
Для разных способов оплаты существуют разные бизнес сущности процесса оплаты, и разный набор состояний , они лежат в папке `ru.tinkoff.acquiring.sdk.payment`

Пример запуска платежа:
```kotlin 
PaymentByCardProcess.init(sdk, application) // создание процесса платежа
val process = PaymentByCardProcess.get()
process.start(cardData, paymentOptions)     // запуск процесса
scope.launch {
    process.state.collect { handle(it) }    // подписка на события процесса
}         
```

Более подробные варианты использования можно посмотреть в sample проекта.

#### Завершение оплаты с уже существующим paymentId
!Тут нужно доделать, что бы в существующую форму, можно было прокинуть paymentId

Для отображения платежной формы и проведения платежа без вызова метода Init можно передать
значение `SelectCardAndPayState` при вызове `openPaymentScreen`, пример вызова:
```kotlin
val paymentId = 123456789L // некоторый paymentId, полученный ранее при вызове метода Init
tinkoffAcquiring.openPaymentScreen(this@MainActivity, paymentOptions, PAYMENT_REQUEST_CODE, SelectCardAndPayState(paymentId))
```

Для завершения платежа без отображения платежной формы можно использовать метод `TinkoffAcquiring.finishPayment`.

### Структура
SDK состоит из следующих модулей:

#### Core
Является базовым модулем для работы с Tinkoff Acquiring API. Модуль реализует протокол взаимодействия с сервером и позволяет не осуществлять прямых обращений в API. Не зависит от Android SDK и может использоваться в standalone Java приложениях.

Основной класс модуля - **AcquiringSdk** - предоставляет интерфейс для взаимодействия с Tinkoff Acquiring API. Для работы необходимы ключи продавца (см. **Подготовка к работе**).

Подключение:
```groovy
implementation 'ru.tinkoff.acquiring:core:$latestVersion'
```

#### UI
Содержит интерфейс, необходимый для приема платежей через мобильное приложение.

Основной класс - **TinkoffAcquiring** - позволяет:
* открывать экран совершения платежа
* открывать экран привязки карты
* открывать экран оплаты по статическому QR коду
* проводить полную сессию платежа без открытия экранов, с передачей платежных данных
* проводить только подтверждение платежа без открытия экранов, с передачей платежных данных
* настроить экран для приема оплаты из уведомления

#### Card-IO
Модуль для сканирования карты камерой телефона с помощью библиотеки Card-IO.

#### Yandex 
Модуль для работы с библиотекой yandexPay

#### Sample
Содержит пример интеграции Tinkoff Acquiring SDK и модуля сканирования Card-IO в мобильное приложение по продаже книг.

### Proguard
```
-keep class ru.tinkoff.acquiring.sdk.localization.** { *; }
-keep class ru.tinkoff.acquiring.sdk.requests.** { *; }
-keep class ru.tinkoff.acquiring.sdk.models.** { *; }
-keep class ru.tinkoff.acquiring.sdk.yandexpay.models.** { *; } // если подключали яндекс
-keep class ru.rtln.tds.sdk.** { *; }
-keep class org.spongycastle.**
-keep class org.bouncycastle.**
```

### Поддержка
- По возникающим вопросам просьба обращаться на [oplata@tinkoff.ru][support-email]
- Баги и feature-реквесты можно направлять в раздел [issues][issues]
- Документация на [GitHub Pages](https://tinkoff.github.io/AcquiringSdkAndroid/ui/ru.tinkoff.acquiring.sdk/-tinkoff-acquiring/index.html)

[search.maven]: http://search.maven.org/#search|ga|1|ru.tinkoff.acquiring.ui
[build-config]: https://developer.android.com/studio/build/index.html
[support-email]: mailto:oplata@tinkoff.ru
[issues]: https://github.com/Tinkoff/AcquiringSdkAndroid/issues
[acquiring]: https://www.tinkoff.ru/kassa/
[init-documentation]: https://oplata.tinkoff.ru/develop/api/payments/init-request/
[full-doc]: https://github.com/Tinkoff/AcquiringSdkAndroid/blob/master/Android%20SDK.pdf
[network-security-config]:https://developer.android.com/training/articles/security-config
