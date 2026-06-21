package work.xy0712.xz.data.model

data class InspectionRoute(
    val id: String = "",
    val route_name: String = "",
    val route_type: String = "",
    val organization_name: String = "",
    val frequency: String = "",
    val point_count: Int = 0,
    val description: String = ""
)

data class InspectionRecord(
    val id: String = "",
    val route_id: String? = null,
    val route_name: String = "",
    val inspector_name: String = "",
    val organization_name: String = "",
    val checkin_address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val voice_text: String = "",
    val status: String = "",
    val issue_count: Int = 0,
    val inspected_at: String = ""
)

data class InspectionRecordCreateRequest(
    val routeId: String = "",
    val routeName: String = "",
    val inspectorName: String = "",
    val organizationName: String = "",
    val checkinAddress: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val voiceText: String = "",
    val status: String = "已完成",
    val issueCount: Int = 0
)

data class IssueRecord(
    val id: String = "",
    val source_type: String = "",
    val related_record_id: String? = null,
    val enterprise_name: String = "",
    val issue_title: String = "",
    val issue_level: String = "",
    val description: String = "",
    val rectify_requirement: String = "",
    val rectify_deadline: String = "",
    val status: String = "",
    val created_at: String = ""
)

data class IssueCreateRequest(
    val sourceType: String = "巡检问题",
    val relatedRecordId: String? = null,
    val enterpriseName: String = "",
    val issueTitle: String = "",
    val issueLevel: String = "一般",
    val description: String = "",
    val rectifyRequirement: String = "",
    val rectifyDeadline: String = "",
    val status: String = "待整改"
)

data class WorkRecord(
    val id: String = "",
    val record_type: String = "",
    val title: String = "",
    val organization_name: String = "",
    val description: String = "",
    val created_by: String = "",
    val created_at: String = ""
)

data class WorkRecordCreateRequest(
    val recordType: String = "工作记录",
    val title: String = "",
    val organizationName: String = "",
    val description: String = "",
    val createdBy: String = "平板用户"
)
