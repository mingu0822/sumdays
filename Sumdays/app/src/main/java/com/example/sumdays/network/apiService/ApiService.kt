package com.example.sumdays.network.apiService

import com.example.sumdays.daily.diary.AnalysisRequest
import com.example.sumdays.daily.memo.MergePassPurchase
import com.example.sumdays.daily.memo.MergeMarbleState
import com.example.sumdays.daily.memo.MergeRequest
import com.example.sumdays.daily.memo.MergeTicketPurchase
import com.example.sumdays.daily.memo.MoodRequest
import com.example.sumdays.data.sync.SyncFetchResponse
import com.example.sumdays.data.sync.SyncRequest
import com.example.sumdays.data.sync.SyncResponse
import com.example.sumdays.mission.AdRewardClaimRequest
import com.example.sumdays.mission.FriendDiaryViewRequest
import com.example.sumdays.mission.GooglePlayPointRedeemRequest
import com.example.sumdays.mission.GooglePlayPointRedeemResponse
import com.example.sumdays.mission.LegacyPointBalanceRequest
import com.example.sumdays.mission.MissionApiResponse
import com.example.sumdays.mission.MissionDashboardResponse
import com.example.sumdays.mission.PointRewardResponse
import com.example.sumdays.mission.PointWalletResponse
import com.example.sumdays.mission.ShopCatalogResponse
import com.example.sumdays.mission.ShopPurchaseRequest
import com.example.sumdays.mission.ShopPurchaseResponse
import com.example.sumdays.mission.WeeklySummaryViewRequest
import com.example.sumdays.network.ChangePasswordRequest
import com.example.sumdays.network.ChangePasswordResponse
import com.example.sumdays.network.LoginRequest
import com.example.sumdays.network.LoginResponse
import com.example.sumdays.network.GoogleLoginRequest
import com.example.sumdays.network.OcrResponse
import com.example.sumdays.network.STTResponse
import com.example.sumdays.network.SignupRequest
import com.example.sumdays.network.SignupResponse
import com.example.sumdays.network.StyleExtractionResponse
import com.example.sumdays.network.UpdateNicknameRequest
import com.example.sumdays.network.UpdateNicknameResponse
import com.example.sumdays.network.WeekAnalysisRequest
import com.example.sumdays.network.WeekAnalysisResponse
import com.google.gson.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Streaming

interface ApiService {
    @POST("/api/missions/shop/consume")
    suspend fun consumeShopItems(
        @Header("Authorization") token: String,
        @Body request: com.example.sumdays.alchemy.ConsumeItemsRequest,
    ): Response<MissionApiResponse<com.example.sumdays.alchemy.ConsumeItemsResponse>>

    @GET("/api/missions/merge-marbles")
    suspend fun getMergeMarble(@Header("Authorization") token: String): Response<MissionApiResponse<MergeMarbleState>>

    @POST("/api/missions/merge-marbles/tickets/purchase")
    suspend fun purchaseMergeTicket(
        @Header("Authorization") token: String,
        @Body request: MergeTicketPurchase,
    ): Response<MissionApiResponse<MergeMarbleState>>

    @POST("/api/missions/merge-marbles/pass/redeem")
    suspend fun redeemMergePass(
        @Header("Authorization") token: String,
        @Body request: MergePassPurchase,
    ): Response<MissionApiResponse<MergeMarbleState>>
    @GET("/api/missions")
    suspend fun getMissionDashboard(
        @Header("Authorization") token: String,
    ): Response<MissionApiResponse<MissionDashboardResponse>>

    @POST("/api/missions/{code}/claim")
    suspend fun claimMissionReward(
        @Header("Authorization") token: String,
        @retrofit2.http.Path("code") missionCode: String,
    ): Response<MissionApiResponse<PointRewardResponse>>

    @POST("/api/missions/ad-reward")
    suspend fun claimAdReward(
        @Header("Authorization") token: String,
        @Body request: AdRewardClaimRequest,
    ): Response<MissionApiResponse<PointRewardResponse>>

    @POST("/api/missions/events/friend-diary-view")
    suspend fun recordFriendDiaryView(
        @Header("Authorization") token: String,
        @Body request: FriendDiaryViewRequest,
    ): Response<MissionApiResponse<Unit>>

    @POST("/api/missions/events/weekly-summary-view")
    suspend fun recordWeeklySummaryView(
        @Header("Authorization") token: String,
        @Body request: WeeklySummaryViewRequest,
    ): Response<MissionApiResponse<Unit>>

