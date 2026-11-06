import re

path = "app/src/main/java/com/hiepnguyen/tripfinance/ui/screens/TripDialogs.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Add imports
content = content.replace(
    "import com.hiepnguyen.tripfinance.data.entity.TripEntity\nimport com.hiepnguyen.tripfinance.ui.theme.EmeraldPrimary",
    "import com.hiepnguyen.tripfinance.data.entity.TripEntity\nimport com.hiepnguyen.tripfinance.ui.locale.AppLanguage\nimport com.hiepnguyen.tripfinance.ui.locale.AppStrings\nimport com.hiepnguyen.tripfinance.ui.locale.LocalAppLanguage\nimport com.hiepnguyen.tripfinance.ui.theme.EmeraldPrimary"
)

# lang in CreateTripDialog
content = content.replace(
    "    var title by remember { mutableStateOf(\"\") }",
    "    val lang = LocalAppLanguage.current\n    var title by remember { mutableStateOf(\"\") }"
)

# strings in CreateTripDialog
content = content.replace('"Tạo Chuyến Đi / Đoàn Mới"', "AppStrings.createTripDialogTitle(lang)")
content = content.replace('"Tên đoàn (VD: Du lịch Đà Nẵng 2026) *"', "AppStrings.tripTitleInputLabel(lang)")
content = content.replace('"Mô tả / Mục đích chuyến đi"', "AppStrings.tripDescriptionInputLabel(lang)")
content = content.replace('"Loại tiền tệ gốc của đoàn (Base Currency) *"', "AppStrings.baseCurrencyPickerLabel(lang)")
content = content.replace('"Đổi ▾"', "AppStrings.changeCurrencyDropDown(lang)")
content = content.replace('"Mọi chi tiêu ngoại tệ và quyết toán công nợ sẽ được quy đổi về loại tiền tệ gốc này."', "AppStrings.baseCurrencyExplanation(lang)")
content = content.replace('"Họ tên bạn (Trưởng đoàn / Người ghi chép) *"', "AppStrings.adminNameInputLabel(lang)")
content = content.replace('"Mã đoàn (Dùng để chia sẻ / nhập đoàn)"', "AppStrings.tripJoinCodeInputLabel(lang)")
content = content.replace('contentDescription = "Tạo mã ngẫu nhiên mới"', "contentDescription = AppStrings.generateNewCodeCd(lang)")
content = content.replace('Text("Tạo đoàn")', "Text(AppStrings.createTripSubmitBtn(lang))")
content = content.replace('TextButton(onClick = safeDismiss) { Text("Hủy") }', "TextButton(onClick = safeDismiss) { Text(AppStrings.cancelTripBtn(lang)) }")

# In EditTripDialog
content = content.replace(
    "    var title by remember { mutableStateOf(trip.title) }",
    "    val lang = LocalAppLanguage.current\n    var title by remember { mutableStateOf(trip.title) }"
)
content = content.replace('"Chỉnh Sửa Thông Tin Đoàn"', "AppStrings.editTripDialogTitle(lang)")
content = content.replace('"Tên đoàn công tác *"', "AppStrings.editTripTitleLabel(lang)")
content = content.replace('"Mô tả / Mục đích"', "AppStrings.editTripDescriptionLabel(lang)")
content = content.replace('"Loại tiền tệ gốc (Base Currency) *"', "AppStrings.editTripBaseCurrencyLabel(lang)")
content = content.replace('"Đổi loại tiền tệ gốc để quy đổi các khoản chi tiêu và báo cáo quyết toán."', "AppStrings.editTripBaseCurrencyExplanation(lang)")
content = content.replace('Text("Lưu thay đổi")', "Text(AppStrings.saveTripChangesBtn(lang))")

# In DeleteTripDialog
content = content.replace(
    "fun DeleteTripDialog(\n    tripTitle: String,\n    onDismiss: () -> Unit,\n    onConfirm: () -> Unit\n) {",
    "fun DeleteTripDialog(\n    tripTitle: String,\n    onDismiss: () -> Unit,\n    onConfirm: () -> Unit\n) {\n    val lang = LocalAppLanguage.current"
)
content = content.replace('"Xác Nhận Xóa Đoàn"', "AppStrings.deleteTripDialogTitle(lang)")
content = content.replace(
    '"Bạn có chắc chắn muốn xóa đoàn \'$tripTitle\'? Toàn bộ chi tiêu, nộp quỹ, thành viên và dữ liệu thuộc đoàn này sẽ bị xóa vĩnh viễn khỏi thiết bị."',
    "AppStrings.deleteTripDialogPrompt(tripTitle, lang)"
)
content = content.replace('Text("Xóa vĩnh viễn")', "Text(AppStrings.deleteTripPermanentlyBtn(lang))")
content = content.replace('TextButton(onClick = onDismiss) {\n                Text("Hủy")\n            }', 'TextButton(onClick = onDismiss) {\n                Text(AppStrings.cancelTripBtn(lang))\n            }')

extensions = """

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
    if (lang == AppLanguage.VI) "Bạn có chắc chắn muốn xóa đoàn \'$tripTitle\'? Toàn bộ chi tiêu, nộp quỹ, thành viên và dữ liệu thuộc đoàn này sẽ bị xóa vĩnh viễn khỏi thiết bị."
    else "Are you sure you want to delete trip \'$tripTitle\'? All expenses, fund contributions, members, and data for this trip will be permanently deleted from this device."

fun AppStrings.deleteTripPermanentlyBtn(lang: AppLanguage) =
    if (lang == AppLanguage.VI) "Xóa vĩnh viễn" else "Delete Permanently"
"""

content = content.rstrip() + extensions

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("TripDialogs.kt processed successfully")
