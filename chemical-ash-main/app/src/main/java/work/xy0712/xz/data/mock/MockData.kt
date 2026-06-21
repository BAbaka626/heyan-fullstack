package work.xy0712.xz.data.mock

import work.xy0712.xz.data.model.EnterpriseRiskPoint
import work.xy0712.xz.data.model.EnterpriseSummary
import work.xy0712.xz.data.model.ClosedManagementReport
import work.xy0712.xz.data.model.ReportResponse
import work.xy0712.xz.data.model.RiskLevelStat
import work.xy0712.xz.data.model.SafetyPromiseReport
import work.xy0712.xz.data.model.SpecialOperationItem
import work.xy0712.xz.data.model.SpecialOperationsReport
import work.xy0712.xz.data.model.TopChemicalReport
import work.xy0712.xz.data.model.VehicleRandomCheckReport

object MockData {

    const val USE_MOCK = false

    val riskLevelStats: List<RiskLevelStat> = listOf(
        RiskLevelStat("RED", "极高风险", "#D32F2F", 3),
        RiskLevelStat("ORANGE", "高风险", "#F57C00", 5),
        RiskLevelStat("YELLOW", "中风险", "#FBC02D", 12),
        RiskLevelStat("BLUE", "低风险", "#1976D2", 15)
    )

    val mockEnterprisesByRiskLevel: List<EnterpriseSummary> = listOf(
        EnterpriseSummary(
            id = "mock-ent-001", name = "广东宝时有限公司", code = "BS2024001",
            social_credit_code = "91440101MA5CXXXXXX", park_name = "广州市南沙小虎化工区",
            address = "小虎岛化工园区A区3号", business_scope = "危险化学品生产、储存",
            legal_person = "王建国", safety_leader = "李明", employee_count = 120,
            hazard_level = "一级", evaluation_level = "极高风险", is_chemical_enterprise = 1
        ),
        EnterpriseSummary(
            id = "mock-ent-002", name = "南沙石化仓储公司", code = "NS2024002",
            social_credit_code = "91440101MA5CYYYYYY", park_name = "广州市南沙小虎化工区",
            address = "小虎岛化工园区B区7号", business_scope = "石化产品仓储",
            legal_person = "张华", safety_leader = "赵强", employee_count = 85,
            hazard_level = "一级", evaluation_level = "极高风险", is_chemical_enterprise = 1
        ),
        EnterpriseSummary(
            id = "mock-ent-003", name = "恒泰新材料科技有限公司", code = "HT2024003",
            social_credit_code = "91440101MA5CZZZZZZ", park_name = "广州市南沙小虎化工区",
            address = "小虎岛化工园区C区12号", business_scope = "新材料研发与生产",
            legal_person = "陈伟", safety_leader = "刘洋", employee_count = 65,
            hazard_level = "二级", evaluation_level = "高风险", is_chemical_enterprise = 1
        ),
        EnterpriseSummary(
            id = "mock-ent-004", name = "安能物流南沙分公司", code = "AN2024004",
            social_credit_code = "91440101MA5CAAAAAA", park_name = "广州市南沙小虎化工区",
            address = "小虎岛化工园区D区5号", business_scope = "危险品运输",
            legal_person = "孙丽", safety_leader = "周斌", employee_count = 45,
            hazard_level = "三级", evaluation_level = "中风险", is_chemical_enterprise = 0
        ),
        EnterpriseSummary(
            id = "mock-ent-005", name = "绿源环保科技有限公司", code = "LY2024005",
            social_credit_code = "91440101MA5CBBBBBB", park_name = "广州市南沙小虎化工区",
            address = "小虎岛化工园区E区8号", business_scope = "环保设备制造",
            legal_person = "吴刚", safety_leader = "郑芳", employee_count = 32,
            hazard_level = "四级", evaluation_level = "低风险", is_chemical_enterprise = 0
        )
    )

