# Pushover4k

![master](https://github.com/YoouDo/pushover4k/actions/workflows/master.yaml/badge.svg) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=YoouDo_pushover4k&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=YoouDo_pushover4k)

### A Pushover client in Kotlin

A simple library to use [Pushover](https://www.pushover.net) messages in Kotlin applications.

Use the following dependency in your application:
```kotlin
implementation("de.kleinkop:pushover4k:1.2.0")
```

### Changes
#### Release 1.2.0
Http4k framework was replaced by JDK internal HttpClient to reduce dependencies.

The method `PushoverClient.sendMessage()` will now provide the details of used Pushover application if available.

### Examples:

Create a client using your Pushover tokens:

```kotlin
val pushoverClient : PushoverClient = PushoverHttpClient(
    appToken = "your app token",
    userToken = "your user token",
    apiHost = "Host of Pushover api",        // default: https://api.pushover.net
    retryAttempts = 3,                       // number of retries, default: 5
    retryInterval = 6000L,                   // retry interval in millis, default: 5000L
    httpTimeout = 20L,                       // http connect timeout in seconds, default: 30L
    registry = SimpleMeterRegistry()         // optional Micrometer registry
)
```

Send a simple message with default values:

```kotlin
pushoverClient.sendMessage(
    Message(
        message = "This is a test message"
    )
)
```

Send a message using all available options excluding those for emergency messages: 

```kotlin
pushoverClient.sendMessage(
    Message(
        message = "This is another test message",
        title = "A title",
        priority = Priority.HIGH,
        url = "https://www.pushover.net",
        urlTitle = "Pushover",
        devices = listOf("device1", "device2"),
        timestamp = OffsetDateTime.now().toLocalDateTimeUTC(),
        html = true,
        sound = "magic",
        image = File("image.png"),
        monospace = false,
    )
)
```

Send an emergency message:
```kotlin
pushoverClient.sendMessage(
    Message(
        message = "Testing emergency",
        priority = Priority.EMERGENCY,
        retry = 60,                                    // required: number of retries from Pushover server
        expire = 3600,                                 // required: time to live of emergency message
        tags = listOf("tag1", "tag2")                  // optional: tags for emergency message
    )
)
```
Please note that you have to provide values for `retry` and `expire`. Usings `tags` is optional.

---

All properties of `Message`:

| property  | type          |      optional      | description                                                                           |
|-----------|---------------|:------------------:|---------------------------------------------------------------------------------------|
| message   | String        |                    | The only mandatory parameter                                                          |
| title     | String        | :heavy_check_mark: | If not provided the Pushover default will be used                                     |
| priority  | Priority      | :heavy_check_mark: | Priority as defined by [Pushover](https://pushover.net/api#priority)                  |
| url       | String        | :heavy_check_mark: | Will be shown as supplementary URL in the message                                     |
| urlTitle  | String        | :heavy_check_mark: | Supplementary URL will be shown with this title                                       |
| devices   | List<String>  | :heavy_check_mark: | Message will be sent to these devices only                                            |
| timestamp | LocalDateTime | :heavy_check_mark: | This time in UTC will be used as a message time                                       |
| html      | Boolean       | :heavy_check_mark: | Use simple HTML tags in the message                                                   |
| sound     | String        | :heavy_check_mark: | Client device will use this sound.                                                    |
| image     | File          | :heavy_check_mark: | Image will be added to message                                                        |
| monospace | Boolean       | :heavy_check_mark: | Message will be rendered with monospace font. Only useable in non-html messages       |
| retry     | Int           | :heavy_check_mark: | Emergency messages will be retried with this interval in seconds. Minimum value is 30 |
| expire    | Int           | :heavy_check_mark: | Emergency message will expire after this period in seconds                            |
| tags      | List<String>  | :heavy_check_mark: | Tags to be added to emergency message. May be used for cancellations                  |
| ttl       | Int           | :heavy_check_mark: | Number of seconds that the message will live, before being deleted automatically      |


#### Response object
Sending a message will return information provided by Pushover using following data class:

```kotlin
data class PushoverResponse(
    val status: Int,
    val request: String,
    val user: String? = null,
    val errors: List<String>? = null,
    val receipt: String? = null,
    val canceled: Int? = null,
    val applicationUsage: ApplicationUsage?,
)
```
 
---
#### Emergency messages

Emergency messages can be queried and cancelled like this:
 ```kotlin
 pushoverClient.getEmergencyState("receipt-id of emergency message")
 ```

Cancel by receipt id:
```kotlin
pushoverClient.cancelEmergencyMessage("receipt-id of emergency message")
```

Cancel by tag with name `TAG`:
```kotlin
pushoverClient.cancelEmergencyMessageByTag("TAG")
```
