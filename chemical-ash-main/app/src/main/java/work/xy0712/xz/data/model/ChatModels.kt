package work.xy0712.xz.data.model

/**
 * 客户端发往后端的请求体
 */
data class AskRequest(
    val question: String,
    val history: List<AskHistoryTurn> = emptyList()
)

/**
 * 历史轮次上报数据模型
 */
data class AskHistoryTurn(
    val question: String = "",
    val answer: String = "",
    val sources: List<AskSource> = emptyList()
)

/**
 * 溯源文档卡片数据模型（增加了Excel、章节等细粒度底账溯源字段）
 */
data class AskSource(
    val title: String = "",
    val source_tag: String = "",
    val sheet_name: String = "",
    val section_title: String = "",
    val row_header: String = "",
    val column_header: String = "",
    val cell_ref: String = "",
    val chunk: Int? = null,
    val excerpt: String? = null
)

/**
 * 核心网络请求响应实体类
 * 意义：通过定义 mode, cards, charts，打通接收图表控制流的核心数据入口
 */
data class AskResponse(
    val mode: String = "",                       // 决定渲染模式："rag"(普通文本) 或 "diagram"(触发图表)
    val chartType: String = "",                  // 新图表控制字段："pie"、"scatter"
    val answer: String = "",                     // 大白话回答文本
    val sources: List<AskSource> = emptyList(),  // 溯源依据清单
    val cards: List<AskCard> = emptyList(),      // 结构化企业风险看板卡片
    val charts: List<AskChart> = emptyList(),    // 可视化图表数据阵列（包含扇形图、地图打点等）
    val warningReminder: AskWarningReminder? = null // 突发风险预警提醒
)

/**
 * 看板卡片数据模型
 */
data class AskCard(
    val type: String = "",                       // 卡片类型标记
    val title: String = "",                      // 卡片标题
    val data: Any? = null                        // 兼容多种企业风险底账配置
)

/**
 * 可视化图表数据模型（对接 ECharts）
 */
data class AskChart(
    val type: String = "",                       // 图表类型："pie_chart"(扇形图)、"risk_map"(地图)
    val chartType: String = "",                  // 新图表类型："pie"、"scatter"
    val title: String = "",                      // 图表标题，如 "重大危险源四级风险扇形图"
    val data: Any? = null                        // 接收图表所需的坐标或数组节点（对应后端的 data）
)

/**
 * 突发风险预警提示数据模型
 */
data class AskWarningReminder(
    val title: String = "",
    val level: String = "",                      // 预警级别（红、橙、黄、蓝）
    val content: String = ""
)

/**
 * 单个会话 Session 实体类（包含此会话下的所有问答轮次）
 */
data class ChatSession(
    val id: String = "",
    val title: String = "新会话",
    val turns: List<ChatTurn> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 单次对话轮次数据模型（包含新扩展的图表响应）
 */
data class ChatTurn(
    val question: String = "",
    val response: AskResponse = AskResponse(),
    val timestamp: Long = System.currentTimeMillis()
)
