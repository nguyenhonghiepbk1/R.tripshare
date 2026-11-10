package com.hiepnguyen.tripfinance.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hiepnguyen.tripfinance.data.db.AppDatabase
import com.hiepnguyen.tripfinance.data.entity.*
import com.hiepnguyen.tripfinance.data.repository.TripFinanceRepository
import com.hiepnguyen.tripfinance.domain.engine.SettlementEngine
import com.hiepnguyen.tripfinance.domain.engine.SplitCalculator
import com.hiepnguyen.tripfinance.domain.model.*
import com.hiepnguyen.tripfinance.domain.export.PdfReportExporter
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import android.content.Context
import android.net.Uri
import java.io.File

data class UiState(
    val currentTrip: TripEntity? = null,
    val allTrips: List<TripEntity> = emptyList(),
    val members: List<TripMemberEntity> = emptyList(),
    val currentMember: TripMemberEntity? = null,
    val financialSummary: FinancialSummary = FinancialSummary(),
    val memberStatuses: List<MemberFinancialStatus> = emptyList(),
    val settlementTransfers: List<SettlementTransfer> = emptyList(),
    val reconciliationError: String? = null,
    val categoryBreakdowns: List<CategoryBreakdown> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val allSplits: List<ExpenseSplitEntity> = emptyList(),
    val fundContributions: List<FundContributionEntity> = emptyList(),
    val exchangeRates: List<ExchangeRateEntity> = emptyList(),
    val auditLogs: List<AuditLogEntity> = emptyList(),
    val snapshots: List<SettlementSnapshotEntity> = emptyList(),
    val selectedCategoryFilter: String? = null,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val language: AppLanguage = AppLanguage.VI
)

sealed class DatabaseInitState {
    object Initializing : DatabaseInitState()
    object Ready : DatabaseInitState()
    data class Error(
        val errorType: com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType,
        val message: String,
        val details: String? = null
    ) : DatabaseInitState()
}

data class DeletedExpenseUndo(
    val expense: ExpenseEntity,
    val splits: List<ExpenseSplitEntity>
)

class TripFinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val _databaseInitState = MutableStateFlow<DatabaseInitState>(DatabaseInitState.Initializing)
    val databaseInitState: StateFlow<DatabaseInitState> = _databaseInitState.asStateFlow()

    private val _repositoryFlow = MutableStateFlow<TripFinanceRepository?>(null)
    private val repository: TripFinanceRepository
        get() = _repositoryFlow.value ?: error(if (_language.value == AppLanguage.VI) "Cơ sở dữ liệu chưa sẵn sàng" else "Database is not ready")

    private val sharedPrefs = application.getSharedPreferences("trip_finance_prefs", Context.MODE_PRIVATE)

    private val _currentTripId = MutableStateFlow<String?>(null)
    private val _currentMemberId = MutableStateFlow<String?>(null)
    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _deletedExpenseEvent = MutableSharedFlow<DeletedExpenseUndo>(extraBufferCapacity = 1)
    val deletedExpenseEvent: SharedFlow<DeletedExpenseUndo> = _deletedExpenseEvent.asSharedFlow()
    var lastDeletedExpense: DeletedExpenseUndo? = null
        private set
    private val _aiInsight = MutableStateFlow<String?>(null)
    private val _isLoadingAi = MutableStateFlow(false)
    val aiInsight: StateFlow<String?> = _aiInsight.asStateFlow()
    val isLoadingAi: StateFlow<Boolean> = _isLoadingAi.asStateFlow()
    private val _isExportingPdf = MutableStateFlow(false)
    val isExportingPdf: StateFlow<Boolean> = _isExportingPdf.asStateFlow()
    private val _isExportingXlsx = MutableStateFlow(false)
    val isExportingXlsx: StateFlow<Boolean> = _isExportingXlsx.asStateFlow()
    private val _language = MutableStateFlow(
        if (sharedPrefs.getString("app_language", "vi") == "en") AppLanguage.EN else AppLanguage.VI
    )

    val uiState: StateFlow<UiState>

    private data class FilterState(
        val categoryFilter: String?,
        val query: String,
        val currentMemberId: String?,
        val language: AppLanguage
    )

    private data class TripCoreData(
        val members: List<TripMemberEntity>,
        val expenses: List<ExpenseEntity>,
        val splits: List<ExpenseSplitEntity>,
        val funds: List<FundContributionEntity>,
        val rates: List<ExchangeRateEntity>
    )

    private data class TripAuxData(
        val logs: List<AuditLogEntity>,
        val snapshots: List<SettlementSnapshotEntity>,
        val financialPair: Pair<FinancialSummary, List<MemberFinancialStatus>>,
        val transfers: List<SettlementTransferEntity> = emptyList()
    )

    init {
        val filterFlow = combine(
            _selectedCategoryFilter,
            _searchQuery,
            combine(_currentMemberId, _language) { mId, lang -> mId to lang }
        ) { cat, q, (mId, lang) ->
            FilterState(cat, q, mId, lang)
        }

        uiState = _repositoryFlow.flatMapLatest { repo ->
            if (repo == null) {
                flowOf(
                    UiState(
                        errorMessage = _errorMessage.value,
                        successMessage = _successMessage.value,
                        language = _language.value
                    )
                )
            } else {
                combine(
                    repo.allTrips,
                    _currentTripId,
                    filterFlow,
                    _errorMessage,
                    _successMessage
                ) { trips, activeTripId, filters, error, success ->
                    val trip = trips.find { it.id == activeTripId } ?: trips.firstOrNull()
                    Triple(trips, trip, filters) to (error to success)
                }.flatMapLatest { (triple, messagePair) ->
                    val (trips, currentTrip, filters) = triple
                    val (errorMsg, successMsg) = messagePair

                    if (currentTrip == null) {
                        flowOf(
                            UiState(
                                allTrips = trips,
                                errorMessage = errorMsg,
                                successMessage = successMsg,
                                language = filters.language
                            )
                        )
                    } else {
                        val tripId = currentTrip.id

                        val coreFlow = combine(
                            repo.getMembers(tripId),
                            repo.getExpenses(tripId),
                            repo.getSplitsForTrip(tripId),
                            repo.getFundContributions(tripId),
                            repo.getExchangeRates(tripId)
                        ) { members, expenses, splits, funds, rates ->
                            TripCoreData(members, expenses, splits, funds, rates)
                        }

                        val auxFlow = combine(
                            repo.getAuditLogs(tripId),
                            repo.getSnapshots(tripId),
                            repo.observeFinancialStatus(tripId),
                            repo.getTransfers(tripId)
                        ) { logs, snapshots, financialPair, transfers ->
                            TripAuxData(logs, snapshots, financialPair, transfers)
                        }

                combine(coreFlow, auxFlow) { core, aux ->
                    val (summary, statuses) = aux.financialPair
                    val currentMember = core.members.find { it.id == filters.currentMemberId }
                        ?: core.members.firstOrNull()

                    val totalSpent = summary.totalExpenses.coerceAtLeast(1L)
                    val catMapVi = mapOf(
                        "FOOD" to "Ăn uống",
                        "TRANSPORT" to "Di chuyển",
                        "HOTEL" to "Lưu trú/Khách sạn",
                        "SIGHTSEEING" to "Vé tham quan",
                        "ENTERTAINMENT" to "Vui chơi/Giải trí",
                        "SHOPPING" to "Mua sắm",
                        "OTHER" to "Chi phí khác"
                    )
                    val catMapEn = mapOf(
                        "FOOD" to "Food & Dining",
                        "TRANSPORT" to "Transportation",
                        "HOTEL" to "Accommodation",
                        "SIGHTSEEING" to "Sightseeing & Tours",
                        "ENTERTAINMENT" to "Entertainment",
                        "SHOPPING" to "Shopping",
                        "OTHER" to "Other Expenses"
                    )

                    val breakdowns = core.expenses.groupBy { it.category }.map { (cat, list) ->
                        val amount = list.sumOfSafe { it.convertedTotalAmount }
                        val label = if (filters.language == AppLanguage.VI) {
                            catMapVi[cat] ?: cat
                        } else {
                            catMapEn[cat] ?: cat
                        }
                        CategoryBreakdown(
                            category = cat,
                            labelVi = label,
                            iconName = cat,
                            totalAmount = amount,
                            percentage = (amount.toDouble() / totalSpent.toDouble()) * 100.0,
                            count = list.size
                        )
                    }.sortedByDescending { it.totalAmount }

                    val fundHolder = core.members.find { it.role == "TREASURER" && it.isActive }
                        ?: core.members.find { it.role == "ADMIN" && it.isActive }
                        ?: core.members.firstOrNull()

                    val settlementResult = SettlementEngine.computeSettlementWithStatus(
                        memberStatuses = statuses,
                        tripJoinCode = currentTrip.joinCode,
                        remainingFund = summary.remainingFund,
                        fundHolder = fundHolder
                    )

                    val savedTransfersMap = aux.transfers.associateBy { it.id }
                    val finalTransfers = settlementResult.transfers.map { tr ->
                        val saved = savedTransfersMap[tr.id]
                        if (saved != null) {
                            tr.copy(
                                isCompleted = saved.isCompleted,
                                completedAmount = saved.completedAmount
                            )
                        } else {
                            tr
                        }
                    }

                    val filteredExpenses = core.expenses.filter { exp ->
                        val matchesCat = filters.categoryFilter == null || exp.category == filters.categoryFilter
                        val matchesSearch = filters.query.isBlank() ||
                                exp.title.contains(filters.query, ignoreCase = true) ||
                                exp.note.contains(filters.query, ignoreCase = true)
                        matchesCat && matchesSearch
                    }

                    UiState(
                        currentTrip = currentTrip,
                        allTrips = trips,
                        members = core.members,
                        currentMember = currentMember,
                        financialSummary = summary,
                        memberStatuses = statuses,
                        settlementTransfers = finalTransfers,
                        reconciliationError = settlementResult.reconciliationError,
                        categoryBreakdowns = breakdowns,
                        expenses = filteredExpenses,
                        allSplits = core.splits,
                        fundContributions = core.funds,
                        exchangeRates = core.rates,
                        auditLogs = aux.logs,
                        snapshots = aux.snapshots,
                        selectedCategoryFilter = filters.categoryFilter,
                        searchQuery = filters.query,
                        errorMessage = errorMsg,
                        successMessage = successMsg,
                        language = filters.language
                    )
                }.flowOn(Dispatchers.Default)
            }
        }
    }
}.flowOn(Dispatchers.Default)
.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = UiState()
)

        // Dọn dẹp các tệp PDF báo cáo cũ hơn 24 giờ trong cache/reports khi khởi động
        viewModelScope.launch(Dispatchers.IO) {
            PdfReportExporter.cleanOldReports(application)
        }

        initDatabase()
    }

    /**
     * Xuất và chia sẻ báo cáo tài chính PDF chạy trên viewModelScope và Dispatchers.IO,
     * đồng thời quản lý trạng thái loading để giao diện hiển thị biểu tượng tiến trình.
     */
    fun exportAndSharePdf(context: Context) {
        val state = uiState.value
        val trip = state.currentTrip ?: return
        val creator = state.currentMember?.name ?: if (state.language == AppLanguage.VI) "Thành viên" else "Member"

        viewModelScope.launch {
            _isExportingPdf.value = true
            try {
                withContext(Dispatchers.IO) {
                    PdfReportExporter.exportAndSharePdf(
                        context = context,
                        trip = trip,
                        members = state.members,
                        summary = state.financialSummary,
                        statuses = state.memberStatuses,
                        settlementTransfers = state.settlementTransfers,
                        expenses = state.expenses,
                        funds = state.fundContributions,
                        splits = state.allSplits,
                        creatorName = creator,
                        language = state.language
                    )
                }
            } finally {
                _isExportingPdf.value = false
            }
        }
    }

    /**
     * Xuất và chia sẻ báo cáo tài chính Excel (.xlsx) chuẩn OpenXML 5 sheet chạy trên viewModelScope
     * và Dispatchers.IO, đồng thời quản lý trạng thái loading để giao diện hiển thị biểu tượng tiến trình.
     */
    fun exportAndShareXlsx(context: Context) {
        val state = uiState.value
        val trip = state.currentTrip ?: return

        viewModelScope.launch {
            _isExportingXlsx.value = true
            try {
                withContext(Dispatchers.IO) {
                    PdfReportExporter.exportAndShareXlsx(
                        context = context,
                        trip = trip,
                        members = state.members,
                        summary = state.financialSummary,
                        statuses = state.memberStatuses,
                        settlementTransfers = state.settlementTransfers,
                        expenses = state.expenses,
                        funds = state.fundContributions,
                        splits = state.allSplits,
                        language = state.language
                    )
                }
            } finally {
                _isExportingXlsx.value = false
            }
        }
    }

    fun retryDatabaseInit() {
        initDatabase()
    }

    private fun initDatabase() {
        _databaseInitState.value = DatabaseInitState.Initializing
        try {
            val db = AppDatabase.getDatabase(getApplication())
            val repo = TripFinanceRepository(db)
            _repositoryFlow.value = repo
            _databaseInitState.value = DatabaseInitState.Ready
        } catch (e: com.hiepnguyen.tripfinance.data.security.DatabaseSecurityException) {
            _repositoryFlow.value = null
            _databaseInitState.value = DatabaseInitState.Error(
                errorType = e.errorType,
                message = e.message ?: if (_language.value == AppLanguage.VI) "Lỗi bảo mật khi mở cơ sở dữ liệu" else "Security error when opening database",
                details = e.cause?.message
            )
        } catch (e: Throwable) {
            _repositoryFlow.value = null
            _databaseInitState.value = DatabaseInitState.Error(
                errorType = com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.UNKNOWN,
                message = if (_language.value == AppLanguage.VI) "Không thể khởi tạo cơ sở dữ liệu: ${e.message}" else "Cannot initialize database: ${e.message}",
                details = e.message
            )
        }
    }

    fun resetDatabaseFresh() {
        viewModelScope.launch {
            try {
                AppDatabase.resetDatabase(getApplication())
                initDatabase()
                showSuccess(AppStrings.vmDbResetSuccess(_language.value))
            } catch (e: Throwable) {
                showError(AppStrings.vmDbResetError(e.message, _language.value))
            }
        }
    }

    fun restoreFromEmergencyBackup(jsonContent: String, password: String?) {
        viewModelScope.launch {
            try {
                val parseResult = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.parseAndValidateBackup(jsonContent, password)
                if (parseResult.isFailure) {
                    val err = parseResult.exceptionOrNull()?.message ?: AppStrings.vmInvalidBackupOrPassword(_language.value)
                    showError(AppStrings.vmRestoreFailed(err, _language.value))
                    return@launch
                }
                val backupData = parseResult.getOrThrow()

                // Đặt lại CSDL và KeyStore sạch sẽ
                AppDatabase.resetDatabase(getApplication())

                // Mở CSDL mới và nạp dữ liệu
                val newDb = AppDatabase.getDatabase(getApplication())
                val repo = TripFinanceRepository(newDb)
                _repositoryFlow.value = repo

                val restoreResult = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.restoreFromBackupData(
                    db = newDb,
                    backupData = backupData,
                    clearExisting = false
                )

                if (restoreResult.isSuccess) {
                    _databaseInitState.value = DatabaseInitState.Ready
                    showSuccess(AppStrings.vmRestoreEmergencySuccess(backupData.trips.size, _language.value))
                } else {
                    val err = restoreResult.exceptionOrNull()?.message ?: AppStrings.vmDataLoadError(_language.value)
                    showError(AppStrings.vmDataLoadErrorDetail(err, _language.value))
                }
            } catch (e: Throwable) {
                showError(AppStrings.vmEmergencyRestoreError(e.message, _language.value))
            }
        }
    }

    fun setLanguage(lang: AppLanguage) {
        _language.value = lang
        sharedPrefs.edit().putString("app_language", lang.code).apply()
    }

    fun toggleLanguage() {
        val next = if (_language.value == AppLanguage.VI) AppLanguage.EN else AppLanguage.VI
        setLanguage(next)
    }

    fun selectTrip(tripId: String) {
        _currentTripId.value = tripId
    }

    fun switchUserPersona(memberId: String) {
        _currentMemberId.value = memberId
    }

    fun setCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun showError(msg: String) {
        _errorMessage.value = msg
    }

    fun showSuccess(msg: String) {
        _successMessage.value = msg
    }

    fun requestSpendingInsight() {
        val state = uiState.value
        val currentTrip = state.currentTrip ?: return
        viewModelScope.launch {
            _isLoadingAi.value = true
            try {
                val insight = com.hiepnguyen.tripfinance.domain.ai.LocalSpendingAdvisor.generateLocalInsight(
                    tripTitle = currentTrip.title,
                    summary = state.financialSummary,
                    categories = state.categoryBreakdowns,
                    members = state.memberStatuses,
                    lang = state.language.code
                )
                _aiInsight.value = insight
            } catch (e: Exception) {
                _aiInsight.value = AppStrings.vmAiAnalysisError(e.message, _language.value)
            } finally {
                _isLoadingAi.value = false
            }
        }
    }

    fun requestAiSpendingInsight() = requestSpendingInsight()

    fun clearAiInsight() {
        _aiInsight.value = null
    }

    // Trip operations
    fun createTrip(
        title: String,
        description: String,
        joinCode: String,
        startDate: Long,
        endDate: Long,
        adminName: String,
        baseCurrency: String = "VND"
    ) {
        viewModelScope.launch {
            try {
                val newTripId = repository.createTrip(
                    title = title,
                    description = description,
                    joinCode = joinCode,
                    startDate = startDate,
                    endDate = endDate,
                    adminName = adminName,
                    baseCurrency = baseCurrency
                )
                _currentTripId.value = newTripId
                showSuccess(AppStrings.vmCreateTripSuccess(title, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmCreateTripError(e.message, _language.value))
            }
        }
    }

    fun editTrip(
        tripId: String,
        title: String,
        description: String,
        startDate: Long,
        endDate: Long,
        baseCurrency: String? = null
    ) {
        val currentMember = uiState.value.currentMember
        if (currentMember != null && currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyEditTrip(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                // If currentMember is null, find the admin of the target trip
                val members = repository.getMembers(tripId).first()
                val adminActor = members.find { it.role == "ADMIN" } ?: currentMember ?: members.firstOrNull()
                if (adminActor == null) {
                    showError(AppStrings.vmAdminMemberNotFound(_language.value))
                    return@launch
                }

                repository.updateTripDetails(
                    tripId = tripId,
                    title = title,
                    description = description,
                    startDate = startDate,
                    endDate = endDate,
                    baseCurrency = baseCurrency,
                    actor = adminActor
                )
                showSuccess(AppStrings.vmUpdateTripSuccess(title, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmUpdateTripError(e.message, _language.value))
            }
        }
    }

    fun deleteTrip(tripId: String) {
        val currentMember = uiState.value.currentMember
        if (currentMember != null && currentMember.role != "ADMIN" && _currentTripId.value == tripId) {
            showError(AppStrings.vmAdminOnlyDeleteTrip(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                val trips = uiState.value.allTrips
                val targetTrip = trips.find { it.id == tripId }
                repository.deleteTripCascade(tripId)
                // If deleting current active trip, switch to another
                if (_currentTripId.value == tripId) {
                    val remaining = trips.filter { it.id != tripId }
                    _currentTripId.value = remaining.firstOrNull()?.id
                }
                showSuccess(AppStrings.vmDeleteTripSuccess(targetTrip?.title ?: tripId, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmDeleteTripError(e.message, _language.value))
            }
        }
    }

    // Expense operations
    fun addExpense(
        title: String,
        category: String,
        payerType: String,
        payerMemberId: String?,
        totalAmount: Double,
        currency: String,
        exchangeRate: Double,
        splitType: String,
        splits: List<Pair<String, Long>>,
        note: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotAddExpense(currentTrip.title, _language.value))
            return
        }

        if (!totalAmount.isFinite() || totalAmount.isNaN() || totalAmount <= 0.0 || totalAmount > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
            showError(AppStrings.vmInvalidExpenseAmount(_language.value))
            return
        }
        if (!exchangeRate.isFinite() || exchangeRate.isNaN() || exchangeRate <= 0.0 || exchangeRate > FinancialLimits.MAX_EXCHANGE_RATE) {
            showError(AppStrings.vmInvalidExchangeRate(_language.value))
            return
        }

        val convertedTotal = FinancialInputValidator.convertToVnd(totalAmount, exchangeRate)
        val totalSplitSum = splits.sumOfSafe { it.second }
        if (totalSplitSum != convertedTotal) {
            showError(AppStrings.vmSplitSumMismatch(totalSplitSum, convertedTotal, _language.value))
            return
        }

        if (splits.any { it.second < 0 }) {
            showError(AppStrings.vmNegativeSplitAmount(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                val expenseId = UUID.randomUUID().toString()
                val expense = ExpenseEntity(
                    id = expenseId,
                    tripId = currentTrip.id,
                    title = title,
                    category = category,
                    payerType = payerType,
                    payerMemberId = if (payerType == "MEMBER") payerMemberId else null,
                    totalAmount = totalAmount,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    convertedTotalAmount = convertedTotal,
                    splitType = splitType,
                    note = note,
                    timestamp = timestamp,
                    createdMemberId = currentMember.id,
                    isSynced = true
                )

                val splitEntities = splits.map { (memberId, amount) ->
                    ExpenseSplitEntity(
                        id = UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        tripId = currentTrip.id,
                        memberId = memberId,
                        amount = amount
                    )
                }

                repository.addExpenseWithSplits(expense, splitEntities, currentMember)
                showSuccess(AppStrings.vmAddExpenseSuccess(title, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmAddExpenseError(e.message, _language.value))
            }
        }
    }

    fun editExpense(
        expenseId: String,
        title: String,
        category: String,
        payerType: String,
        payerMemberId: String?,
        totalAmount: Double,
        currency: String,
        exchangeRate: Double,
        splitType: String,
        splits: List<Pair<String, Long>>,
        note: String,
        timestamp: Long
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotEditExpense(currentTrip.title, _language.value))
            return
        }

        // Strict RBAC: Only Admin can edit expenses
        if (currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyEditExpense(_language.value))
            return
        }

        if (!totalAmount.isFinite() || totalAmount.isNaN() || totalAmount <= 0.0 || totalAmount > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
            showError(AppStrings.vmInvalidExpenseAmount(_language.value))
            return
        }
        if (!exchangeRate.isFinite() || exchangeRate.isNaN() || exchangeRate <= 0.0 || exchangeRate > FinancialLimits.MAX_EXCHANGE_RATE) {
            showError(AppStrings.vmInvalidExchangeRate(_language.value))
            return
        }

        val convertedTotal = FinancialInputValidator.convertToVnd(totalAmount, exchangeRate)
        val totalSplitSum = splits.sumOfSafe { it.second }
        if (totalSplitSum != convertedTotal) {
            showError(AppStrings.vmSplitSumMismatch(totalSplitSum, convertedTotal, _language.value))
            return
        }

        if (splits.any { it.second < 0 }) {
            showError(AppStrings.vmNegativeSplitAmount(_language.value))
            return
        }

        // CRITICAL FIX: Bảo lưu người tạo ban đầu (createdMemberId), không ghi đè bằng id của người sửa
        val existingExpense = uiState.value.expenses.find { it.id == expenseId }
        val preservedCreatedMemberId = existingExpense?.createdMemberId ?: currentMember.id

        viewModelScope.launch {
            try {
                val updatedExpense = ExpenseEntity(
                    id = expenseId,
                    tripId = currentTrip.id,
                    title = title,
                    category = category,
                    payerType = payerType,
                    payerMemberId = if (payerType == "MEMBER") payerMemberId else null,
                    totalAmount = totalAmount,
                    currency = currency,
                    exchangeRate = exchangeRate,
                    convertedTotalAmount = convertedTotal,
                    splitType = splitType,
                    note = note,
                    timestamp = timestamp,
                    createdMemberId = preservedCreatedMemberId,
                    isSynced = true
                )

                val splitEntities = splits.map { (memberId, amount) ->
                    ExpenseSplitEntity(
                        id = UUID.randomUUID().toString(),
                        expenseId = expenseId,
                        tripId = currentTrip.id,
                        memberId = memberId,
                        amount = amount
                    )
                }

                repository.updateExpenseWithSplits(updatedExpense, splitEntities, currentMember)
                showSuccess(AppStrings.vmUpdateExpenseSuccess(title, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmUpdateExpenseError(e.message, _language.value))
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotDeleteExpense(currentTrip.title, _language.value))
            return
        }

        // Strict RBAC: Only Admin can delete expenses
        if (currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyDeleteExpense(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                val splits = repository.deleteExpense(expense, currentMember)
                val undoItem = DeletedExpenseUndo(expense, splits)
                lastDeletedExpense = undoItem
                _deletedExpenseEvent.emit(undoItem)
            } catch (e: Exception) {
                showError(AppStrings.vmDeleteExpenseError(e.message, _language.value))
            }
        }
    }

    fun undoDeleteExpense(deletedUndo: DeletedExpenseUndo) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotRestoreExpense(currentTrip.title, _language.value))
            return
        }

        viewModelScope.launch {
            try {
                repository.restoreExpense(deletedUndo.expense, deletedUndo.splits, currentMember)
                lastDeletedExpense = null
                showSuccess(AppStrings.vmRestoreExpenseSuccess(deletedUndo.expense.title, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmRestoreExpenseError(e.message, _language.value))
            }
        }
    }

    fun undoDeleteLastExpense() {
        val item = lastDeletedExpense ?: return
        undoDeleteExpense(item)
    }

    // Fund operations
    fun addFundContribution(
        memberId: String,
        amount: Long,
        currency: String,
        exchangeRate: Double,
        note: String
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotContributeFund(currentTrip.title, _language.value))
            return
        }

        val members = uiState.value.members
        val contributor = members.find { it.id == memberId }

        if (currentMember.role != "ADMIN" && currentMember.role != "TREASURER") {
            showError(AppStrings.vmAdminOrTreasurerOnlyFund(_language.value))
            return
        }

        if (amount <= 0L || amount > FinancialLimits.MAX_TRANSACTION_AMOUNT) {
            showError(AppStrings.vmInvalidFundAmount(_language.value))
            return
        }
        if (!exchangeRate.isFinite() || exchangeRate.isNaN() || exchangeRate <= 0.0 || exchangeRate > FinancialLimits.MAX_EXCHANGE_RATE) {
            showError(AppStrings.vmInvalidExchangeRate(_language.value))
            return
        }

        val convertedAmount = FinancialInputValidator.convertToVnd(amount.toDouble(), exchangeRate)
        val contribution = FundContributionEntity(
            id = UUID.randomUUID().toString(),
            tripId = currentTrip.id,
            memberId = memberId,
            amount = amount,
            currency = currency,
            exchangeRate = exchangeRate,
            convertedAmount = convertedAmount,
            note = note,
            timestamp = System.currentTimeMillis(),
            recordedByMemberId = currentMember.id
        )

        viewModelScope.launch {
            try {
                val defaultMemberName = if (_language.value == AppLanguage.VI) "Thành viên" else "Member"
                repository.addFundContribution(contribution, contributor?.name ?: defaultMemberName, currentMember)
                showSuccess(AppStrings.vmFundContributionSuccess(convertedAmount, contributor?.name ?: "", _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmFundContributionError(e.message, _language.value))
            }
        }
    }

    fun deleteFundContribution(contribution: FundContributionEntity) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotDeleteFund(currentTrip.title, _language.value))
            return
        }

        if (currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyDeleteFund(_language.value))
            return
        }

        val defaultMemberName = if (_language.value == AppLanguage.VI) "Thành viên" else "Member"
        val contributorName = uiState.value.members.find { it.id == contribution.memberId }?.name ?: defaultMemberName
        viewModelScope.launch {
            try {
                repository.deleteFundContribution(contribution, contributorName, currentMember)
                showSuccess(AppStrings.vmDeleteFundSuccess(contributorName, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmDeleteFundError(e.message, _language.value))
            }
        }
    }

    // Members & Roles
    fun addMember(
        name: String,
        role: String
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotAddMember(currentTrip.title, _language.value))
            return
        }

        if (currentMember.role != "ADMIN" && currentMember.role != "TREASURER") {
            showError(AppStrings.vmAdminOrTreasurerOnlyAddMember(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                repository.addMember(
                    tripId = currentTrip.id,
                    name = name,
                    role = role,
                    actor = currentMember
                )
                showSuccess(AppStrings.vmAddMemberSuccess(name, role, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmAddMemberError(e.message, _language.value))
            }
        }
    }

    fun editMember(
        member: TripMemberEntity,
        newName: String,
        newRole: String
    ) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotEditMember(currentTrip.title, _language.value))
            return
        }

        if (currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyEditMember(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                val updated = member.copy(
                    name = newName,
                    role = newRole
                )
                repository.updateMember(updated, currentMember)
                showSuccess(AppStrings.vmUpdateMemberSuccess(newName, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmUpdateMemberError(e.message, _language.value))
            }
        }
    }

    fun updateMemberRole(member: TripMemberEntity, newRole: String) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotChangeRole(currentTrip.title, _language.value))
            return
        }

        if (currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyChangeRole(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                repository.updateMember(member.copy(role = newRole), currentMember)
                showSuccess(AppStrings.vmChangeRoleSuccess(member.name, newRole, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmChangeRoleError(e.message, _language.value))
            }
        }
    }

    fun removeOrDeactivateMember(member: TripMemberEntity) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotDeleteMember(currentTrip.title, _language.value))
            return
        }

        if (currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyDeleteMember(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                repository.removeOrDeactivateMember(member, currentMember)
                showSuccess(AppStrings.vmProcessMemberStatusSuccess(member.name, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmProcessMemberStatusError(e.message, _language.value))
            }
        }
    }

    // Exchange rates
    fun updateExchangeRate(currencyCode: String, rateToBase: Double) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripLockedCannotUpdateRate(currentTrip.title, _language.value))
            return
        }

        if (currentMember.role != "ADMIN" && currentMember.role != "TREASURER") {
            showError(AppStrings.vmAdminOrTreasurerOnlyRate(_language.value))
            return
        }

        val rateEntity = ExchangeRateEntity(
            id = UUID.randomUUID().toString(),
            tripId = currentTrip.id,
            currencyCode = currencyCode,
            rateToBase = rateToBase,
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            try {
                repository.updateExchangeRate(rateEntity, currentMember)
                showSuccess(AppStrings.vmUpdateRateSuccess(currencyCode, rateToBase, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmUpdateRateError(e.message, _language.value))
            }
        }
    }

    // Settlement & Snapshot Finalization
    fun finalizeSettlement(snapshotTitle: String) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return
        val state = uiState.value

        if (currentTrip.isSettled) {
            showError(AppStrings.vmTripAlreadySettled(_language.value))
            return
        }

        if (currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyLockSettlement(_language.value))
            return
        }

        if (state.members.isEmpty()) {
            showError(AppStrings.vmNoMembersCannotSettle(_language.value))
            return
        }

        if (!state.financialSummary.isBalanced || state.financialSummary.balanceDiscrepancy != 0L) {
            showError(AppStrings.vmDiscrepancyCannotSettle(state.financialSummary.balanceDiscrepancy, _language.value))
            return
        }

        val settlementJsonObj = org.json.JSONObject().apply {
            put("tripId", currentTrip.id)
            put("tripTitle", currentTrip.title)
            put("joinCode", currentTrip.joinCode)
            put("settledAt", System.currentTimeMillis())
            put("settledByMemberId", currentMember.id)
            put("settledByMemberName", currentMember.name)
            put("summary", org.json.JSONObject().apply {
                put("totalExpenses", state.financialSummary.totalExpenses)
                put("personalPaidExpenses", state.financialSummary.personalPaidExpenses)
                put("fundPaidExpenses", state.financialSummary.fundPaidExpenses)
                put("totalFundCollected", state.financialSummary.totalFundCollected)
                put("remainingFund", state.financialSummary.remainingFund)
                put("balanceDiscrepancy", state.financialSummary.balanceDiscrepancy)
                put("isBalanced", state.financialSummary.isBalanced)
            })
            put("members", org.json.JSONArray().apply {
                state.memberStatuses.forEach { st ->
                    put(org.json.JSONObject().apply {
                        put("memberId", st.member.id)
                        put("name", st.member.name)
                        put("role", st.member.role)
                        put("totalPaid", st.totalPaid)
                        put("outOfPocketPaid", st.outOfPocketPaid)
                        put("fundContributed", st.fundContributed)
                        put("totalOwed", st.totalOwed)
                        put("balance", st.balance)
                        put("status", st.status.name)
                    })
                }
            })
            put("transfers", org.json.JSONArray().apply {
                state.settlementTransfers.forEach { tr ->
                    put(org.json.JSONObject().apply {
                        put("id", tr.id)
                        put("fromMemberId", tr.fromMember.id)
                        put("fromMemberName", tr.fromMember.name)
                        put("toMemberId", tr.toMember.id)
                        put("toMemberName", tr.toMember.name)
                        put("amount", tr.amount)
                        put("transferNote", tr.transferNote)
                        put("isCompleted", tr.isCompleted)
                    })
                }
            })
        }
        val settlementJson = settlementJsonObj.toString(2)

        viewModelScope.launch {
            try {
                repository.finalizeSettlement(
                    trip = currentTrip,
                    snapshotTitle = snapshotTitle.ifBlank { AppStrings.vmDefaultSnapshotTitle(System.currentTimeMillis(), _language.value) },
                    summary = state.financialSummary,
                    settlementJson = settlementJson,
                    actor = currentMember
                )
                showSuccess(AppStrings.vmLockSettlementSuccess(_language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmLockSettlementError(e.message, _language.value))
            }
        }
    }

    fun toggleTransferCompleted(transfer: SettlementTransfer) {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (!currentTrip.isSettled) {
            showError("Chỉ có thể đánh dấu đã chuyển khoản khi chuyến đi đã khóa sổ!")
            return
        }

        val newStatus = !transfer.isEffectivelyCompleted
        val completedAmount = if (newStatus) transfer.amount else null

        viewModelScope.launch {
            try {
                repository.setTransferCompleted(
                    tripId = currentTrip.id,
                    transfer = transfer,
                    isCompleted = newStatus,
                    actor = currentMember,
                    completedAmount = completedAmount
                )
            } catch (e: Exception) {
                showError(AppStrings.vmUpdateTransferError(e.message, _language.value))
            }
        }
    }

    fun reopenSettlement() {
        val currentTrip = uiState.value.currentTrip ?: return
        val currentMember = uiState.value.currentMember ?: return

        if (!currentTrip.isSettled) {
            showError(AppStrings.vmTripNotSettledYet(_language.value))
            return
        }

        if (currentMember.role != "ADMIN") {
            showError(AppStrings.vmAdminOnlyReopenTrip(_language.value))
            return
        }

        viewModelScope.launch {
            try {
                repository.reopenSettlement(currentTrip, currentMember)
                showSuccess(AppStrings.vmReopenTripSuccess(currentTrip.title, _language.value))
            } catch (e: Exception) {
                showError(AppStrings.vmReopenTripError(e.message, _language.value))
            }
        }
    }

    // ==========================================
    // BACKUP & RECOVERY OPERATIONS
    // ==========================================

    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    val localBackups: StateFlow<List<File>> = _localBackups.asStateFlow()

    fun refreshLocalBackups(context: Context) {
        viewModelScope.launch {
            _localBackups.value = repository.listLocalBackups(context)
        }
    }

    fun createLocalBackup(context: Context, password: String? = null, onCompleted: ((File) -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val file = repository.createLocalBackup(context, password)
                refreshLocalBackups(context)
                val msg = if (!password.isNullOrBlank()) {
                    AppStrings.vmBackupCreatedEncrypted(file.name, _language.value)
                } else {
                    AppStrings.vmBackupCreatedPlain(file.name, _language.value)
                }
                showSuccess(msg)
                onCompleted?.invoke(file)
            } catch (e: Exception) {
                showError(AppStrings.vmCreateBackupError(e.message, _language.value))
            }
        }
    }

    fun exportBackupToUri(context: Context, uri: Uri, password: String? = null) {
        viewModelScope.launch {
            try {
                val result = repository.exportBackupToUri(context, uri, password)
                if (result.isSuccess) {
                    val msg = if (!password.isNullOrBlank()) {
                        AppStrings.vmExportBackupEncryptedSuccess(_language.value)
                    } else {
                        AppStrings.vmExportBackupPlainSuccess(_language.value)
                    }
                    showSuccess(msg)
                } else {
                    showError(AppStrings.vmExportFileError(result.exceptionOrNull()?.message, _language.value))
                }
            } catch (e: Exception) {
                showError(AppStrings.vmExportBackupError(e.message, _language.value))
            }
        }
    }

    fun restoreBackupFromUri(context: Context, uri: Uri, clearExisting: Boolean = false, password: String? = null) {
        viewModelScope.launch {
            try {
                val result = repository.restoreBackupFromUri(context, uri, clearExisting, password)
                if (result.isSuccess) {
                    val report = result.getOrThrow()
                    showSuccess(report.message)
                    refreshLocalBackups(context)
                } else {
                    val err = result.exceptionOrNull()?.message ?: AppStrings.vmUnknownError(_language.value)
                    val userMsg = if (err.contains("ENCRYPTED_BACKUP_PASSWORD_REQUIRED") || err.contains("mật khẩu") || err.contains("password")) {
                        AppStrings.vmPasswordRequiredForRestore(_language.value)
                    } else err
                    showError(AppStrings.vmRestoreBackupError(userMsg, _language.value))
                }
            } catch (e: Exception) {
                showError(AppStrings.vmGeneralRestoreError(e.message, _language.value))
            }
        }
    }

    fun restoreBackupFromFile(context: Context, file: File, clearExisting: Boolean = false, password: String? = null) {
        viewModelScope.launch {
            try {
                val result = repository.restoreBackupFromFile(file, clearExisting, password)
                if (result.isSuccess) {
                    val report = result.getOrThrow()
                    showSuccess(report.message)
                    refreshLocalBackups(context)
                } else {
                    val err = result.exceptionOrNull()?.message ?: AppStrings.vmUnknownError(_language.value)
                    val userMsg = if (err.contains("ENCRYPTED_BACKUP_PASSWORD_REQUIRED") || err.contains("mật khẩu") || err.contains("password")) {
                        AppStrings.vmPasswordRequiredForRestore(_language.value)
                    } else err
                    showError(AppStrings.vmRestoreFileError(userMsg, _language.value))
                }
            } catch (e: Exception) {
                showError(AppStrings.vmGeneralRestoreError(e.message, _language.value))
            }
        }
    }

    fun isEncryptedBackupFile(file: File): Boolean = _repositoryFlow.value?.isEncryptedBackupFile(file) ?: com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.isEncryptedBackupFile(file)
    fun isEncryptedBackupUri(context: Context, uri: Uri): Boolean = _repositoryFlow.value?.isEncryptedBackupUri(context, uri) ?: com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.isEncryptedBackupUri(context, uri)

    fun shareBackupFile(context: Context, file: File) {
        try {
            repository.shareBackupFile(context, file)
        } catch (e: Exception) {
            showError(AppStrings.vmShareBackupError(e.message, _language.value))
        }
    }
}

