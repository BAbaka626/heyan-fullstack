package work.xy0712.xz.data.repository

import work.xy0712.xz.data.api.ChemicalApi
import work.xy0712.xz.data.api.ChemicalApiFactory
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
import work.xy0712.xz.data.model.ReportResponse
import work.xy0712.xz.data.model.ScoreListResponse
import work.xy0712.xz.data.model.WarningListResponse
import work.xy0712.xz.data.model.WorkRecord
import work.xy0712.xz.data.model.WorkRecordCreateRequest
import work.xy0712.xz.data.model.WorkRecordListResponse

class ChemicalRepository(private val baseUrl: String) {

    private val api: ChemicalApi by lazy {
        ChemicalApiFactory.create(baseUrl)
    }

    // ─── Dashboard ───
    suspend fun getDashboard(): DashboardResponse = api.getDashboard()

    // ─── Warnings ───
    suspend fun getWarnings(): WarningListResponse = api.getWarnings()

    // ─── Enterprises ───
    suspend fun getEnterprises(query: String = ""): EnterpriseListResponse {
        return api.getEnterprises(query)
    }

    suspend fun getEnterpriseDetail(id: String): EnterpriseDetailResponse {
        return api.getEnterpriseDetail(id)
    }

    suspend fun getEnterpriseRiskProfile(id: String): EnterpriseRiskProfile {
        return api.getEnterpriseRiskProfile(id)
    }

    // ─── Knowledge ───
    suspend fun getKnowledgeDocs(): KnowledgeDocListResponse = api.getKnowledgeDocs()
    suspend fun getKnowledgeDocDetail(id: Int): KnowledgeDocDetail = api.getKnowledgeDocDetail(id)

    // ─── Inspection ───
    suspend fun getInspectionRoutes(): InspectionRouteListResponse = api.getInspectionRoutes()
    suspend fun getInspectionRecords(): InspectionRecordListResponse = api.getInspectionRecords()
    suspend fun createInspectionRecord(request: InspectionRecordCreateRequest): InspectionRecord =
        api.createInspectionRecord(request)

    // ─── Issues ───
    suspend fun getIssues(): IssueListResponse = api.getIssues()
    suspend fun createIssue(request: IssueCreateRequest): IssueRecord = api.createIssue(request)

    // ─── Work Records ───
    suspend fun getWorkRecords(): WorkRecordListResponse = api.getWorkRecords()
    suspend fun createWorkRecord(request: WorkRecordCreateRequest): WorkRecord =
        api.createWorkRecord(request)

    // ─── Scores ───
    suspend fun getScores(): ScoreListResponse = api.getScores()

    // ─── Reports ───
    suspend fun getDailyReport(date: String = ""): ReportResponse = api.getDailyReport(date).data
    suspend fun getWeeklyReport(): ReportResponse = api.getWeeklyReport().data
    suspend fun getMonthlyReport(month: String = ""): ReportResponse = api.getMonthlyReport(month).data

    // ─── QA ───
    suspend fun ask(question: String, history: List<work.xy0712.xz.data.model.AskHistoryTurn>): AskResponse {
        return api.ask(AskRequest(question, history))
    }
}
