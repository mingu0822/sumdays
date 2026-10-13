package com.example.sumdays.stickers

import com.example.sumdays.network.apiService.ApiResponse
import retrofit2.Response
import retrofit2.http.*

interface StickerApi {
    @GET("api/stickers/{owner}/{date}")
    suspend fun list(@Header("Authorization") token: String, @Path("owner") owner: Int,
                     @Path("date") date: String): Response<ApiResponse<List<DiarySticker>>>

    @PUT("api/stickers/{owner}/{date}/{id}")
    suspend fun place(@Header("Authorization") token: String, @Path("owner") owner: Int,
                      @Path("date") date: String, @Path("id") id: String,
                      @Body body: PlaceStickerBody): Response<ApiResponse<DiarySticker>>

    @PATCH("api/stickers/{owner}/{date}/{id}")
    suspend fun update(@Header("Authorization") token: String, @Path("owner") owner: Int,
                       @Path("date") date: String, @Path("id") id: String,
                       @Body body: StickerGeometry): Response<ApiResponse<DiarySticker>>

    @DELETE("api/stickers/{owner}/{date}/{id}")
    suspend fun remove(@Header("Authorization") token: String, @Path("owner") owner: Int,
                       @Path("date") date: String, @Path("id") id: String): Response<ApiResponse<Map<String, String>>>
}
