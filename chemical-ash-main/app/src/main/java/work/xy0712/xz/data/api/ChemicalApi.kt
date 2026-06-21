package work.xy0712.xz.data.api

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import work.xy0712.xz.data.model.AskRequest
import work.xy0712.xz.data.model.AskResponse
import work.xy0712.xz.data.model.DashboardResponse
import work.xy0712.xz.data.model.EnterpriseDetailResponse
import work.xy0712.xz.data.model.EnterpriseListResponse
import work.xy0712.xz.data.model.EnterpriseRiskProfile
import work.xy0712.xz.data.model.InspectionRecord
import work.xy0712.xz.data.model.InspectionRecordCreateRequest
import work.xy0712.xz.data.model.InspectionRecordListResponse
import work.xy0712.xz.data.model.InspectionRouteListResponse
import work.xy0712.xz.data.model.IssueCreateRequest
import work.xy0712.xz.data.model.IssueListResponse
import work.xy0712.xz.data.model.IssueRecord
import work.xy0712.xz.data.model.KnowledgeDocDetail
import work.xy0712.xz.data.model.KnowledgeDocListResponse
import work.xy0712.xz.data.model.ReportApiResponse
import work.xy0712.xz.data.model.ReportResponse
import work.xy0712.xz.data.model.ScoreListResponse
import work.xy0712.xz.data.model.WarningListResponse
import work.xy0712.xz.data.model.WorkRecord
import work.xy0712.xz.data.model.WorkRecordCreateRequest
import work.xy0712.xz.data.model.WorkRecordListResponse

interface ChemicalApi {
    @GET("api/health")
    suspend fun healthCheck(): Map<String, String>

    @GET("api/dashboard")
    suspend fun getDashboard(): DashboardResponse

    @GET("api/warnings")
    suspend fun getWarnings(): WarningListResponse

    @GET("api/enterprises")
    suspend fun getEnterprises(@Query("q") query: String = ""): EnterpriseListResponse

    @GET("api/enterprises/{id}")
    suspend fun getEnterpriseDetail(@Path("id") id: String): EnterpriseDetailResponse

    @GET("api/enterprises/{id}/risk-profile")
    suspend fun getEnterpriseRiskProfile(@Path("id") id: String): EnterpriseRiskProfile

    @GET("api/knowledge/docs")
    suspend fun getKnowledgeDocs(): KnowledgeDocListResponse

    @GET("api/knowledge/docs/{id}")
    suspend fun getKnowledgeDocDetail(@Path("id") id: Int): KnowledgeDocDetail

    @GET("api/inspection/routes")
    suspend fun getInspectionRoutes(): InspectionRouteListResponse

    @GET("api/inspection/records")
    suspend fun getInspectionRecords(): InspectionRecordListResponse

    @POST("api/inspection/records")
    suspend fun createInspectionRecord(@Body request: InspectionRecordCreateRequest): InspectionRecord

    @GET("api/issues")
    suspend fun getIssues(): IssueListResponse

    @POST("api/issues")
    suspend fun createIssue(@Body request: IssueCreateRequest): IssueRecord

    @GET("api/work-records")
    suspend fun getWorkRecords(): WorkRecordListResponse

    @POST("api/work-records")
    suspend fun createWorkRecord(@Body request: WorkRecordCreateRequest): WorkRecord

    @GET("api/scores/monthly")
    suspend fun getScores(): ScoreListResponse

    @GET("api/reports/daily")
    suspend fun getDailyReport(@Query("date") date: String = ""): ReportApiResponse

    @GET("api/reports/weekly")
    suspend fun getWeeklyReport(): ReportApiResponse

    @GET("api/reports/monthly")
    suspend fun getMonthlyReport(@Query("month") month: String = ""): ReportApiResponse

    @POST("api/qa/ask")
    suspend fun ask(@Body request: AskRequest): AskResponse
}

object ChemicalApiFactory {
    fun create(baseUrl: String): ChemicalApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val errorInterceptor = okhttp3.Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (!response.isSuccessful) {
                val bodyString = response.peekBody(Long.MAX_VALUE).string()
                android.util.Log.e(
                    "ChemicalApi",
                    "HTTP ${response.code} on ${request.method} ${request.url} | Body: $bodyString"
                )
            }
            response
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(errorInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()

        return retrofit.create(ChemicalApi::class.java)
    }
}