    fun getMockSuggestions(enterpriseName: String): List<String> = listOf(
        "建议 $enterpriseName 加强重大危险源区域的安全巡查频次，当前风险评分较高。",
        "该企业近30天内报警处置及时率低于平均水平，建议优化应急响应流程。",
        "建议补充完善装卸作业区的安全警示标识，并开展专项培训。"
    )

    fun getMockReportContent(reportType: String): String = when (reportType) {
        "weekly" -> """
            ## 2026年4月第3周安全周报

            ### 一、总体态势
            本周园区安全生产总体平稳，共发生预警事件2起，均已完成处置。

            ### 二、重点工作
            1. 完成重大危险源专项检查，覆盖12家企业、34个危险源单元。
            2. 开展园区消防安全演练1次，参演人员120人次。
            3. 督促2家企业完成隐患整改。

            ### 三、下周计划
            - 继续推进"一企一档"数据完善工作
            - 开展防雷防静电专项检查
        """.trimIndent()
        "monthly" -> """
            ## 2026年4月管理月报

            ### 一、安全运行概况
            本月园区企业总体安全运行平稳，月度评分为88分，较上月提升2分。

            ### 二、风险预警情况
            - 红色预警：0起
            - 橙色预警：1起
            - 黄色预警：3起
            - 蓝色预警：5起

            ### 三、隐患排查治理
            本月共排查隐患15项，已整改完成12项，整改率80%。

            ### 四、下月工作重点
            1. 推进双重预防机制数字化运行效果评估
            2. 开展汛期安全检查
        """.trimIndent()
        else -> "暂无报告内容"
    }

    val mockRiskTrend: List<EnterpriseRiskPoint> = listOf(
        EnterpriseRiskPoint(date = "2026-04-18", score = 72.5, level = "中风险"),
        EnterpriseRiskPoint(date = "2026-04-19", score = 75.0, level = "中风险"),
        EnterpriseRiskPoint(date = "2026-04-20", score = 71.0, level = "中风险"),
        EnterpriseRiskPoint(date = "2026-04-21", score = 78.0, level = "中风险"),
        EnterpriseRiskPoint(date = "2026-04-22", score = 82.0, level = "中风险"),
        EnterpriseRiskPoint(date = "2026-04-23", score = 80.0, level = "中风险"),
        EnterpriseRiskPoint(date = "2026-04-24", score = 85.0, level = "低风险")
    )

    val mockDailyReport: ReportResponse = ReportResponse(
        reportDate = "2026-06-19",
        leaderName = "张三",
        safetyPromise = SafetyPromiseReport(
            totalEnterprises = 50,
            completedCount = 49,
            uncommittedCompanies = listOf("企业A", "企业B"),
            promiseRate = 98.0,
            specialWorkCompanies = listOf("广东宝时有限公司"),
            deviceStartStopCompanies = emptyList(),
            maintenanceCompanies = emptyList()
        ),
        closedManagement = ClosedManagementReport(
            totalVehicles = 1240,
            hazmatVehicles = 320,
            totalTonnage = 4500.5,
            topChemicals = listOf(
                TopChemicalReport(1, "汽油", 1500.0),
                TopChemicalReport(2, "柴油", 1200.0),
                TopChemicalReport(3, "甲醇", 800.0),
                TopChemicalReport(4, "液氨", 600.0),
                TopChemicalReport(5, "粗苯", 400.0)
            ),
            randomCheck = VehicleRandomCheckReport(
                consistent = true,
                speedingCount = 0,
                illegalParkingCount = 0,
                trackTraceable = true
            )
        ),
        specialOperations = SpecialOperationsReport(
            onlinePromises = 15,
            actualOperations = 12,
            superHotWorkCount = 2,
            level1HotWorkCount = 5,
            confinedSpaceCount = 5,
            operationList = listOf(
                SpecialOperationItem(
                    enterpriseName = "广东宝时有限公司",
                    superHotCount = 0,
                    level1HotCount = 1,
                    confinedSpaceCount = 0,
                    details = "2026年6月19日，在1号罐区处开展一级动火作业(作业票: HW-20260619-001)；(已线下抽查)"
                )
            )
        )
    )
}
