package com.hiepnguyen.tripfinance.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.data.entity.ExpenseEntity
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.data.entity.ExpenseSplitEntity
import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.ui.components.CategoryIcon
import com.hiepnguyen.tripfinance.ui.components.NumberFormatUtils
import com.hiepnguyen.tripfinance.ui.theme.*
import com.hiepnguyen.tripfinance.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    uiState: UiState,
    onOpenAddExpense: () -> Unit,
    onEditExpense: (
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
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onDeleteExpense: (ExpenseEntity) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onSearchChange: (String) -> Unit
) {
    var selectedExpenseForDetail by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseToEdit by remember { mutableStateOf<ExpenseEntity?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    val splitsByExpense = remember(uiState.allSplits) {
        uiState.allSplits.groupBy { it.expenseId }
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isAdmin = uiState.currentMember?.role == "ADMIN"
    val lang = LocalAppLanguage.current
    val isSettled = uiState.currentTrip?.isSettled == true
    val tripCurrency = uiState.currentTrip?.baseCurrency ?: "VND"

    Scaffold(
        floatingActionButton = {
            if (!isSettled) {
                FloatingActionButton(
                    onClick = onOpenAddExpense,
                    containerColor = EmeraldPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 72.dp)
                        .testTag("fab_add_expense")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = AppStrings.addExpenseFabCd(lang))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (isSettled) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = AppStrings.tripSettledBannerExpenses(lang),
                            fontSize = 11.sp,
                            color = Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(AppStrings.searchExpensesHint(lang)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF64748B))
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchChange("")
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }) {
                            Icon(Icons.Filled.Clear, contentDescription = AppStrings.clearSearchCd(lang))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("expense_search_field")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Chips
            val categories = listOf(
                null to AppStrings.categoryAll(lang),
                "FOOD" to AppStrings.categoryFood(lang),
                "TRANSPORT" to AppStrings.categoryTransport(lang),
                "HOTEL" to AppStrings.categoryHotel(lang),
                "SIGHTSEEING" to AppStrings.categorySightseeing(lang),
                "ENTERTAINMENT" to AppStrings.categoryEntertainment(lang),
                "SHOPPING" to AppStrings.categoryShopping(lang),
                "OTHER" to AppStrings.categoryOther(lang)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories, key = { it.first ?: "ALL" }) { (catKey, catLabel) ->
                    FilterChip(
                        selected = uiState.selectedCategoryFilter == catKey,
                        onClick = { onCategoryFilterChange(catKey) },
                        label = { Text(catLabel, fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expense List
            if (uiState.expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = AppStrings.noExpensesYet(lang),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            fontSize = 15.sp
                        )
                        Text(
                            text = AppStrings.tapPlusToCreateExpense(lang),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(uiState.expenses, key = { it.id }) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            members = uiState.members,
                            isAdmin = isAdmin,
                            isSettled = isSettled,
                            baseCurrency = tripCurrency,
                            onClick = { selectedExpenseForDetail = expense },
                            onEdit = { expenseToEdit = expense },
                            onDelete = { expenseToDelete = expense }
                        )
                    }
                }
            }
        }
    }

    // Expense Detail Dialog
    selectedExpenseForDetail?.let { exp ->
        val splitsForExp = splitsByExpense[exp.id] ?: emptyList()
        ExpenseDetailDialog(
            expense = exp,
            members = uiState.members,
            splits = splitsForExp,
            isAdmin = isAdmin,
            isSettled = isSettled,
            baseCurrency = tripCurrency,
            onDismiss = { selectedExpenseForDetail = null },
            onEdit = {
                selectedExpenseForDetail = null
                expenseToEdit = exp
            },
            onDelete = {
                selectedExpenseForDetail = null
                expenseToDelete = exp
            }
        )
    }

    // Edit Expense Dialog
    expenseToEdit?.let { exp ->
        val splitsForExp = splitsByExpense[exp.id] ?: emptyList()
        AddExpenseDialog(
            members = uiState.members,
            exchangeRates = uiState.exchangeRates,
            currentMemberId = uiState.currentMember?.id,
            initialExpense = exp,
            initialSplits = splitsForExp,
            baseCurrency = uiState.currentTrip?.baseCurrency ?: "VND",
            onDismiss = { expenseToEdit = null },
            onSave = { title, category, payerType, payerMemberId, totalAmount, currency, exchangeRate, splitType, splits, note, timestamp ->
                onEditExpense(
                    exp.id, title, category, payerType, payerMemberId, totalAmount, currency, exchangeRate, splitType, splits, note, timestamp
                )
                expenseToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    expenseToDelete?.let { exp ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            icon = {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text(AppStrings.confirmDeleteExpenseHeader(lang), fontWeight = FontWeight.Bold)
            },
            text = {
                Text(AppStrings.confirmDeleteExpensePrompt(exp.title, NumberFormatUtils.formatCurrency(exp.convertedTotalAmount, tripCurrency), lang))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteExpense(exp)
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_expense_button")
                ) {
                    Text(AppStrings.deleteExpenseBtn(lang))
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text(AppStrings.cancelBtn(lang))
                }
            }
        )
    }
}

@Composable
fun ExpenseItemCard(
    expense: ExpenseEntity,
    members: List<TripMemberEntity>,
    isAdmin: Boolean,
    isSettled: Boolean = false,
    baseCurrency: String = "VND",
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val payerName = if (expense.payerType == "FUND") {
        AppStrings.groupFundPayer(lang)
    } else {
        members.find { it.id == expense.payerMemberId }?.name ?: AppStrings.memberDefaultName(lang)
    }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(expense.timestamp))

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIcon(category = expense.category, size = 22)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Thời gian chi đặt trực tiếp dưới tên khoản chi
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Người chi hộ đặt ở phía dưới thời gian chi
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (expense.payerType == "FUND") Color(0xFFFEF3C7) else Color(0xFFE0E7FF)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (expense.payerType == "FUND") Icons.Filled.Savings else Icons.Filled.Person,
                            contentDescription = null,
                            tint = if (expense.payerType == "FUND") Color(0xFF92400E) else Color(0xFF3730A3),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (expense.payerType == "FUND") AppStrings.fundPaidBadge(lang) else AppStrings.memberPaidBadge(payerName, lang),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (expense.payerType == "FUND") Color(0xFF92400E) else Color(0xFF3730A3)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = NumberFormatUtils.formatCurrency(expense.convertedTotalAmount, baseCurrency),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )

                if (expense.currency != baseCurrency) {
                    Text(
                        text = "${expense.totalAmount} ${expense.currency}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                if (isAdmin && !isSettled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(28.dp).testTag("edit_expense_${expense.id}")
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = AppStrings.editExpenseCd(lang),
                                tint = IndigoSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp).testTag("delete_expense_${expense.id}")
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = AppStrings.deleteExpenseCd(lang),
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseDetailDialog(
    expense: ExpenseEntity,
    members: List<TripMemberEntity>,
    splits: List<ExpenseSplitEntity> = emptyList(),
    isAdmin: Boolean = false,
    isSettled: Boolean = false,
    baseCurrency: String = "VND",
    onDismiss: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val lang = LocalAppLanguage.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val payerMember = members.find { it.id == expense.payerMemberId }
    val payerName = if (expense.payerType == "FUND") {
        AppStrings.groupFundDirectDeduct(lang)
    } else {
        "${payerMember?.name ?: AppStrings.memberDefaultName(lang)} (${when (payerMember?.role) {
            "ADMIN" -> AppStrings.roleAdmin(lang)
            "TREASURER" -> AppStrings.roleTreasurer(lang)
            else -> AppStrings.memberDefaultName(lang)
        }})"
    }

    val categoryLabel = when (expense.category) {
        "FOOD" -> AppStrings.categoryFood(lang)
        "TRANSPORT" -> AppStrings.categoryTransport(lang)
        "HOTEL" -> AppStrings.categoryHotelAccommodation(lang)
        "SIGHTSEEING" -> AppStrings.categorySightseeing(lang)
        "ENTERTAINMENT" -> AppStrings.categoryEntertainment(lang)
        "SHOPPING" -> AppStrings.categoryShopping(lang)
        else -> AppStrings.categoryOtherExpenses(lang)
    }

    val splitTypeLabel = when (expense.splitType) {
        "EQUAL" -> AppStrings.splitEqualAll(lang)
        "CUSTOM_PARTICIPANT" -> AppStrings.splitCustomParticipants(lang)
        "RATIO" -> AppStrings.splitRatioPercentage(lang)
        "CUSTOM_AMOUNT" -> AppStrings.splitCustomExactAmount(lang)
        else -> expense.splitType
    }

    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(category = expense.category, size = 26)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(expense.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = AppStrings.expenseTimeLabel(dateFormat.format(Date(expense.timestamp)), lang),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmeraldPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(AppStrings.convertedTotalAmountLabel(baseCurrency, lang), fontSize = 11.sp, color = EmeraldOnPrimaryContainer)
                            Text(
                                text = NumberFormatUtils.formatCurrency(expense.convertedTotalAmount, baseCurrency),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldOnPrimaryContainer
                            )
                            if (expense.currency != baseCurrency) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = AppStrings.originalAmountAndRate(expense.totalAmount, expense.currency, expense.exchangeRate, lang),
                                    fontSize = 12.sp,
                                    color = EmeraldOnPrimaryContainer
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = AppStrings.expenseCategoryRow(categoryLabel, lang),
                            fontSize = 13.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = AppStrings.expensePayerRow(payerName, lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = AppStrings.expenseSplitMethodRow(splitTypeLabel, lang),
                            fontSize = 13.sp,
                            color = Color(0xFF1E293B)
                        )
                        if (expense.note.isNotBlank()) {
                            HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                text = AppStrings.expenseNoteRow(expense.note, lang),
                                fontSize = 13.sp,
                                color = Color(0xFF334155)
                            )
                        }
                    }
                }

                // Participant splits breakdown list
                if (splits.isNotEmpty()) {
                    item {
                        Text(
                            text = AppStrings.participantsCountTitle(splits.size, lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            splits.forEach { sp ->
                                val memName = members.find { it.id == sp.memberId }?.name ?: AppStrings.memberDefaultName(lang)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = memName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF334155)
                                    )
                                    Text(
                                        text = NumberFormatUtils.formatCurrency(sp.amount, baseCurrency),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                if (isSettled) {
                    item {
                        Text(
                            text = AppStrings.tripSettledBannerLocked(lang),
                            fontSize = 11.sp,
                            color = Color(0xFFB91C1C),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (!isAdmin) {
                    item {
                        Text(
                            text = AppStrings.adminOnlyEditNotice(lang),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isAdmin && !isSettled) {
                    OutlinedButton(
                        onClick = onEdit,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoSecondary)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.editAction(lang))
                    }
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(AppStrings.deleteAction(lang))
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(AppStrings.closeAction(lang))
                }
            }
        }
    )
}


// =========================================================================
// AppStrings Localization Extensions for ExpensesScreen
// =========================================================================

fun AppStrings.addExpenseFabCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thêm khoản chi" else "Add expense"

fun AppStrings.tripSettledBannerExpenses(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chuyến đi đã khóa sổ quyết toán. Dữ liệu chi tiêu được bảo vệ và không thể thêm/sửa/xóa."
    else "Trip ledger is settled. Expense records are protected and cannot be added/edited/deleted."

fun AppStrings.searchExpensesHint(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tìm kiếm khoản chi, ghi chú..." else "Search expenses, notes..."

fun AppStrings.clearSearchCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa tìm kiếm" else "Clear search"

fun AppStrings.categoryAll(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tất cả" else "All"

fun AppStrings.categoryFood(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Ăn uống" else "Food & Dining"

fun AppStrings.categoryTransport(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Di chuyển" else "Transportation"

fun AppStrings.categoryHotel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khách sạn" else "Accommodation"

fun AppStrings.categorySightseeing(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Vé tham quan" else "Sightseeing"

fun AppStrings.categoryEntertainment(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Giải trí" else "Entertainment"

fun AppStrings.categoryShopping(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mua sắm" else "Shopping"

fun AppStrings.categoryOther(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khác" else "Others"

fun AppStrings.noExpensesYet(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa có khoản chi nào" else "No expenses recorded yet"

fun AppStrings.tapPlusToCreateExpense(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhấn nút '+' bên dưới để tạo khoản chi mới"
    else "Tap '+' button below to record a new expense"

fun AppStrings.confirmDeleteExpenseHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xác Nhận Xóa Khoản Chi" else "Confirm Delete Expense"

fun AppStrings.confirmDeleteExpensePrompt(title: String, amountStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bạn có chắc chắn muốn xóa khoản chi '$title' ($amountStr)? Thao tác này chỉ dành cho Trưởng đoàn."
    else "Are you sure you want to delete expense '$title' ($amountStr)? Only the Group Leader can perform this action."

fun AppStrings.deleteExpenseBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa khoản chi" else "Delete Expense"

fun AppStrings.cancelBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hủy" else "Cancel"

fun AppStrings.groupFundPayer(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quỹ chung đoàn" else "Group Fund"

fun AppStrings.memberDefaultName(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên" else "Member"

fun AppStrings.fundPaidBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quỹ chi" else "Fund Paid"

fun AppStrings.memberPaidBadge(payerName: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chi hộ: $payerName" else "Paid by: $payerName"

fun AppStrings.editExpenseCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sửa khoản chi" else "Edit expense"

fun AppStrings.deleteExpenseCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa khoản chi" else "Delete expense"

fun AppStrings.groupFundDirectDeduct(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quỹ chung của đoàn (Trừ thẳng vào quỹ)"
    else "Group Fund (Deducted directly from fund)"

fun AppStrings.roleAdmin(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Trưởng đoàn" else "Leader"

fun AppStrings.roleTreasurer(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thủ quỹ" else "Treasurer"

fun AppStrings.categoryHotelAccommodation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khách sạn / Lưu trú" else "Hotel / Accommodation"

fun AppStrings.categoryOtherExpenses(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chi phí khác" else "Other Expenses"

fun AppStrings.splitEqualAll(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia đều cho tất cả thành viên" else "Split equally among all members"

fun AppStrings.splitCustomParticipants(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chọn danh sách người tham gia" else "Custom participant roster"

fun AppStrings.splitRatioPercentage(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia theo tỷ lệ %" else "Split by percentage %"

fun AppStrings.splitCustomExactAmount(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tùy nhập số tiền từng người" else "Custom exact amount per person"

fun AppStrings.expenseTimeLabel(timeStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thời gian: $timeStr" else "Time: $timeStr"

fun AppStrings.convertedTotalAmountLabel(currency: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tổng số tiền quy đổi ($currency)" else "Converted Total Amount ($currency)"

fun AppStrings.originalAmountAndRate(amount: Double, curr: String, rate: Double, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tiền gốc: $amount $curr • Tỷ giá: $rate"
    else "Original: $amount $curr • Rate: $rate"

fun AppStrings.expenseCategoryRow(category: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Danh mục: $category" else "• Category: $category"

fun AppStrings.expensePayerRow(payer: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Người xuất tiền: $payer" else "• Payer: $payer"

fun AppStrings.expenseSplitMethodRow(splitMethod: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Cách phân bổ: $splitMethod" else "• Split Method: $splitMethod"

fun AppStrings.expenseNoteRow(note: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "• Ghi chú: $note" else "• Note: $note"

fun AppStrings.participantsCountTitle(count: Int, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Danh Sách Người Tham Gia ($count người):"
    else "Participants Breakdown ($count members):"

fun AppStrings.tripSettledBannerLocked(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "🔒 Chuyến đi đã khóa sổ. Dữ liệu chi tiêu được niêm phong, không thể sửa hoặc xóa."
    else "🔒 Trip ledger is closed. Expense records are sealed and cannot be edited or deleted."

fun AppStrings.adminOnlyEditNotice(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "ℹ️ Chỉ Trưởng đoàn (Admin) mới có quyền sửa hoặc xóa khoản chi này."
    else "ℹ️ Only the Group Leader (Admin) can edit or delete this expense."

fun AppStrings.editAction(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sửa" else "Edit"

fun AppStrings.deleteAction(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa" else "Delete"

fun AppStrings.closeAction(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đóng" else "Close"
