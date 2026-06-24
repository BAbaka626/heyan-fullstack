package work.xy0712.xz.data.model

import com.google.gson.annotations.SerializedName

data class EnterpriseSummary(
    val id: String = "",
    val name: String = "",
    val code: String = "",
    val social_credit_code: String = "",
    val park_name: String = "",
    val address: String = "",
    val business_scope: String = "",
    val legal_person: String = "",
    val safety_leader: String = "",
    val employee_count: Int = 0,
    val hazard_level: String = "",
    val evaluation_level: String = "",
    val is_chemical_enterprise: Int = 0,
    @SerializedName(value = "QYFXDJ", alternate = ["qyfxdj"])
    val qyfxdj: Int? = null,
    val computedRiskLevel: String = "",
    val computedRiskLabel: String = "",
    val computedRiskColor: String = "",
    val computedRiskWeight: Int = 0,
    val riskReason: String = "",
    val riskLevel: String = "",
    val risk_level: String = "",
    val riskColor: String = "",
    val doublePreventionScore: Double? = null,
    val doublePreventionEffectLevel: String = ""
)

data class PersonRecord(
    val person_name: String = "",
    val position_name: String = "",
    val mobile_number: String = "",
    val affiliated_department: String = ""
)

data class ChemicalRecord(
    val name: String = "",
    val alias: String = "",
    val cas: String = "",
    val annual_output: String = "",
    val is_hazardous: Int = 0
)

data class MajorHazardRecord(
    val hazard_name: String = "",
    val level: String = "",
    val responsible: String = "",
    val responsible_phone: String = "",
    val technical: String = "",
    val operation: String = ""
)

data class RiskUnitRecord(
    val risk_class: String = "",
    val risk_unit_name: String = "",
    val liable_person: String = ""
)

data class RiskMeasureRecord(
    val classify1: String = "",
    val classify2: String = "",
    val classify3: String = "",
    val measure_desc: String = "",
    val troubleshoot_content: String = ""
)

data class EnterpriseDetailResponse(
    val enterprise: EnterpriseSummary = EnterpriseSummary(),
    val personnel: List<PersonRecord> = emptyList(),
    val chemicals: List<ChemicalRecord> = emptyList(),
    val majorHazards: List<MajorHazardRecord> = emptyList(),
    val riskUnits: List<RiskUnitRecord> = emptyList(),
    val riskMeasures: List<RiskMeasureRecord> = emptyList()
)

data class EnterpriseRiskSummary(
    val riskScore: Double = 0.0,
    val riskLevel: String = "",
    val onlinePersonnel: Int = 0,
    val activeAlarmCount: Int = 0,
    val openIssueCount: Int = 0,
    val inspectionCount: Int = 0,
    val activityIndex: Double = 0.0,
    val majorHazardCount: Int = 0,
    val employeeCount: Int = 0,
    val safetyLeader: String = "",
    val legalPerson: String = "",
    val parkName: String = "",
    val hazardLevel: String = "",
    val evaluationLevel: String = "",
    val socialCreditCode: String = "",
    val code: String = ""
)

data class EnterpriseRiskPoint(
    val date: String = "",
    val score: Double? = null,
    val level: String = "",
    val onlinePersonnel: Int? = null,
    val inspections: Int? = null,
    val activityIndex: Double? = null,
    val alarms: Int? = null,
    val issues: Int? = null,
    val name: String = "",
    val value: Double? = null
)

data class EnterpriseRiskCharts(
    val riskTrend: List<EnterpriseRiskPoint> = emptyList(),
    val activityTrend: List<EnterpriseRiskPoint> = emptyList(),
    val issueAlarmTrend: List<EnterpriseRiskPoint> = emptyList()
)

data class EnterpriseRiskProfile(
    val enterprise: EnterpriseSummary = EnterpriseSummary(),
    val summary: EnterpriseRiskSummary = EnterpriseRiskSummary(),
    val charts: EnterpriseRiskCharts = EnterpriseRiskCharts(),
    val personnelHighlights: List<PersonRecord> = emptyList(),
    val majorHazards: List<MajorHazardRecord> = emptyList()
)

data class RiskLevelStat(
    val levelCode: String,
    val levelName: String,
    val colorHex: String,
    val count: Int
)
