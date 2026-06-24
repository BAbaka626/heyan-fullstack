package work.xy0712.xz.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import work.xy0712.xz.data.mock.MockData
import work.xy0712.xz.data.model.EnterpriseDetailResponse
import work.xy0712.xz.data.model.EnterpriseRiskProfile
import work.xy0712.xz.data.model.EnterpriseSummary
import work.xy0712.xz.data.model.InspectionRecord
import work.xy0712.xz.data.model.InspectionRecordCreateRequest
import work.xy0712.xz.data.model.InspectionRoute
import work.xy0712.xz.data.model.IssueCreateRequest
import work.xy0712.xz.data.model.IssueRecord
import work.xy0712.xz.data.model.ReportResponse
import work.xy0712.xz.data.model.RiskLevelStat
import work.xy0712.xz.data.model.WorkRecord
import work.xy0712.xz.data.model.WorkRecordCreateRequest
import work.xy0712.xz.data.repository.ChemicalRepository
import work.xy0712.xz.ui.enterprise.EnterpriseRiskLevel
import work.xy0712.xz.ui.enterprise.countEnterpriseRiskLevels
import work.xy0712.xz.ui.enterprise.getEnterpriseRiskLevel

class EnterpriseViewModel : ViewModel() {

    private val repository = ChemicalRepository(work.xy0712.xz.data.api.ApiConfig.BASE_URL)

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Enterprises
    private val _enterprises = MutableStateFlow<List<EnterpriseSummary>>(emptyList())
    val enterprises: StateFlow<List<EnterpriseSummary>> = _enterprises.asStateFlow()
    private var loadedEnterprises: List<EnterpriseSummary> = emptyList()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Risk level filter
    private val _selectedRiskLevel = MutableStateFlow<String?>(null)
    val selectedRiskLevel: StateFlow<String?> = _selectedRiskLevel.asStateFlow()

    // Risk stats
    private val _riskLevelStats = MutableStateFlow<List<RiskLevelStat>>(emptyList())
    val riskLevelStats: StateFlow<List<RiskLevelStat>> = _riskLevelStats.asStateFlow()

    private val _dailyReport = MutableStateFlow(MockData.mockDailyReport)
    val dailyReport: StateFlow<ReportResponse> = _dailyReport.asStateFlow()

    // Selected enterprise
    private val _selectedEnterprise = MutableStateFlow<EnterpriseDetailResponse?>(null)
    val selectedEnterprise: StateFlow<EnterpriseDetailResponse?> = _selectedEnterprise.asStateFlow()

    private val _selectedRiskProfile = MutableStateFlow<EnterpriseRiskProfile?>(null)
    val selectedRiskProfile: StateFlow<EnterpriseRiskProfile?> = _selectedRiskProfile.asStateFlow()

    // Inspection data
    private val _inspectionRoutes = MutableStateFlow<List<InspectionRoute>>(emptyList())
    val inspectionRoutes: StateFlow<List<InspectionRoute>> = _inspectionRoutes.asStateFlow()

    private val _inspectionRecords = MutableStateFlow<List<InspectionRecord>>(emptyList())
    val inspectionRecords: StateFlow<List<InspectionRecord>> = _inspectionRecords.asStateFlow()

    // Issues & Work records
    private val _issues = MutableStateFlow<List<IssueRecord>>(emptyList())
    val issues: StateFlow<List<IssueRecord>> = _issues.asStateFlow()

    private val _workRecords = MutableStateFlow<List<WorkRecord>>(emptyList())
    val workRecords: StateFlow<List<WorkRecord>> = _workRecords.asStateFlow()

    init {
        loadEnterprises()
        loadDailyReport()
        loadInspectionData()
        loadIssues()
        loadWorkRecords()
    }

    fun loadDailyReport(date: String = "") {
        viewModelScope.launch {
            try {
                _dailyReport.value = if (MockData.USE_MOCK) {
                    MockData.mockDailyReport
                } else {
                    repository.getDailyReport(date)
                }
            } catch (e: Exception) {
                android.util.Log.e("EnterpriseViewModel", "loadDailyReport failed", e)
                _dailyReport.value = MockData.mockDailyReport
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _selectedRiskLevel.value = null
        loadEnterprises()
    }

    fun onRiskLevelSelected(levelCode: String?) {
        _selectedRiskLevel.value = levelCode
        applyRiskFilter()
    }

    fun loadEnterprises() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = repository.getEnterprises(_searchQuery.value)
                loadedEnterprises = response.items
                updateRiskStats(loadedEnterprises)
                applyRiskFilter()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "加载企业列表失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun applyRiskFilter() {
        val selected = _selectedRiskLevel.value
        _enterprises.value = if (selected == null) {
            loadedEnterprises
        } else {
            loadedEnterprises.filter { getEnterpriseRiskLevel(it).name == selected }
        }
    }

    private fun updateRiskStats(source: List<EnterpriseSummary>) {
        val counts = countEnterpriseRiskLevels(source)
        _riskLevelStats.value = listOf(
            RiskLevelStat(EnterpriseRiskLevel.EXTREME.name, EnterpriseRiskLevel.EXTREME.label, EnterpriseRiskLevel.EXTREME.colorHex, counts.extreme),
            RiskLevelStat(EnterpriseRiskLevel.HIGH.name, EnterpriseRiskLevel.HIGH.label, EnterpriseRiskLevel.HIGH.colorHex, counts.high),
            RiskLevelStat(EnterpriseRiskLevel.MEDIUM.name, EnterpriseRiskLevel.MEDIUM.label, EnterpriseRiskLevel.MEDIUM.colorHex, counts.medium),
            RiskLevelStat(EnterpriseRiskLevel.LOW.name, EnterpriseRiskLevel.LOW.label, EnterpriseRiskLevel.LOW.colorHex, counts.low),
            RiskLevelStat(EnterpriseRiskLevel.UNKNOWN.name, EnterpriseRiskLevel.UNKNOWN.label, EnterpriseRiskLevel.UNKNOWN.colorHex, counts.unknown)
        )
    }

    fun selectEnterprise(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val detail = repository.getEnterpriseDetail(id)
                val profile = repository.getEnterpriseRiskProfile(id)
                _selectedEnterprise.value = detail
                _selectedRiskProfile.value = profile
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "加载企业详情失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSelectedEnterprise() {
        _selectedEnterprise.value = null
        _selectedRiskProfile.value = null
    }

    private fun loadInspectionData() {
        viewModelScope.launch {
            try {
                _inspectionRoutes.value = repository.getInspectionRoutes().items
                _inspectionRecords.value = repository.getInspectionRecords().items
            } catch (_: Exception) { }
        }
    }

    private fun loadIssues() {
        viewModelScope.launch {
            try {
                _issues.value = repository.getIssues().items
            } catch (_: Exception) { }
        }
    }

    private fun loadWorkRecords() {
        viewModelScope.launch {
            try {
                _workRecords.value = repository.getWorkRecords().items
            } catch (_: Exception) { }
        }
    }

    fun submitIssue(request: IssueCreateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.createIssue(request)
                loadIssues()
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "提交问题单失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun submitWorkRecord(request: WorkRecordCreateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.createWorkRecord(request)
                loadWorkRecords()
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "提交工作记录失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun submitInspectionRecord(request: InspectionRecordCreateRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.createInspectionRecord(request)
                loadInspectionData()
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "提交巡检记录失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
