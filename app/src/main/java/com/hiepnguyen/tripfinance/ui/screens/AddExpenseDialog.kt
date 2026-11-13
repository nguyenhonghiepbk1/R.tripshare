package com.hiepnguyen.tripfinance.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.data.entity.ExchangeRateEntity
import com.hiepnguyen.tripfinance.data.entity.TripMemberEntity
import com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator
import com.hiepnguyen.tripfinance.domain.model.AmountValidationResult
import com.hiepnguyen.tripfinance.domain.model.RateValidationResult
import com.hiepnguyen.tripfinance.domain.model.sumOfSafe
import com.hiepnguyen.tripfinance.domain.engine.SplitCalculator
import com.hiepnguyen.tripfinance.domain.model.DefaultExchangeRates
import com.hiepnguyen.tripfinance.ui.components.CategoryIcon
import com.hiepnguyen.tripfinance.ui.components.NumberFormatUtils
import com.hiepnguyen.tripfinance.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import kotlin.math.abs
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    members: List<TripMemberEntity>,
    exchangeRates: List<ExchangeRateEntity>,
    currentMemberId: String?,
    initialExpense: com.hiepnguyen.tripfinance.data.entity.ExpenseEntity? = null,
    initialSplits: List<com.hiepnguyen.tripfinance.data.entity.ExpenseSplitEntity> = emptyList(),
    baseCurrency: String = "VND",
    lang: AppLanguage = LocalAppLanguage.current,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    val isEditMode = initialExpense != null

    // Basic Information
    var title by remember { mutableStateOf(initialExpense?.title ?: "") }
    var note by remember { mutableStateOf(initialExpense?.note ?: "") }
    var category by remember { mutableStateOf(initialExpense?.category ?: "FOOD") }

    // Payer Selection
    var payerType by remember { mutableStateOf(initialExpense?.payerType ?: "MEMBER") } // MEMBER or FUND
    val defaultPayerId = remember(members, currentMemberId, initialExpense) {
        if (initialExpense?.payerMemberId != null) {
            initialExpense.payerMemberId
        } else if (members.any { it.id == currentMemberId }) {
            currentMemberId ?: ""
        } else {
            members.firstOrNull()?.id ?: ""
        }
    }
    var selectedPayerMemberId by remember { mutableStateOf(defaultPayerId) }
    var payerDropdownExpanded by remember { mutableStateOf(false) }

    // Amount & Currency
    var amountText by remember {
        mutableStateOf(
            initialExpense?.let { exp ->
                if (exp.totalAmount % 1.0 == 0.0) exp.totalAmount.toLong().toString()
                else exp.totalAmount.toString()
            } ?: ""
        )
    }
    var selectedCurrency by remember { mutableStateOf(initialExpense?.currency ?: baseCurrency) }
    var exchangeRateText by remember { mutableStateOf(initialExpense?.exchangeRate?.toString() ?: "1.0") }

    // Date & Time (Thời gian chi)
    val context = LocalContext.current
    var expenseTimestamp by remember { mutableStateOf(initialExpense?.timestamp ?: System.currentTimeMillis()) }
    val dateTimeFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var dateInputText by remember { mutableStateOf(dateTimeFormatter.format(Date(expenseTimestamp))) }
    var dateInputError by remember { mutableStateOf(false) }

    fun updateTimestamp(newTimestamp: Long) {
        expenseTimestamp = newTimestamp
        dateInputText = dateTimeFormatter.format(Date(newTimestamp))
        dateInputError = false
    }

    fun openDateTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = expenseTimestamp }
        val datePicker = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // Open TimePicker right after date selection
                val timePicker = android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        updateTimestamp(cal.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                )
                timePicker.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
    }

    // Split Strategy: EQUAL, RATIO, CUSTOM_AMOUNT, CUSTOM_PARTICIPANT
    var splitType by remember { mutableStateOf(initialExpense?.splitType ?: "EQUAL") }

    // Custom participant selection state
    val selectedParticipantIds = remember(members, initialSplits) {
        mutableStateMapOf<String, Boolean>().apply {
            if (initialSplits.isNotEmpty()) {
                val splitMemberIds = initialSplits.map { it.memberId }.toSet()
                members.forEach { put(it.id, splitMemberIds.contains(it.id)) }
            } else {
                members.forEach { put(it.id, it.isActive) }
            }
        }
    }

    // Ratio percentages state (Khởi tạo mặc định đảm bảo tổng đúng 100.0%, không bị 99.9% cho 3 người)
    val memberRatios = remember(members) {
        mutableStateMapOf<String, String>().apply {
            val activeMembers = members.filter { it.isActive }
            val count = activeMembers.size.coerceAtLeast(1)
            val baseInt = 1000 / count
            val remainderInt = 1000 % count
            activeMembers.forEachIndexed { index, m ->
                val ratioTenths = baseInt + (if (index < remainderInt) 1 else 0)
                val ratioDouble = ratioTenths / 10.0
                put(m.id, String.format(Locale.US, "%.1f", ratioDouble))
            }
            members.filter { !it.isActive }.forEach { put(it.id, "0.0") }
        }
    }

    // Custom amounts state
    val memberCustomAmounts = remember(members, initialSplits) {
        mutableStateMapOf<String, String>().apply {
            if (initialSplits.isNotEmpty()) {
                members.forEach { m ->
                    val sp = initialSplits.find { it.memberId == m.id }
                    put(m.id, sp?.amount?.toString() ?: "0")
                }
            } else {
                members.forEach { put(it.id, "0") }
            }
        }
    }

    // Update exchange rate when currency changes
    LaunchedEffect(selectedCurrency, baseCurrency) {
        if (selectedCurrency.equals(baseCurrency, ignoreCase = true)) {
            exchangeRateText = "1.0"
        } else {
            val rate = exchangeRates.find { it.currencyCode.equals(selectedCurrency, ignoreCase = true) }?.rateToBase
                ?: DefaultExchangeRates.getRate(selectedCurrency)
            exchangeRateText = if (rate % 1.0 == 0.0) rate.toLong().toString() else rate.toString()
        }
    }

    val amountValidation = FinancialInputValidator.parseAmount(amountText, selectedCurrency)
    val rateValidation = FinancialInputValidator.parseRate(exchangeRateText, selectedCurrency, baseCurrency)

    val parsedAmount = (amountValidation as? AmountValidationResult.Success)?.amount ?: 0.0
    val parsedRate = (rateValidation as? RateValidationResult.Success)?.rate ?: 0.0
    val isAmountValid = amountValidation is AmountValidationResult.Success
    val isRateValid = rateValidation is RateValidationResult.Success

    val convertedTotalVnd = if (isAmountValid && isRateValid) {
        FinancialInputValidator.convertToVnd(parsedAmount, parsedRate)
    } else 0L

    // Compute live splits based on selected splitType
    val calculatedSplits: List<Pair<String, Long>> = remember(
        convertedTotalVnd,
        splitType,
        selectedParticipantIds.toMap(),
        memberRatios.toMap(),
        memberCustomAmounts.toMap()
    ) {
        if (convertedTotalVnd <= 0L || members.isEmpty()) {
            emptyList()
        } else {
            when (splitType) {
                "EQUAL" -> {
                    val activeIds = members.filter { it.isActive }.map { it.id }
                    SplitCalculator.calculateEqualSplit(convertedTotalVnd, activeIds, selectedPayerMemberId)
                }
                "CUSTOM_PARTICIPANT" -> {
                    val selectedIds = members.filter { selectedParticipantIds[it.id] == true }.map { it.id }
                    SplitCalculator.calculateSelectedParticipantsSplit(convertedTotalVnd, selectedIds, selectedPayerMemberId)
                }
                "RATIO" -> {
                    val ratioPairs = members.map { m ->
                        val ratio = memberRatios[m.id]?.toDoubleOrNull() ?: 0.0
                        m.id to ratio
                    }
                    val (splits, _) = SplitCalculator.calculateRatioSplit(convertedTotalVnd, ratioPairs, selectedPayerMemberId)
                    splits
                }
                "CUSTOM_AMOUNT" -> {
                    members.map { m ->
                        val amt = memberCustomAmounts[m.id]?.toLongOrNull() ?: 0L
                        m.id to amt
                    }
                }
                else -> emptyList()
            }
        }
    }

    val totalRatio = remember(members, memberRatios.toMap()) {
        members.sumOf { memberRatios[it.id]?.toDoubleOrNull() ?: 0.0 }
    }
    val isRatioValid = remember(totalRatio) {
        abs(totalRatio - 100.0) <= 0.01
    }

    val currentAllocatedSum = calculatedSplits.sumOfSafe { it.second }
    val hasNoNegativeSplits = calculatedSplits.isNotEmpty() && calculatedSplits.all { it.second >= 0L }
    val isAllocationExact = convertedTotalVnd > 0 && currentAllocatedSum == convertedTotalVnd && (splitType != "RATIO" || isRatioValid)
    val diff = convertedTotalVnd - currentAllocatedSum

    val isPayerValid = payerType == "FUND" || selectedPayerMemberId.isNotBlank()
    val isFormValid = title.isNotBlank() &&
            isAmountValid &&
            isRateValid &&
            convertedTotalVnd > 0 &&
            isAllocationExact &&
            hasNoNegativeSplits &&
            (splitType != "RATIO" || isRatioValid) &&
            isPayerValid

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    BackHandler { safeDismiss() }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true, usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(vertical = 12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = EmeraldPrimaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AddShoppingCart,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isEditMode) AppStrings.editExpenseDialogTitle(lang) else AppStrings.addExpenseDialogTitle(lang),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEditMode) AppStrings.editExpenseDialogSubtitle(lang) else AppStrings.addExpenseDialogSubtitle(lang),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                IconButton(onClick = safeDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = AppStrings.closeExpenseDialogCd(lang), tint = Color(0xFF94A3B8))
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. TÊN KHOẢN CHI & DANH MỤC
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = AppStrings.section1ExpenseNameCategory(lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(AppStrings.expenseNamePlaceholder(lang)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expense_title_input")
                        )

                        // Category Selection Chips
                        val categories = listOf(
                            "FOOD" to AppStrings.categoryFood(lang),
                            "TRANSPORT" to AppStrings.categoryTransport(lang),
                            "HOTEL" to AppStrings.categoryHotel(lang),
                            "SIGHTSEEING" to "Tham quan",
                            "ENTERTAINMENT" to AppStrings.categoryEntertainment(lang),
                            "SHOPPING" to AppStrings.categoryShopping(lang),
                            "OTHER" to AppStrings.categoryOther(lang)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { (catKey, catLabel) ->
                                FilterChip(
                                    selected = category == catKey,
                                    onClick = { category = catKey },
                                    leadingIcon = { CategoryIcon(category = catKey, size = 16) },
                                    label = { Text(catLabel, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }

                // 2. SỐ TIỀN & LOẠI TIỀN (TIỀN TỆ & TỶ GIÁ)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = AppStrings.section2AmountAndCurrency(lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = amountText,
                                onValueChange = { input ->
                                    val trimmed = input.trim()
                                    if (selectedCurrency == "VND") {
                                        if (trimmed.all { it.isDigit() || it == '.' || it == ',' || it == ' ' }) {
                                            amountText = trimmed
                                        }
                                    } else {
                                        if (trimmed.all { it.isDigit() || it == '.' || it == ',' || it == ' ' }) {
                                            amountText = trimmed
                                        }
                                    }
                                },
                                isError = amountText.isNotEmpty() && !isAmountValid,
                                supportingText = {
                                    if (amountText.isNotEmpty() && amountValidation is AmountValidationResult.Error) {
                                        Text(amountValidation.message, color = DangerRed, fontSize = 11.sp)
                                    }
                                },
                                label = { Text(AppStrings.amountFieldLabel(lang)) },
                                placeholder = { Text(if (selectedCurrency == "VND") "0" else "0.00") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (selectedCurrency == "VND") KeyboardType.Number else KeyboardType.Decimal,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .weight(1.6f)
                                    .testTag("expense_amount_input")
                            )

                            // Currency Selector
                            var currExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { currExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                ) {
                                    Text(
                                        text = selectedCurrency,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = if (lang == AppLanguage.VI) "Chọn loại tiền" else "Select currency")
                                }

                                DropdownMenu(
                                    expanded = currExpanded,
                                    onDismissRequest = { currExpanded = false }
                                ) {
                                    listOf("VND", "USD", "EUR", "JPY", "KRW", "THB", "SGD", "CNY").forEach { c ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(c, fontWeight = FontWeight.Bold)
                                                    if (c == selectedCurrency) {
                                                        Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldPrimary)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedCurrency = c
                                                currExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Amount Add Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                50_000L to "+50k",
                                100_000L to "+100k",
                                200_000L to "+200k",
                                500_000L to "+500k",
                                1_000_000L to "+1Tr",
                                2_000_000L to "+2Tr"
                            ).forEach { (addVal, label) ->
                                SuggestionChip(
                                    onClick = {
                                        val parsed = FinancialInputValidator.parseAmount(amountText, selectedCurrency)
                                        val current = (parsed as? AmountValidationResult.Success)?.amount ?: 0.0
                                        val updated = current + addVal
                                        amountText = if (selectedCurrency == "VND" || updated % 1.0 == 0.0) updated.toLong().toString() else updated.toString()
                                    },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }

                            if (amountText.isNotEmpty()) {
                                SuggestionChip(
                                    onClick = { amountText = "" },
                                    label = { Text(AppStrings.clearExpenseInputCd(lang), fontSize = 11.sp, color = DangerRed) }
                                )
                            }
                        }

                        // Foreign Currency Exchange Rate & Converted Base Currency Display
                        if (!selectedCurrency.equals(baseCurrency, ignoreCase = true)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = exchangeRateText,
                                        onValueChange = { exchangeRateText = it },
                                        isError = exchangeRateText.isNotEmpty() && !isRateValid,
                                        supportingText = {
                                            if (exchangeRateText.isNotEmpty() && rateValidation is RateValidationResult.Error) {
                                                Text(rateValidation.message, color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        label = {
                                            Text(
                                                AppStrings.exchangeRateHelpText(selectedCurrency, baseCurrency, lang),
                                                color = Color(0xFFDC2626)
                                            )
                                        },
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFFDC2626),
                                            unfocusedTextColor = Color(0xFFDC2626),
                                            focusedBorderColor = Color(0xFFDC2626),
                                            unfocusedBorderColor = Color(0xFFF87171),
                                            focusedLabelColor = Color(0xFFDC2626),
                                            unfocusedLabelColor = Color(0xFFDC2626),
                                            cursorColor = Color(0xFFDC2626)
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Next
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(AppStrings.convertedBaseEquivalentLabel(baseCurrency, lang), fontSize = 12.sp, color = Color(0xFF475569))
                                        Text(
                                            text = NumberFormatUtils.formatCurrency(convertedTotalVnd, baseCurrency),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldPrimary
                                        )
                                    }
                                }
                            }
                        } else if (parsedAmount > 0) {
                            Text(
                                text = AppStrings.inWordsLabel(NumberFormatUtils.formatVnd(parsedAmount), lang),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldPrimary
                            )
                        }
                    }
                }

                // 3. THỜI GIAN CHI (EXPENSE DATE & TIME)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.section3ExpenseTime(lang),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            Text(
                                text = AppStrings.manualOrCalendarHint(lang),
                                fontSize = 11.sp,
                                color = IndigoSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Text Input & Calendar Picker Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = dateInputText,
                                onValueChange = { input ->
                                    dateInputText = input
                                    try {
                                        val parsed = dateTimeFormatter.parse(input.trim())
                                        if (parsed != null) {
                                            expenseTimestamp = parsed.time
                                            dateInputError = false
                                        } else {
                                            dateInputError = true
                                        }
                                    } catch (e: Exception) {
                                        // Try date-only format dd/MM/yyyy
                                        try {
                                            val dateOnlyFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                            val parsedDate = dateOnlyFormat.parse(input.trim())
                                            if (parsedDate != null) {
                                                val cal = Calendar.getInstance().apply {
                                                    time = parsedDate
                                                    set(Calendar.HOUR_OF_DAY, 12)
                                                    set(Calendar.MINUTE, 0)
                                                }
                                                expenseTimestamp = cal.timeInMillis
                                                dateInputError = false
                                            } else {
                                                dateInputError = true
                                            }
                                        } catch (e2: Exception) {
                                            dateInputError = true
                                        }
                                    }
                                },
                                label = { Text(AppStrings.dateTimeFormatLabel(lang)) },
                                placeholder = { Text("VD: 21/08/2026 14:30") },
                                isError = dateInputError && dateInputText.isNotBlank(),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.EditCalendar,
                                        contentDescription = null,
                                        tint = if (dateInputError) DangerRed else IndigoSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("expense_date_input")
                            )

                            // Calendar Picker Trigger Button
                            Button(
                                onClick = { openDateTimePicker() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("open_calendar_picker_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarMonth,
                                        contentDescription = AppStrings.pickCalendarCd(lang),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = AppStrings.calendarTableBtn(lang),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (dateInputError && dateInputText.isNotBlank()) {
                            Text(
                                text = AppStrings.invalidDateTimeFormatError(lang),
                                fontSize = 11.sp,
                                color = DangerRed
                            )
                        }

                        // Quick Date/Time Selection Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SuggestionChip(
                                onClick = { updateTimestamp(System.currentTimeMillis()) },
                                label = { Text(AppStrings.timeChipNow(lang), fontSize = 11.sp) }
                            )

                            SuggestionChip(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        timeInMillis = expenseTimestamp - (24 * 3600 * 1000L)
                                    }
                                    updateTimestamp(cal.timeInMillis)
                                },
                                label = { Text(AppStrings.timeChipYesterday(lang), fontSize = 11.sp) }
                            )

                            SuggestionChip(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, 8)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    updateTimestamp(cal.timeInMillis)
                                },
                                label = { Text(AppStrings.timeChipMorning(lang), fontSize = 11.sp) }
                            )

                            SuggestionChip(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, 12)
                                        set(Calendar.MINUTE, 30)
                                        set(Calendar.SECOND, 0)
                                    }
                                    updateTimestamp(cal.timeInMillis)
                                },
                                label = { Text(AppStrings.timeChipNoon(lang), fontSize = 11.sp) }
                            )

                            SuggestionChip(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.HOUR_OF_DAY, 19)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                    }
                                    updateTimestamp(cal.timeInMillis)
                                },
                                label = { Text(AppStrings.timeChipEvening(lang), fontSize = 11.sp) }
                            )

                            SuggestionChip(
                                onClick = {
                                    val cal = Calendar.getInstance().apply {
                                        timeInMillis = expenseTimestamp + (24 * 3600 * 1000L)
                                    }
                                    updateTimestamp(cal.timeInMillis)
                                },
                                label = { Text(AppStrings.timeChipPlusOneDay(lang), fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // 4. NGƯỜI XUẤT TIỀN (PAYER SELECTION)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = AppStrings.section4PayerSource(lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = payerType == "MEMBER",
                                onClick = { payerType = "MEMBER" },
                                label = { Text(AppStrings.memberPaidInAdvanceTab(lang), fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Person, contentDescription = AppStrings.memberPaidInAdvanceTab(lang), modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = payerType == "FUND",
                                onClick = { payerType = "FUND" },
                                label = { Text(AppStrings.groupFundTab(lang), fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Savings, contentDescription = AppStrings.groupFundTab(lang), modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (payerType == "MEMBER") {
                            val selectedMember = members.find { it.id == selectedPayerMemberId }

                            // Interactive Payer Card with Full Dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { payerDropdownExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = Color(0xFFF8FAFC)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("payer_member_selector")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = IndigoSecondary,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = (selectedMember?.name?.take(1) ?: "N").uppercase(),
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = selectedMember?.name ?: AppStrings.noPayerSelected(lang),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1E293B)
                                                )
                                                Text(
                                                    text = when (selectedMember?.role) {
                                                        "ADMIN" -> AppStrings.roleLeaderBadge(lang)
                                                        "TREASURER" -> AppStrings.roleTreasurerBadge(lang)
                                                        else -> AppStrings.roleMemberBadge(lang)
                                                    },
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = Icons.Filled.ArrowDropDown,
                                            contentDescription = AppStrings.selectPayerCd(lang),
                                            tint = Color(0xFF64748B)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = payerDropdownExpanded,
                                    onDismissRequest = { payerDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Text(
                                        text = AppStrings.selectPayingMemberHeader(lang),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64748B),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                    members.forEach { member ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = member.name,
                                                            fontWeight = if (member.id == selectedPayerMemberId) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 14.sp
                                                        )
                                                        Text(
                                                            text = when (member.role) {
                                                                "ADMIN" -> AppStrings.roleLeaderBadge(lang)
                                                                "TREASURER" -> AppStrings.roleTreasurerBadge(lang)
                                                                else -> AppStrings.roleMemberBadge(lang)
                                                            },
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF64748B)
                                                        )
                                                    }
                                                    if (member.id == selectedPayerMemberId) {
                                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldPrimary)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedPayerMemberId = member.id
                                                payerDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Quick horizontal list of members for 1-click selection
                            if (members.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    members.forEach { m ->
                                        FilterChip(
                                            selected = selectedPayerMemberId == m.id,
                                            onClick = { selectedPayerMemberId = m.id },
                                            label = { Text(m.name, fontSize = 12.sp) },
                                            leadingIcon = if (selectedPayerMemberId == m.id) {
                                                { Icon(Icons.Filled.Check, contentDescription = m.name, modifier = Modifier.size(14.dp)) }
                                            } else null
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = AmberTertiary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = AppStrings.fundDirectDeductNotice(lang),
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. PHÂN BỔ CHI PHÍ (SPLIT STRATEGY)
                item {
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = AppStrings.section5SplitMethod(lang),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )

                    val splitModes = listOf(
                        "EQUAL" to AppStrings.splitTabEqual(lang),
                        "CUSTOM_PARTICIPANT" to AppStrings.splitTabCustomRoster(lang),
                        "RATIO" to AppStrings.splitTabRatio(lang),
                        "CUSTOM_AMOUNT" to AppStrings.splitTabCustomAmount(lang)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        splitModes.forEach { (modeKey, modeTitle) ->
                            FilterChip(
                                selected = splitType == modeKey,
                                onClick = { splitType = modeKey },
                                label = { Text(modeTitle, fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Detail config for each split mode
                item {
                    when (splitType) {
                        "CUSTOM_PARTICIPANT" -> {
                            Text(AppStrings.selectParticipantsPrompt(lang), fontSize = 11.sp, color = Color(0xFF64748B))
                            Column {
                                members.forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val current = selectedParticipantIds[m.id] ?: true
                                                selectedParticipantIds[m.id] = !current
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedParticipantIds[m.id] == true,
                                            onCheckedChange = { selectedParticipantIds[m.id] = it }
                                        )
                                        Text(m.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                        "RATIO" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = AppStrings.enterRatioPrompt(lang),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155)
                                    )

                                    // Quick Helper Actions
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val activeMembers = members.filter { it.isActive }
                                                val count = activeMembers.size.coerceAtLeast(1)
                                                val baseInt = 1000 / count
                                                val remainderInt = 1000 % count
                                                activeMembers.forEachIndexed { index, m ->
                                                    val ratioTenths = baseInt + (if (index < remainderInt) 1 else 0)
                                                    memberRatios[m.id] = String.format(Locale.US, "%.1f", ratioTenths / 10.0)
                                                }
                                                members.filter { !it.isActive }.forEach { memberRatios[it.id] = "0.0" }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(AppStrings.splitEquallyBtn(lang), fontSize = 11.sp)
                                        }

                                        Button(
                                            onClick = {
                                                // Tự động bù chênh lệch vào người trả tiền hoặc thành viên đầu tiên để tổng chuẩn xác 100.0%
                                                val targetMemberId = if (selectedPayerMemberId.isNotBlank()) selectedPayerMemberId else members.firstOrNull { it.isActive }?.id ?: members.firstOrNull()?.id
                                                if (targetMemberId != null) {
                                                    val otherSum = members.filter { it.id != targetMemberId }.sumOf { memberRatios[it.id]?.toDoubleOrNull() ?: 0.0 }
                                                    val balanced = (100.0 - otherSum).coerceAtLeast(0.0)
                                                    memberRatios[targetMemberId] = String.format(Locale.US, "%.1f", balanced)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = IndigoSecondary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(AppStrings.balance100PercentBtn(lang), fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Status Badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isRatioValid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                    border = BorderStroke(1.dp, if (isRatioValid) Color(0xFF86EFAC) else Color(0xFFFCA5A5)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isRatioValid) AppStrings.totalRatioValidExact(lang) else AppStrings.totalRatioMustBe100(String.format(Locale.US, "%.1f", totalRatio), lang),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isRatioValid) Color(0xFF15803D) else Color(0xFFB91C1C)
                                        )
                                        if (!isRatioValid) {
                                            val diffPercent = 100.0 - totalRatio
                                            Text(
                                                text = if (diffPercent > 0) AppStrings.ratioShortage(String.format(Locale.US, "%.1f", diffPercent), lang) else AppStrings.ratioSurplus(String.format(Locale.US, "%.1f", -diffPercent), lang),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB91C1C)
                                            )
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    members.forEach { m ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(m.name, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
                                            OutlinedTextField(
                                                value = memberRatios[m.id] ?: "0",
                                                onValueChange = { memberRatios[m.id] = it },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                trailingIcon = { Text("%", fontSize = 12.sp) },
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .height(50.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "CUSTOM_AMOUNT" -> {
                            Text(AppStrings.customAmountPrompt(baseCurrency, lang), fontSize = 11.sp, color = Color(0xFF64748B))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                members.forEach { m ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m.name, fontSize = 13.sp, modifier = Modifier.weight(1.2f))
                                        OutlinedTextField(
                                            value = memberCustomAmounts[m.id] ?: "0",
                                            onValueChange = { if (it.all { c -> c.isDigit() }) memberCustomAmounts[m.id] = it },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier
                                                .width(140.dp)
                                                .height(50.dp)
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Text(
                                AppStrings.equalSplitExplanation(lang),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                // Real-time "Xem trước phân bổ" (Live Allocation Preview)
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAllocationExact) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    AppStrings.previewAllocationHeader(lang),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAllocationExact) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                                Text(
                                    text = if (isAllocationExact) AppStrings.allocationValidBadge(lang) else if (splitType == "RATIO" && !isRatioValid) AppStrings.allocationRatioNot100Badge(lang) else AppStrings.allocationDiffBadge(NumberFormatUtils.formatVnd(diff), lang),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAllocationExact) Color(0xFF15803D) else Color(0xFFDC2626)
                                )
                            }
                            if (splitType == "RATIO" && !isRatioValid) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = AppStrings.ratioWarningNotice(String.format(Locale.US, "%.1f", totalRatio), lang),
                                    fontSize = 11.sp,
                                    color = DangerRed
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            calculatedSplits.forEach { (mId, share) ->
                                val mName = members.find { it.id == mId }?.name ?: mId
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(mName, fontSize = 12.sp, color = Color(0xFF334155))
                                    Text(NumberFormatUtils.formatVnd(share), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFCBD5E1))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(AppStrings.totalAllocatedLabel(lang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${NumberFormatUtils.formatVnd(currentAllocatedSum)} / ${NumberFormatUtils.formatVnd(convertedTotalVnd)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAllocationExact) EmeraldPrimary else DangerRed
                                )
                            }
                        }
                    }
                }

                // 6. NỘI DUNG CHI TIẾT & GHI CHÚ
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = AppStrings.section6NotesAndDetails(lang),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text(AppStrings.notesAndDetailsPlaceholder(lang)) },
                            minLines = 2,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSave(
                        title,
                        category,
                        payerType,
                        if (payerType == "MEMBER") selectedPayerMemberId else null,
                        parsedAmount,
                        selectedCurrency,
                        parsedRate,
                        splitType,
                        calculatedSplits,
                        note,
                        expenseTimestamp
                    )
                    onDismiss()
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("save_expense_button")
            ) {
                Icon(Icons.Filled.Check, contentDescription = if (isEditMode) AppStrings.saveChangesBtn(lang) else AppStrings.saveExpenseBtn(lang), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEditMode) AppStrings.saveChangesBtn(lang) else AppStrings.saveExpenseBtn(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text(AppStrings.cancelExpenseDialogBtn(lang))
            }
        }
    )
}


// =========================================================================
// AppStrings Localization Extensions for AddExpenseDialog
// =========================================================================

fun AppStrings.editExpenseDialogTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉnh Sửa Khoản Chi" else "Edit Expense"

fun AppStrings.addExpenseDialogTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thêm Khoản Chi Mới" else "Add New Expense"

fun AppStrings.editExpenseDialogSubtitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Cập nhật chi tiết & phân bổ lại người tham gia"
    else "Update details & re-allocate participants"

fun AppStrings.addExpenseDialogSubtitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhập chi tiết thu chi và phân bổ đoàn"
    else "Record trip expense and allocate among members"

fun AppStrings.closeExpenseDialogCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đóng" else "Close"

fun AppStrings.section1ExpenseNameCategory(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "1. Tên & Danh mục khoản chi" else "1. Expense Name & Category"

fun AppStrings.expenseNamePlaceholder(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tên khoản chi * (VD: Ăn trưa hải sản, Vé cáp treo...)"
    else "Expense title * (e.g. Seafood Lunch, Cable Car...)"

fun AppStrings.section2AmountAndCurrency(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "2. Số tiền & Loại tiền tệ" else "2. Amount & Currency"

fun AppStrings.amountFieldLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Số tiền *" else "Amount *"

fun AppStrings.clearExpenseInputCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa" else "Clear"

fun AppStrings.exchangeRateHelpText(selectedCurr: String, baseCurr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tỷ giá quy đổi (1 $selectedCurr = ? $baseCurr)"
    else "Exchange rate (1 $selectedCurr = ? $baseCurr)"

fun AppStrings.convertedBaseEquivalentLabel(baseCurr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quy đổi $baseCurr tương đương:"
    else "Converted equivalent in $baseCurr:"

fun AppStrings.inWordsLabel(words: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bằng chữ: $words" else "In words: $words"

fun AppStrings.section3ExpenseTime(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "3. Thời gian chi" else "3. Expense Date & Time"

fun AppStrings.manualOrCalendarHint(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhập tay hoặc bấm lịch" else "Manual entry or pick calendar"

fun AppStrings.dateTimeFormatLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Ngày & Giờ (dd/MM/yyyy HH:mm)" else "Date & Time (dd/MM/yyyy HH:mm)"

fun AppStrings.pickCalendarCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chọn lịch" else "Pick calendar"

fun AppStrings.calendarTableBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bảng lịch" else "Calendar"

fun AppStrings.invalidDateTimeFormatError(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Định dạng không hợp lệ. Vui lòng nhập: ngày/tháng/năm giờ:phút (VD: 21/08/2026 15:30) hoặc bấm nút Bảng lịch."
    else "Invalid format. Please enter dd/MM/yyyy HH:mm (e.g. 21/08/2026 15:30) or use Calendar picker."

fun AppStrings.timeChipNow(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "⚡ Bây giờ" else "⚡ Now"

fun AppStrings.timeChipYesterday(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hôm qua" else "Yesterday"

fun AppStrings.timeChipMorning(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Sáng nay (08:00)" else "This Morning (08:00)"

fun AppStrings.timeChipNoon(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Trưa nay (12:30)" else "Lunch Time (12:30)"

fun AppStrings.timeChipEvening(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tối nay (19:00)" else "Evening (19:00)"

fun AppStrings.timeChipPlusOneDay(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "+1 ngày" else "+1 Day"

fun AppStrings.section4PayerSource(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "4. Người xuất tiền (Nguồn thanh toán) *"
    else "4. Payer (Funding Source) *"

fun AppStrings.memberPaidInAdvanceTab(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên chi trước" else "Member Paid"

fun AppStrings.groupFundTab(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Quỹ chung của đoàn" else "Group Fund"

fun AppStrings.noPayerSelected(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chưa chọn người chi" else "No payer selected"

fun AppStrings.roleLeaderBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Trưởng đoàn" else "Leader"

fun AppStrings.roleTreasurerBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thủ quỹ đoàn" else "Treasurer"

fun AppStrings.roleMemberBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thành viên" else "Member"

fun AppStrings.selectPayerCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chọn người chi" else "Select payer"

fun AppStrings.selectPayingMemberHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chọn thành viên xuất tiền:" else "Select paying member:"

fun AppStrings.fundDirectDeductNotice(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Khoản chi này sẽ được trừ trực tiếp vào Quỹ chung của đoàn."
    else "This expense will be deducted directly from the Group Fund."

fun AppStrings.section5SplitMethod(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "5. Phương thức phân bổ chi phí (Split)"
    else "5. Expense Allocation Method (Split)"

fun AppStrings.splitTabEqual(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia đều" else "Equal"

fun AppStrings.splitTabCustomRoster(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chọn người" else "By Members"

fun AppStrings.splitTabRatio(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tỷ lệ %" else "Percentage %"

fun AppStrings.splitTabCustomAmount(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tùy nhập" else "Exact Amount"

fun AppStrings.selectParticipantsPrompt(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tích chọn những người cùng chịu chi phí khoản này:"
    else "Select members sharing this expense:"

fun AppStrings.enterRatioPrompt(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhập tỷ lệ % chia từng người:" else "Enter percentage share for each member:"

fun AppStrings.splitEquallyBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chia đều" else "Equalize"

fun AppStrings.balance100PercentBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "⚡ Cân bằng 100%" else "⚡ Balance 100%"

fun AppStrings.totalRatioValidExact(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "✓ Tổng tỷ lệ: 100.0% (Chuẩn xác)" else "✓ Total: 100.0% (Valid)"

fun AppStrings.totalRatioMustBe100(currentRatioStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "⚠️ Tổng tỷ lệ: $currentRatioStr% (Bắt buộc phải đúng 100.0%)"
    else "⚠️ Total ratio: $currentRatioStr% (Must equal 100.0%)"

fun AppStrings.ratioShortage(diffStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thiếu $diffStr%" else "Remaining: $diffStr%"

fun AppStrings.ratioSurplus(diffStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Thừa $diffStr%" else "Over: $diffStr%"

fun AppStrings.customAmountPrompt(baseCurr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Nhập số tiền $baseCurr cụ thể cho từng thành viên:"
    else "Enter exact $baseCurr amount for each member:"

fun AppStrings.equalSplitExplanation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Toàn bộ thành viên sẽ được chia đều số tiền. Phần dư chia tự động bù người chi."
    else "Amount is split equally among all members. Any division remainder is rounded to the payer."

fun AppStrings.previewAllocationHeader(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "XEM TRƯỚC PHÂN BỔ (PREVIEW)" else "ALLOCATION PREVIEW"

fun AppStrings.allocationValidBadge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "✓ Hợp lệ" else "✓ Valid"

fun AppStrings.allocationRatioNot100Badge(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "⚠ Tổng % khác 100%" else "⚠ Total % ≠ 100%"

fun AppStrings.allocationDiffBadge(diffFormatted: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "⚠ Lệch $diffFormatted" else "⚠ Difference: $diffFormatted"

fun AppStrings.ratioWarningNotice(ratioStr: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu ý: Tổng tỷ lệ hiện tại là $ratioStr%. Vui lòng bấm 'Cân bằng 100%' hoặc chỉnh sửa để tổng đúng 100.0% trước khi lưu."
    else "Note: Current total ratio is $ratioStr%. Please tap 'Balance 100%' or adjust so that total equals 100.0% before saving."

fun AppStrings.totalAllocatedLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tổng phân bổ:" else "Total Allocated:"

fun AppStrings.section6NotesAndDetails(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "6. Nội dung chi tiết khoản chi & Ghi chú"
    else "6. Expense Details & Receipt Notes"

fun AppStrings.notesAndDetailsPlaceholder(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chi tiết các món ăn, địa điểm, số hóa đơn đỏ, ghi chú..."
    else "Itemized dishes, location, tax receipt number, memo..."

fun AppStrings.saveChangesBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu Thay Đổi" else "Save Changes"

fun AppStrings.saveExpenseBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu Khoản Chi" else "Save Expense"

fun AppStrings.cancelExpenseDialogBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hủy" else "Cancel"
