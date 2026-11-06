package com.hiepnguyen.tripfinance.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.hiepnguyen.tripfinance.data.entity.TripEntity
import com.hiepnguyen.tripfinance.ui.locale.AppLanguage
import com.hiepnguyen.tripfinance.ui.locale.AppStrings
import com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage
import com.hiepnguyen.tripfinance.ui.theme.EmeraldPrimary

@Composable
fun CreateTripDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        joinCode: String,
        startDate: Long,
        endDate: Long,
        adminName: String,
        adminBankName: String?,
        adminBankAccount: String?,
        adminBankHolder: String?,
        baseCurrency: String
    ) -> Unit
) {
    val lang = LocalAppLanguage.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }
    var joinCodeInput by remember { mutableStateOf(com.hiepnguyen.tripfinance.domain.model.TripCodeGenerator.generateCode("TRIP-")) }
    var selectedBaseCurrency by remember { mutableStateOf("VND") }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    BackHandler { safeDismiss() }

    val isValid = title.isNotBlank() && adminName.isNotBlank()

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.createTripDialogTitle(lang), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(AppStrings.tripTitleInputLabel(lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_trip_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(AppStrings.tripDescriptionInputLabel(lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                // Base Currency Picker
                Column {
                    Text(
                        text = AppStrings.baseCurrencyPickerLabel(lang),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { currencyDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("create_trip_currency_selector")
                        ) {
                            val currInfo = com.hiepnguyen.tripfinance.domain.model.SupportedCurrencies.LIST.find { it.code == selectedBaseCurrency }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${currInfo?.code ?: selectedBaseCurrency} (${currInfo?.symbol ?: ""}) - ${currInfo?.nameVi ?: ""}",
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    fontSize = 13.5.sp
                                )
                                Text(AppStrings.changeCurrencyDropDown(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        DropdownMenu(
                            expanded = currencyDropdownExpanded,
                            onDismissRequest = { currencyDropdownExpanded = false }
                        ) {
                            com.hiepnguyen.tripfinance.domain.model.SupportedCurrencies.LIST.forEach { curr ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${curr.code} (${curr.symbol})", fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(curr.nameVi, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    },
                                    onClick = {
                                        selectedBaseCurrency = curr.code
                                        currencyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = AppStrings.baseCurrencyExplanation(lang),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                OutlinedTextField(
                    value = adminName,
                    onValueChange = { adminName = it },
                    label = { Text(AppStrings.adminNameInputLabel(lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = joinCodeInput,
                    onValueChange = { input ->
                        val filtered = input.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                        if (filtered.length <= 16) joinCodeInput = filtered
                    },
                    label = { Text(AppStrings.tripJoinCodeInputLabel(lang)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            joinCodeInput = com.hiepnguyen.tripfinance.domain.model.TripCodeGenerator.generateCode("TRIP-")
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = AppStrings.generateNewCodeCd(lang), tint = EmeraldPrimary)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    val now = System.currentTimeMillis()
                    val finalCode = joinCodeInput.trim().ifEmpty {
                        com.hiepnguyen.tripfinance.domain.model.TripCodeGenerator.generateCode("TRIP-")
                    }
                    onConfirm(
                        title.trim(),
                        description.trim(),
                        finalCode,
                        now,
                        now + 86400000L * 5,
                        adminName.trim(),
                        null,
                        null,
                        null,
                        selectedBaseCurrency
                    )
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("submit_create_trip_button")
            ) {
                Text(AppStrings.createTripSubmitBtn(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text(AppStrings.cancelTripBtn(lang)) }
        }
    )
}

@Composable
fun EditTripDialog(
    trip: TripEntity,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, startDate: Long, endDate: Long, baseCurrency: String) -> Unit
) {
    val lang = LocalAppLanguage.current
    var title by remember { mutableStateOf(trip.title) }
    var description by remember { mutableStateOf(trip.description) }
    var baseCurrency by remember { mutableStateOf(trip.baseCurrency) }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val safeDismiss = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    BackHandler { safeDismiss() }

    val isValid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(decorFitsSystemWindows = true),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.editTripDialogTitle(lang), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(AppStrings.editTripTitleLabel(lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("edit_trip_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(AppStrings.editTripDescriptionLabel(lang)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                // Base Currency Picker in Edit Dialog
                Column {
                    Text(
                        text = AppStrings.editTripBaseCurrencyLabel(lang),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { currencyDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("edit_trip_currency_selector")
                        ) {
                            val currInfo = com.hiepnguyen.tripfinance.domain.model.SupportedCurrencies.LIST.find { it.code == baseCurrency }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${currInfo?.code ?: baseCurrency} (${currInfo?.symbol ?: ""}) - ${currInfo?.nameVi ?: ""}",
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary,
                                    fontSize = 13.5.sp
                                )
                                Text(AppStrings.changeCurrencyDropDown(lang), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }

                        DropdownMenu(
                            expanded = currencyDropdownExpanded,
                            onDismissRequest = { currencyDropdownExpanded = false }
                        ) {
                            com.hiepnguyen.tripfinance.domain.model.SupportedCurrencies.LIST.forEach { curr ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("${curr.code} (${curr.symbol})", fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(curr.nameVi, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                        }
                                    },
                                    onClick = {
                                        baseCurrency = curr.code
                                        currencyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        text = AppStrings.editTripBaseCurrencyExplanation(lang),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onConfirm(title.trim(), description.trim(), trip.startDate, trip.endDate, baseCurrency)
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("submit_edit_trip_button")
            ) {
                Text(AppStrings.saveTripChangesBtn(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text(AppStrings.cancelTripBtn(lang)) }
        }
    )
}

@Composable
fun DeleteTripDialog(
    tripTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val lang = LocalAppLanguage.current
    BackHandler { onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        title = {
            Text(AppStrings.deleteTripDialogTitle(lang), fontWeight = FontWeight.Bold)
        },
        text = {
            Text(AppStrings.deleteTripDialogPrompt(tripTitle, lang))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_trip_button")
            ) {
                Text(AppStrings.deleteTripPermanentlyBtn(lang))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(AppStrings.cancelTripBtn(lang))
            }
        }
    )
}

// --- AppStrings Localized Extensions for TripDialogs ---

fun AppStrings.createTripDialogTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tạo Chuyến Đi / Đoàn Mới" else "Create New Trip / Group"

fun AppStrings.tripTitleInputLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tên đoàn (VD: Du lịch Đà Nẵng 2026) *" else "Group/Trip Name (e.g. Da Nang Trip 2026) *"

fun AppStrings.tripDescriptionInputLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mô tả / Mục đích chuyến đi" else "Description / Trip Purpose"

fun AppStrings.baseCurrencyPickerLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Loại tiền tệ gốc của đoàn (Base Currency) *" else "Trip Base Currency *"

fun AppStrings.changeCurrencyDropDown(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đổi ▾" else "Change ▾"

fun AppStrings.baseCurrencyExplanation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mọi chi tiêu ngoại tệ và quyết toán công nợ sẽ được quy đổi về loại tiền tệ gốc này."
    else "All foreign currency expenses and debt settlements will be converted to this base currency."

fun AppStrings.adminNameInputLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Họ tên bạn (Trưởng đoàn / Người ghi chép) *" else "Your Name (Leader / Bookkeeper) *"

fun AppStrings.tripJoinCodeInputLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mã đoàn (Dùng để chia sẻ / nhập đoàn)" else "Trip Code (For sharing / importing)"

fun AppStrings.generateNewCodeCd(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tạo mã ngẫu nhiên mới" else "Generate random code"

fun AppStrings.createTripSubmitBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tạo đoàn" else "Create Trip"

fun AppStrings.cancelTripBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Hủy" else "Cancel"

fun AppStrings.editTripDialogTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Chỉnh Sửa Thông Tin Đoàn" else "Edit Trip Information"

fun AppStrings.editTripTitleLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Tên đoàn công tác *" else "Group/Trip Name *"

fun AppStrings.editTripDescriptionLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Mô tả / Mục đích" else "Description / Purpose"

fun AppStrings.editTripBaseCurrencyLabel(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Loại tiền tệ gốc (Base Currency) *" else "Base Currency *"

fun AppStrings.editTripBaseCurrencyExplanation(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Đổi loại tiền tệ gốc để quy đổi các khoản chi tiêu và báo cáo quyết toán."
    else "Change base currency to convert expenses and final settlement reports."

fun AppStrings.saveTripChangesBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Lưu thay đổi" else "Save Changes"

fun AppStrings.deleteTripDialogTitle(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xác Nhận Xóa Đoàn" else "Confirm Delete Trip"

fun AppStrings.deleteTripDialogPrompt(tripTitle: String, lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Bạn có chắc chắn muốn xóa đoàn '$tripTitle'? Toàn bộ chi tiêu, nộp quỹ, thành viên và dữ liệu thuộc đoàn này sẽ bị xóa vĩnh viễn khỏi thiết bị."
    else "Are you sure you want to delete trip '$tripTitle'? All expenses, fund contributions, members, and data for this trip will be permanently deleted from this device."

fun AppStrings.deleteTripPermanentlyBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa vĩnh viễn" else "Delete Permanently"
