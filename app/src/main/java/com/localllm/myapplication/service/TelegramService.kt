package com.localllm.myapplication.service
import com.localllm.myapplication.data.GetUpdatesResponse

import retrofit2.Response
import retrofit2.http.*

interface TelegramService {

    @GET
    suspend fun getUpdates(
        @Url fullUrl: String
    ): Response<GetUpdatesResponse>

    @FormUrlEncoded
    @POST
    suspend fun sendMessage(
        @Url fullUrl: String,
        @Field("chat_id") chatId: String,
        @Field("text") text: String
    ): Response<Unit>
}