    @POST("/api/missions/wallet/initialize")
    suspend fun initializePointWallet(
        @Header("Authorization") token: String,
        @Body request: LegacyPointBalanceRequest,
    ): Response<MissionApiResponse<PointWalletResponse>>

    @POST("/api/missions/play/points/redeem")
    suspend fun redeemGooglePlayPoints(
        @Header("Authorization") token: String,
        @Body request: GooglePlayPointRedeemRequest,
    ): Response<MissionApiResponse<GooglePlayPointRedeemResponse>>

    @POST("/api/missions/shop/purchase")
    suspend fun purchaseShopProduct(
        @Header("Authorization") token: String,
        @Body request: ShopPurchaseRequest,
    ): Response<MissionApiResponse<ShopPurchaseResponse>>

    @GET("/api/missions/shop")
    suspend fun getShopCatalog(
        @Header("Authorization") token: String,
    ): Response<MissionApiResponse<ShopCatalogResponse>>

    // 로그인 
    @POST("/api/auth/login")
    fun login(@Body request: LoginRequest): Call<LoginResponse>

    @POST("/api/auth/google")
    fun googleLogin(@Body request: GoogleLoginRequest): Call<LoginResponse>
    
    // 회원가입
    @POST("/api/auth/signup")
    fun signup(@Body request: SignupRequest): Call<SignupResponse>

    // 닉네임 변경
    @PUT("api/auth/nickname")
    fun updateNickname(@Header("Authorization") token: String, @Body request: UpdateNicknameRequest): Call<UpdateNicknameResponse>

    // 비밀번호 변경
    @PUT("api/auth/password")
    fun changePassword(@Header("Authorization") token: String, @Body request: ChangePasswordRequest): Call<ChangePasswordResponse>

    // 프로필 사진
    @Multipart
    @POST("/api/auth/profile-image")
    suspend fun updateProfileImage(
        @Header("Authorization") token: String,
        @Part profileImage: MultipartBody.Part
    ): Response<UpdateProfileImageResponse>

    @POST("/api/auth/profile-avatar")
    suspend fun updateProfileAvatar(
        @Header("Authorization") token: String,
        @Body request: UpdateProfileAvatarRequest
    ): Response<UpdateProfileAvatarResponse>

    // 백업 
    @Multipart
    @POST("/api/db/sync")
    suspend fun syncData(
        @Header("Authorization") token: String,

        @Part("syncRequest")
        syncRequest: RequestBody,

        @Part
        photos: List<MultipartBody.Part>
    ): Response<SyncResponse>
    
    // 동기화
    @GET("/api/db/sync")
    suspend fun fetchServerData(
        @Header("Authorization") token: String
    ): Response<SyncFetchResponse>

    @POST("/api/ai/merge")
    suspend fun mergeMemos(@Header("Authorization") token: String, @Body req: MergeRequest): Response<JsonObject>
    @POST("/api/ai/style-preview")
    suspend fun generateStylePreview(@Header("Authorization") token: String, @Body req: MergeRequest): Response<JsonObject>
    @Streaming
    @POST("/api/ai/merge")
    fun mergeMemosStream(@Header("Authorization") token: String, @Body request: MergeRequest): Call<ResponseBody>
    @Multipart
    @POST("/api/ai/stt/memo")
    fun transcribeAudio(
        @Part audio: MultipartBody.Part
    ): Call<STTResponse>
    @Multipart
    @POST("/api/ai/ocr/memo")
    fun extractTextFromImage(
        @Part image: MultipartBody.Part,
        @Part("type") type: RequestBody
    ): Call<OcrResponse>
    @POST("/api/ai/analyze")
    suspend fun diaryAnalyze(@Body req: AnalysisRequest): Response<JsonObject>

    @POST("/api/ai/mood")
    suspend fun generateMood(@Body req: MoodRequest): retrofit2.Response<com.google.gson.JsonObject>

    @Multipart
    @POST("/api/ai/extract-style")
    fun extractStyle(
        @Part("diaries") diaries: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Call<StyleExtractionResponse>

    @POST("/api/ai/summarize-week")
    suspend fun summarizeWeek(@Body request: WeekAnalysisRequest): Response<WeekAnalysisResponse>
}



// 응답 DTO (nullable 권장)
data class MergeResponse(
    val merged_memo: String?,
    val count: Int?,
    val end_flag: Boolean?
)

data class UpdateProfileImageResponse(
    val success: Boolean,
    val profileImageUrl: String?
)
data class UpdateProfileAvatarRequest(
    val faceId: Int,
    val eyesId: Int,
    val mouthId: Int,
    val accessoryId: Int
)

data class UpdateProfileAvatarResponse(
    val success: Boolean,
    val message: String?
)