// --- AppStrings Localized Extensions for TripFinanceViewModel ---

fun AppStrings.vmDbResetSuccess(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã xóa và tạo mới cơ sở dữ liệu an toàn." else "Database reset and recreated securely."

fun AppStrings.vmDbResetError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khi tạo mới cơ sở dữ liệu: ${err ?: ""}" else "Failed to reset database: ${err ?: ""}"

fun AppStrings.vmInvalidBackupOrPassword(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tệp sao lưu không hợp lệ hoặc sai mật khẩu" else "Invalid backup file or incorrect password"

fun AppStrings.vmRestoreFailed(err: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khôi phục thất bại: $err" else "Restore failed: $err"

fun AppStrings.vmRestoreEmergencySuccess(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khôi phục thành công $count đoàn từ bản sao lưu!" else "Successfully restored $count trip(s) from backup!"

fun AppStrings.vmDataLoadError(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khi nạp dữ liệu" else "Data loading error"

fun AppStrings.vmDataLoadErrorDetail(err: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi nạp dữ liệu: $err" else "Failed to load data: $err"

fun AppStrings.vmEmergencyRestoreError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khôi phục khẩn cấp: ${err ?: ""}" else "Emergency restore error: ${err ?: ""}"

fun AppStrings.vmAiAnalysisError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khi phân tích: ${err ?: ""}" else "Analysis error: ${err ?: ""}"

fun AppStrings.vmCreateTripSuccess(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tạo đoàn '$title' thành công!" else "Trip '$title' created successfully!"

fun AppStrings.vmCreateTripError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khi tạo đoàn: ${err ?: ""}" else "Failed to create trip: ${err ?: ""}"

fun AppStrings.vmAdminOnlyEditTrip(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền chỉnh sửa thông tin đoàn!" else "Only Trip Leader (Admin) can edit trip information!"

fun AppStrings.vmAdminMemberNotFound(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Không tìm thấy thông tin thành viên quản trị đoàn" else "Admin member information not found"

fun AppStrings.vmUpdateTripSuccess(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã cập nhật thông tin đoàn '$title' thành công!" else "Trip '$title' updated successfully!"

fun AppStrings.vmUpdateTripError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi cập nhật đoàn: ${err ?: ""}" else "Failed to update trip: ${err ?: ""}"

fun AppStrings.vmAdminOnlyDeleteTrip(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền xóa đoàn!" else "Only Trip Leader (Admin) can delete trip!"

fun AppStrings.vmDeleteTripSuccess(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã xóa đoàn '$title' thành công!" else "Trip '$title' deleted successfully!"

fun AppStrings.vmDeleteTripError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khi xóa đoàn: ${err ?: ""}" else "Failed to delete trip: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotAddExpense(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể thêm khoản chi mới!" else "Trip '$title' is settled & locked. Cannot add new expenses!"

fun AppStrings.vmInvalidExpenseAmount(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Số tiền chi tiêu không hợp lệ hoặc vượt quá hạn mức cho phép." else "Invalid expense amount or exceeds maximum limit."

fun AppStrings.vmInvalidExchangeRate(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tỷ giá quy đổi không hợp lệ." else "Invalid exchange rate."

fun AppStrings.vmSplitSumMismatch(splitSum: Long, total: Long, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tổng tiền phân bổ ($splitSum VND) không bằng tổng khoản chi ($total VND). Vui lòng kiểm tra lại!"
    else "Split total ($splitSum VND) does not match expense total ($total VND). Please check again!"

fun AppStrings.vmNegativeSplitAmount(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Số tiền phân bổ không được âm. Vui lòng kiểm tra lại tỷ lệ chia!" else "Split amount cannot be negative. Please check split ratios!"

fun AppStrings.vmAddExpenseSuccess(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã thêm khoản chi '$title' thành công!" else "Expense '$title' added successfully!"

fun AppStrings.vmAddExpenseError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi thêm chi tiêu: ${err ?: ""}" else "Failed to add expense: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotEditExpense(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể chỉnh sửa khoản chi!" else "Trip '$title' is settled & locked. Cannot edit expenses!"

fun AppStrings.vmAdminOnlyEditExpense(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền chỉnh sửa các khoản chi!" else "Only Trip Leader (Admin) can edit expenses!"

fun AppStrings.vmUpdateExpenseSuccess(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã cập nhật khoản chi '$title' thành công!" else "Expense '$title' updated successfully!"

fun AppStrings.vmUpdateExpenseError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi cập nhật chi tiêu: ${err ?: ""}" else "Failed to update expense: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotDeleteExpense(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể xóa khoản chi!" else "Trip '$title' is settled & locked. Cannot delete expenses!"

fun AppStrings.vmAdminOnlyDeleteExpense(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền xóa các khoản chi!" else "Only Trip Leader (Admin) can delete expenses!"

fun AppStrings.vmDeleteExpenseSuccess(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã xóa khoản chi '$title'" else "Expense '$title' deleted"

fun AppStrings.vmDeleteExpenseError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi xóa khoản chi: ${err ?: ""}" else "Failed to delete expense: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotContributeFund(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể nộp thêm quỹ!" else "Trip '$title' is settled & locked. Cannot contribute to fund!"

fun AppStrings.vmAdminOrTreasurerOnlyFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn hoặc Thủ quỹ mới có quyền ghi nhận thu quỹ!" else "Only Leader or Treasurer can record fund contributions!"

fun AppStrings.vmInvalidFundAmount(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Số tiền nộp quỹ không hợp lệ (phải từ 1 đến 100 tỷ)." else "Invalid contribution amount (must be from 1 to 100B)."

fun AppStrings.vmFundContributionSuccess(amount: Long, contributorName: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã ghi nhận nộp quỹ $amount VND cho $contributorName!" else "Recorded fund contribution of $amount VND for $contributorName!"

fun AppStrings.vmFundContributionError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi nộp quỹ: ${err ?: ""}" else "Failed to record contribution: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotDeleteFund(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể xóa khoản nộp quỹ!" else "Trip '$title' is settled & locked. Cannot delete fund entry!"

fun AppStrings.vmAdminOnlyDeleteFund(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền xóa khoản đóng góp quỹ!" else "Only Trip Leader (Admin) can delete fund entries!"

fun AppStrings.vmDeleteFundSuccess(name: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã xóa khoản nộp quỹ của $name" else "Deleted fund contribution of $name"

fun AppStrings.vmDeleteFundError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi xóa khoản nộp quỹ: ${err ?: ""}" else "Failed to delete fund contribution: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotAddMember(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể thêm thành viên mới!" else "Trip '$title' is settled & locked. Cannot add members!"

fun AppStrings.vmAdminOrTreasurerOnlyAddMember(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn hoặc Thủ quỹ mới có quyền thêm thành viên!" else "Only Leader or Treasurer can add members!"

fun AppStrings.vmAddMemberSuccess(name: String, role: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã thêm thành viên '$name' ($role)" else "Added member '$name' ($role)"

fun AppStrings.vmAddMemberError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi thêm thành viên: ${err ?: ""}" else "Failed to add member: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotEditMember(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể sửa thông tin thành viên!" else "Trip '$title' is settled & locked. Cannot edit members!"

fun AppStrings.vmAdminOnlyEditMember(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền chỉnh sửa thông tin thành viên!" else "Only Trip Leader (Admin) can edit members!"

fun AppStrings.vmUpdateMemberSuccess(name: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã cập nhật thông tin thành viên '$name' thành công!" else "Member '$name' updated successfully!"

fun AppStrings.vmUpdateMemberError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi cập nhật thành viên: ${err ?: ""}" else "Failed to update member: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotChangeRole(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể thay đổi vai trò thành viên!" else "Trip '$title' is settled & locked. Cannot change member role!"

fun AppStrings.vmAdminOnlyChangeRole(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền thay đổi vai trò thành viên!" else "Only Trip Leader (Admin) can change member roles!"

fun AppStrings.vmChangeRoleSuccess(name: String, role: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã đổi vai trò của $name thành $role" else "Changed role of $name to $role"

fun AppStrings.vmChangeRoleError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi cập nhật vai trò: ${err ?: ""}" else "Failed to update role: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotDeleteMember(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể xóa hoặc ngừng hoạt động thành viên!" else "Trip '$title' is settled & locked. Cannot modify members!"

fun AppStrings.vmAdminOnlyDeleteMember(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền xóa/ngừng hoạt động thành viên!" else "Only Trip Leader (Admin) can deactivate/delete members!"

fun AppStrings.vmProcessMemberStatusSuccess(name: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã xử lý trạng thái thành viên $name" else "Processed member status for $name"

fun AppStrings.vmProcessMemberStatusError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi xóa/vô hiệu hóa thành viên: ${err ?: ""}" else "Failed to update member status: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotUpdateRate(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể thay đổi tỷ giá!" else "Trip '$title' is settled & locked. Cannot update rates!"

fun AppStrings.vmAdminOrTreasurerOnlyRate(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn hoặc Thủ quỹ mới có quyền cập nhật tỷ giá!" else "Only Leader or Treasurer can update exchange rates!"

fun AppStrings.vmUpdateRateSuccess(code: String, rate: Double, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã cập nhật tỷ giá 1 $code = $rate VND" else "Updated rate: 1 $code = $rate VND"

fun AppStrings.vmUpdateRateError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi cập nhật tỷ giá: ${err ?: ""}" else "Failed to update exchange rate: ${err ?: ""}"

fun AppStrings.vmTripAlreadySettled(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi này đã được khóa sổ quyết toán từ trước!" else "This trip has already been settled and locked!"

fun AppStrings.vmAdminOnlyLockSettlement(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền khóa sổ và quyết toán chuyến đi!" else "Only Trip Leader (Admin) can finalize and lock settlement!"

fun AppStrings.vmNoMembersCannotSettle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đoàn không có thành viên nào! Không thể thực hiện quyết toán và khóa sổ." else "No members in group! Cannot settle or lock trip."

fun AppStrings.vmDiscrepancyCannotSettle(discrepancy: Long, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hệ thống phát hiện chênh lệch đối soát ($discrepancy VND). Toàn bộ chi tiêu phải được phân bổ cân bằng tuyệt đối để khóa sổ!"
    else "Discrepancy detected ($discrepancy VND). All expenses must be fully balanced to lock!"

fun AppStrings.vmDefaultSnapshotTitle(timestamp: Long, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quyết toán ngày $timestamp" else "Settlement on $timestamp"

fun AppStrings.vmLockSettlementSuccess(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã khóa sổ và lưu Snapshot quyết toán thành công!" else "Trip settled and snapshot saved successfully!"

fun AppStrings.vmLockSettlementError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khóa sổ quyết toán: ${err ?: ""}" else "Failed to lock settlement: ${err ?: ""}"

fun AppStrings.vmTripNotSettledYet(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi hiện chưa khóa sổ!" else "Trip is not locked yet!"

fun AppStrings.vmAdminOnlyReopenTrip(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉ Trưởng đoàn (Admin) mới có quyền mở khóa sổ chuyến đi!" else "Only Trip Leader (Admin) can reopen the trip ledger!"

fun AppStrings.vmReopenTripSuccess(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã mở khóa sổ chuyến đi '$title' thành công! Bạn có thể tiếp tục cập nhật dữ liệu." else "Trip '$title' ledger reopened! You can resume updating data."

fun AppStrings.vmReopenTripError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi mở khóa sổ: ${err ?: ""}" else "Failed to reopen trip: ${err ?: ""}"

fun AppStrings.vmBackupCreatedEncrypted(fileName: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã tạo bản sao lưu mã hóa AES-256-GCM: $fileName" else "Created AES-256-GCM encrypted backup: $fileName"

fun AppStrings.vmBackupCreatedPlain(fileName: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã tạo bản sao lưu an toàn: $fileName" else "Created secure backup: $fileName"

fun AppStrings.vmCreateBackupError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi tạo sao lưu: ${err ?: ""}" else "Failed to create backup: ${err ?: ""}"

fun AppStrings.vmExportBackupEncryptedSuccess(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã xuất tệp sao lưu mã hóa AES-256-GCM thành công!" else "Exported AES-256-GCM encrypted backup successfully!"

fun AppStrings.vmExportBackupPlainSuccess(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã xuất tệp sao lưu thành công!" else "Exported backup file successfully!"

fun AppStrings.vmExportFileError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi xuất tệp: ${err ?: ""}" else "Failed to export file: ${err ?: ""}"

fun AppStrings.vmExportBackupError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi xuất sao lưu: ${err ?: ""}" else "Backup export error: ${err ?: ""}"

fun AppStrings.vmUnknownError(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi không xác định" else "Unknown error"

fun AppStrings.vmPasswordRequiredForRestore(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tệp sao lưu yêu cầu mật khẩu giải mã chính xác." else "Backup file requires a valid decryption password."

fun AppStrings.vmRestoreBackupError(msg: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khôi phục sao lưu: $msg" else "Restore error: $msg"

fun AppStrings.vmRestoreFileError(msg: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khôi phục tệp: $msg" else "File restore error: $msg"

fun AppStrings.vmGeneralRestoreError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi khôi phục: ${err ?: ""}" else "Failed to restore: ${err ?: ""}"

fun AppStrings.vmShareBackupError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi chia sẻ tệp sao lưu: ${err ?: ""}" else "Failed to share backup file: ${err ?: ""}"

fun AppStrings.vmTripLockedCannotRestoreExpense(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi '$title' đã được khóa sổ quyết toán. Không thể hoàn tác khoản chi!"
    else "Trip '$title' is settled & locked. Cannot undo deleted expense!"

fun AppStrings.vmRestoreExpenseSuccess(title: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đã hoàn tác khôi phục khoản chi '$title'"
    else "Restored expense '$title'"

fun AppStrings.vmRestoreExpenseError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi hoàn tác khôi phục khoản chi: ${err ?: ""}"
    else "Failed to restore expense: ${err ?: ""}"

fun AppStrings.vmUpdateTransferError(err: String?, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lỗi cập nhật trạng thái chuyển khoản: ${err ?: ""}"
    else "Failed to update transfer status: ${err ?: ""}"


