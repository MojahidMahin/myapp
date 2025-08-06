package com.localllm.myapplication.data

data class TelegramMessage(
    val message_id: Long,
    val chat: Chat,
    val text: String?
)

data class Chat(
    val id: Long,
    val first_name: String?,
    val last_name: String?,
    val username: String?
)

data class Update(
    val update_id: Long,
    val message: TelegramMessage?
)

data class GetUpdatesResponse(val result: List<Update>)
