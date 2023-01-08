package de.kleinkop.pushover4k.client

interface PushoverClient {
    fun sendMessage(msg: Message): PushoverResponse

    fun getSounds(): SoundResponse

    fun getEmergencyState(receiptId: String): ReceiptResponse

    fun cancelEmergencyMessage(receiptId: String): PushoverResponse

    fun cancelEmergencyMessageByTag(tag: String): PushoverResponse
}
