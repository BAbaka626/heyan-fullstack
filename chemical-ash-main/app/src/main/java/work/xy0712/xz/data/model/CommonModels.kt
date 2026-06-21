package work.xy0712.xz.data.model

data class Park(
    val name: String = "",
    val city: String = "",
    val management_org: String = "",
    val safety_org: String = "",
    val output_2025: Double = 0.0,
    val approved_area: Double = 0.0,
    val built_area: Double = 0.0,
    val enterprise_count: Int = 0,
    val major_hazard_count: Int = 0
)

data class MonthlyScore(
    val month: String = "",
    val previous_score: Double = 0.0,
    val access_count: Int = 0,
    val bonus_score: Double = 0.0,
    val penalty_score: Double = 0.0,
    val final_score: Double = 0.0,
    val grade: String = ""
)

data class AccessStats(
    val month: String = "",
    val access_count: Int = 0,
    val report_count: Int = 0,
    val active_enterprises: Int = 0
)

data class DashboardSummary(
    val enterpriseCount: Int = 0,
    val majorHazardCount: Int = 0,
    val monthlyScore: MonthlyScore = MonthlyScore(),
    val access: AccessStats = AccessStats()
)

data class DashboardResponse(
    val park: Park = Park(),
    val summary: DashboardSummary = DashboardSummary()
)

data class WarningItem(
    val id: Int = 0,
    val park_name: String = "",
    val enterprise_name: String = "",
    val category: String = "",
    val parameter_type: String = "",
    val point_count: Int = 0,
    val alarm_count: Int = 0,
    val repeat_alarm_count: Int = 0,
    val avg_close_minutes: Double = 0.0,
    val timely_rate: Double = 0.0,
    val average_disturbance_rate: Double = 0.0,
    val level: String = "",
    val status: String = "",
    val occurred_at: String = ""
)

data class KnowledgeDocSummary(
    val id: Int = 0,
    val title: String = "",
    val file_path: String = "",
    val doc_type: String = "",
    val source_tag: String = "",
    val content_length: Int = 0
)

data class KnowledgeChunk(
    val chunk_index: Int = 0,
    val content: String = ""
)

data class KnowledgeDocDetail(
    val id: Int = 0,
    val title: String = "",
    val file_path: String = "",
    val doc_type: String = "",
    val source_tag: String = "",
    val content: String = "",
    val chunks: List<KnowledgeChunk> = emptyList()
)

// List wrappers
data class WarningListResponse(val items: List<WarningItem> = emptyList())
data class EnterpriseListResponse(val items: List<EnterpriseSummary> = emptyList())
data class KnowledgeDocListResponse(val items: List<KnowledgeDocSummary> = emptyList())
data class InspectionRouteListResponse(val items: List<InspectionRoute> = emptyList())
data class InspectionRecordListResponse(val items: List<InspectionRecord> = emptyList())
data class IssueListResponse(val items: List<IssueRecord> = emptyList())
data class WorkRecordListResponse(val items: List<WorkRecord> = emptyList())
data class ScoreListResponse(val items: List<MonthlyScore> = emptyList())

data class ReportApiResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: ReportResponse = ReportResponse()
)

data class ReportResponse(
    val reportDate: String = "",
    val leaderName: String = "",
    val safetyPromise: SafetyPromiseReport = SafetyPromiseReport(),
    val closedManagement: ClosedManagementReport = ClosedManagementReport(),
    val specialOperations: SpecialOperationsReport = SpecialOperationsReport()
)

data class SafetyPromiseReport(
    val totalEnterprises: Int = 0,
    val completedCount: Int = 0,
    val uncommittedCompanies: List<String> = emptyList(),
    val promiseRate: Double = 0.0,
    val specialWorkCompanies: List<String> = emptyList(),
    val deviceStartStopCompanies: List<String> = emptyList(),
    val maintenanceCompanies: List<String> = emptyList()
)

data class ClosedManagementReport(
    val totalVehicles: Int = 0,
    val hazmatVehicles: Int = 0,
    val totalTonnage: Double = 0.0,
    val topChemicals: List<TopChemicalReport> = emptyList(),
    val randomCheck: VehicleRandomCheckReport = VehicleRandomCheckReport()
)

data class TopChemicalReport(
    val rank: Int = 0,
    val name: String = "",
    val weight: Double = 0.0
)

data class VehicleRandomCheckReport(
    val consistent: Boolean = false,
    val speedingCount: Int = 0,
    val illegalParkingCount: Int = 0,
    val trackTraceable: Boolean = false
)

data class SpecialOperationsReport(
    val onlinePromises: Int = 0,
    val actualOperations: Int = 0,
    val superHotWorkCount: Int = 0,
    val level1HotWorkCount: Int = 0,
    val confinedSpaceCount: Int = 0,
    val operationList: List<SpecialOperationItem> = emptyList()
)

data class SpecialOperationItem(
    val enterpriseName: String = "",
    val superHotCount: Int = 0,
    val level1HotCount: Int = 0,
    val confinedSpaceCount: Int = 0,
    val details: String = ""
)
