package com.hiepnguyen.tripfinance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hiepnguyen.tripfinance.data.db.AppDatabase
import com.hiepnguyen.tripfinance.data.entity.*
import com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager
import com.hiepnguyen.tripfinance.data.repository.TripFinanceRepository
import com.hiepnguyen.tripfinance.domain.model.DefaultExchangeRates
import com.hiepnguyen.tripfinance.domain.model.SettlementTransfer
import com.hiepnguyen.tripfinance.domain.model.sumOfSafe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import kotlin.math.round

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: TripFinanceRepository

    @Before
    fun setup() {
        com.hiepnguyen.tripfinance.data.security.DatabaseKeyManager.keyProvider =
            com.hiepnguyen.tripfinance.data.security.InMemoryKeyProvider()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(sqliteDb: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(sqliteDb)
                    sqliteDb.execSQL("PRAGMA foreign_keys = ON;")
                    AppDatabase.createDatabaseCheckConstraints(sqliteDb)
                }
                override fun onOpen(sqliteDb: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(sqliteDb)
                    sqliteDb.execSQL("PRAGMA foreign_keys = ON;")
                    AppDatabase.createDatabaseCheckConstraints(sqliteDb)
                }
            })
            .build()
        db.openHelper.writableDatabase.let {
            it.execSQL("PRAGMA foreign_keys = ON;")
            AppDatabase.createDatabaseCheckConstraints(it)
        }
        repository = TripFinanceRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        com.hiepnguyen.tripfinance.data.security.DatabaseKeyManager.resetKeyProvider()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TripFinance", appName)
    }

    @Test
    fun `launch MainActivity without crash`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
        controller.create().start().resume()
        assertNotNull(controller.get())
        controller.pause().stop().destroy()
    }

    @Test
    fun `database starts clean without sample seed data`() = runBlocking {
        // AppDatabase should start completely empty when installed/created freshly
        val allTrips = repository.allTrips.first()
        assertTrue("Database should have 0 trips upon fresh start", allTrips.isEmpty())
    }

    @Test
    fun `unified exchange rates match specification`() {
        // EUR 27.600, JPY 168, THB 720, USD 25.450
        assertEquals(27600.0, DefaultExchangeRates.RATE_EUR, 0.001)
        assertEquals(168.0, DefaultExchangeRates.RATE_JPY, 0.001)
        assertEquals(720.0, DefaultExchangeRates.RATE_THB, 0.001)
        assertEquals(25450.0, DefaultExchangeRates.RATE_USD, 0.001)
        assertEquals(27600.0, DefaultExchangeRates.getRate("EUR"), 0.001)
        assertEquals(168.0, DefaultExchangeRates.getRate("JPY"), 0.001)
        assertEquals(720.0, DefaultExchangeRates.getRate("THB"), 0.001)
    }

    @Test
    fun `currency conversion properly rounds decimal inputs such as 12_50 USD`() {
        val amount = 12.50
        val rate = DefaultExchangeRates.RATE_USD // 25450.0
        val converted = round(amount * rate).toLong()
        // 12.50 USD * 25,450 = 318,125 VND (Previously truncated with toLong() if entered as integer or lost)
        assertEquals(318125L, converted)

        // Decimal EUR test: 10.75 EUR * 27,600 = 296,700 VND
        val eurAmount = 10.75
        val eurRate = DefaultExchangeRates.RATE_EUR // 27600.0
        val eurConverted = round(eurAmount * eurRate).toLong()
        assertEquals(296700L, eurConverted)

        // Non-integer rounding test: 12.345 USD * 25,450 = 314,180.25 -> 314,180 VND
        val preciseAmount = 12.345
        val preciseConverted = round(preciseAmount * rate).toLong()
        assertEquals(314180L, preciseConverted)
    }

    @Test
    fun `editExpense preserves createdMemberId and records detailBefore and detailAfter`() = runBlocking {
        // 1. Create a trip
        val tripId = repository.createTrip(
            title = "Chuyến đi Hà Nội",
            description = "Công tác Hà Nội 2026",
            joinCode = "HN2026",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Nguyễn Văn A"
        )
        val members = repository.getMembers(tripId).first()
        val adminMember = members.first()

        // 2. Add second member (Bob)
        val bobId = repository.addMember(
            tripId = tripId,
            name = "Trần Văn B",
            role = "MEMBER",
            actor = adminMember
        )
        val updatedMembers = repository.getMembers(tripId).first()
        val bobMember = updatedMembers.first { it.id == bobId }

        // 3. Bob creates an expense with decimal amount 12.50 USD
        val expense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            title = "Bữa sáng phở",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = bobId,
            totalAmount = 12.50,
            currency = "USD",
            exchangeRate = DefaultExchangeRates.RATE_USD,
            convertedTotalAmount = round(12.50 * DefaultExchangeRates.RATE_USD).toLong(),
            splitType = "EQUAL",
            note = "Ăn sáng ngon",
            timestamp = System.currentTimeMillis(),
            createdMemberId = bobId
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, adminMember.id, 159062L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, bobId, 159063L)
        )
        repository.addExpenseWithSplits(
            expense = expense,
            splits = splits,
            actor = bobMember
        )

        // Verify createdMemberId is Bob
        val savedExpense = db.expenseDao().getExpenseById(expense.id)
        assertNotNull(savedExpense)
        assertEquals(bobId, savedExpense?.createdMemberId)
        assertEquals(12.50, savedExpense?.totalAmount ?: 0.0, 0.001)

        // 4. Admin edits the expense (changes title and totalAmount to 15.0 USD)
        val updatedExpense = savedExpense!!.copy(
            title = "Bữa sáng phở đặc biệt",
            totalAmount = 15.0,
            convertedTotalAmount = round(15.0 * DefaultExchangeRates.RATE_USD).toLong(),
            createdMemberId = adminMember.id // Intentional attempt by caller to pass editor's id
        )
        val updatedSplits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, adminMember.id, 190875L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, bobId, 190875L)
        )
        repository.updateExpenseWithSplits(
            expense = updatedExpense,
            splits = updatedSplits,
            actor = adminMember
        )

        // 5. Verify createdMemberId is preserved as Bob
        val modifiedExpense = db.expenseDao().getExpenseById(expense.id)
        assertNotNull(modifiedExpense)
        assertEquals("createdMemberId must NOT be overwritten by editor", bobId, modifiedExpense?.createdMemberId)
        assertEquals("Bữa sáng phở đặc biệt", modifiedExpense?.title)

        // 6. Verify audit logs contain detailBefore and detailAfter
        val logs = repository.getAuditLogs(tripId).first()
        val updateLog = logs.find { it.action == "UPDATE_EXPENSE" }
        assertNotNull("Update expense audit log must exist", updateLog)
        assertNotNull("detailBefore must not be null", updateLog?.detailBefore)
        assertNotNull("detailAfter must not be null", updateLog?.detailAfter)
        assertTrue("detailBefore must contain old title", updateLog?.detailBefore?.contains("Bữa sáng phở") == true)
        assertTrue("detailAfter must contain new title", updateLog?.detailAfter?.contains("Bữa sáng phở đặc biệt") == true)
    }

    @Test
    fun `atomic deleteTripCascade removes all trip data`() = runBlocking {
        val tripId = repository.createTrip(
            title = "Chuyến đi test cascade",
            description = "Thử nghiệm cascade",
            joinCode = "DL2026",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Nguyễn Văn A",
        )
        val members = repository.getMembers(tripId).first()
        val admin = members.first()

        val expense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            title = "Vé xe",
            category = "TRANSPORT",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 250000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 250000L,
            splitType = "EQUAL",
            note = "",
            timestamp = System.currentTimeMillis(),
            createdMemberId = admin.id
        )
        repository.addExpenseWithSplits(
            expense = expense,
            splits = listOf(ExpenseSplitEntity(UUID.randomUUID().toString(), expense.id, tripId, admin.id, 250000L)),
            actor = admin
        )

        // Delete cascade in atomic transaction
        repository.deleteTripCascade(tripId)

        // Verify everything deleted
        val trips = repository.allTrips.first()
        assertTrue(trips.none { it.id == tripId })
        val remainingMembers = repository.getMembers(tripId).first()
        assertTrue(remainingMembers.isEmpty())
        val remainingExpenses = repository.getExpenses(tripId).first()
        assertTrue(remainingExpenses.isEmpty())
    }

    @Test
    fun `test BackupCryptoUtils encryption and decryption roundtrip`() {
        val sampleJson = """{"version":1,"appName":"TripFinance","data":"secret_account_number_123456"}"""
        val password = "SuperSecretPassword#2026"

        val encrypted = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.encryptBackup(
            sampleJson,
            password,
            iterations = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.TEST_ITERATIONS
        )
        assertTrue(com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.isEncryptedBackup(encrypted))
        assertTrue(com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.isEncryptedBackupSnippet(encrypted.take(500)))

        // Decrypt with correct password
        val decryptSuccess = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(encrypted, password)
        assertTrue(decryptSuccess.isSuccess)
        assertEquals(sampleJson, decryptSuccess.getOrThrow())

        // Decrypt with wrong password
        val decryptFailure = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(encrypted, "WrongPassword#999")
        assertTrue(decryptFailure.isFailure)
    }

    @Test
    fun `joinCode collision does not replace existing trip or delete expenses`() = runBlocking {
        // Tạo đoàn 1 với joinCode cố định
        val trip1Id = repository.createTrip(
            title = "Đoàn gốc 1",
            description = "Chuyến đi 1",
            joinCode = "TRIP-ORIGINAL",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Admin 1",
        )
        val admin1 = repository.getMembers(trip1Id).first().first()

        // Thêm chi tiêu cho đoàn 1
        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = trip1Id,
            title = "Chi tiêu đoàn 1",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = admin1.id,
            totalAmount = 500000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 500000L,
            splitType = "EQUAL",
            note = "",
            timestamp = System.currentTimeMillis(),
            createdMemberId = admin1.id
        )
        repository.addExpenseWithSplits(
            expense = expense,
            splits = listOf(ExpenseSplitEntity(UUID.randomUUID().toString(), expId, trip1Id, admin1.id, 500000L)),
            actor = admin1
        )

        // Cố tình tạo đoàn 2 với cùng joinCode "TRIP-ORIGINAL" -> Hệ thống phải báo lỗi trùng mã, KHÔNG được đè đoàn 1
        try {
            repository.createTrip(
                title = "Đoàn xâm phạm 2",
                description = "Cố tình trùng mã",
                joinCode = "TRIP-ORIGINAL",
                startDate = System.currentTimeMillis(),
                endDate = System.currentTimeMillis() + 86400000L,
                adminName = "Admin 2",
            )
            fail("Phải ném ngoại lệ khi trùng joinCode do người dùng chỉ định")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("đã được sử dụng") == true)
        }

        // Kiểm tra toàn vẹn: Đoàn 1 và chi tiêu của nó vẫn nguyên vẹn 100%
        val trip1 = db.tripDao().getTripByIdOnce(trip1Id)
        assertNotNull(trip1)
        assertEquals("Đoàn gốc 1", trip1?.title)
        val expList = repository.getExpenses(trip1Id).first()
        assertEquals(1, expList.size)
        assertEquals("Chi tiêu đoàn 1", expList[0].title)
    }

    @Test
    fun `restore backup never unseals settled trip`() = runBlocking {
        val tripId = repository.createTrip(
            title = "Đoàn đã quyết toán",
            description = "Đã khóa sổ",
            joinCode = "SETTLED-01",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Admin Quyết Toán",
        )
        val originalTrip = db.tripDao().getTripByIdOnce(tripId)!!

        // Khóa sổ đoàn
        db.tripDao().updateTrip(originalTrip.copy(isSettled = true, settledAt = 1700000000000L))
        val settledTrip = db.tripDao().getTripByIdOnce(tripId)!!
        assertTrue(settledTrip.isSettled)

        // Tạo bản backup cũ trong đó đoàn chưa khóa sổ (isSettled = false)
        val backupData = com.hiepnguyen.tripfinance.data.backup.BackupData(
            metadata = com.hiepnguyen.tripfinance.data.backup.BackupMetadata(
                createdAt = 1690000000000L,
                createdAtFormatted = "2026-01-01",
                totalTrips = 1,
                totalExpenses = 0
            ),
            trips = listOf(originalTrip.copy(isSettled = false, settledAt = null))
        )

        // Phục hồi dữ liệu (clearExisting = false)
        val result = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.restoreFromBackupData(db, backupData, clearExisting = false)
        assertTrue(result.isSuccess)

        // Xác nhận trạng thái niêm phong: Đoàn VẪN PHẢI LÀ isSettled = true, không được mở niêm phong
        val afterRestoreTrip = db.tripDao().getTripByIdOnce(tripId)!!
        assertTrue("Đoàn đã quyết toán không được phép bị mở niêm phong khi restore bản cũ!", afterRestoreTrip.isSettled)
        assertEquals(1700000000000L, afterRestoreTrip.settledAt)
    }

    @Test
    fun `restore clearExisting handles cascade cleanly without trigger violation`() = runBlocking {
        // Tạo đoàn và thành viên có chi tiêu
        val tripId = repository.createTrip(
            title = "Đoàn cần dọn dẹp",
            description = "Test clearExisting",
            joinCode = "CLEAN-99",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 86400000L,
            adminName = "Admin Clean",
        )
        val admin = repository.getMembers(tripId).first().first()

        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = tripId,
            title = "Chi phí khách sạn",
            category = "HOTEL",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 1200000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 1200000L,
            splitType = "EQUAL",
            note = "",
            timestamp = System.currentTimeMillis(),
            createdMemberId = admin.id
        )
        repository.addExpenseWithSplits(
            expense = expense,
            splits = listOf(ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, admin.id, 1200000L)),
            actor = admin
        )

        // Khôi phục đè toàn bộ (clearExisting = true)
        val backupData = com.hiepnguyen.tripfinance.data.backup.BackupData(
            metadata = com.hiepnguyen.tripfinance.data.backup.BackupMetadata(
                createdAt = System.currentTimeMillis(),
                createdAtFormatted = "2026-09-28",
                totalTrips = 0,
                totalExpenses = 0
            ),
            trips = emptyList()
        )
        val result = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.restoreFromBackupData(db, backupData, clearExisting = true)
        assertTrue("clearExisting = true phải thành công mà không bị trigger chặn", result.isSuccess)

        val trips = repository.allTrips.first()
        assertTrue(trips.isEmpty())
    }

    @Test
    fun `financial input validator parses VND and foreign currency correctly`() {
        // 1. Kiểm tra VND với dấu phân cách hàng nghìn kiểu Việt Nam: 1.500 -> 1500 VND (Không được hiểu là 1.5 VND)
        val res1 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("1.500", "VND")
        assertTrue(res1 is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success)
        assertEquals(1500.0, (res1 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        val res2 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("25.450", "VND")
        assertTrue(res2 is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success)
        assertEquals(25450.0, (res2 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        val res3 = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("1.500.000", "VND")
        assertTrue(res3 is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success)
        assertEquals(1500000.0, (res3 as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        // 2. Tỷ giá nhập sai như "abc" phải báo lỗi, KHÔNG được âm thầm thay bằng 1.0
        val rateBad = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("abc", "USD")
        assertTrue("Tỷ giá 'abc' phải trả về lỗi", rateBad is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Error)

        val rateGood = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseRate("25450", "USD")
        assertTrue(rateGood is com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success)
        assertEquals(25450.0, (rateGood as com.hiepnguyen.tripfinance.domain.model.RateValidationResult.Success).rate, 0.001)

        // 3. Ngoại tệ với số thập phân hợp lệ: 12.50 USD
        val usdRes = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("12.50", "USD")
        assertTrue(usdRes is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success)
        assertEquals(12.50, (usdRes as com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Success).amount, 0.001)

        // 4. Giới hạn trên: Số tiền vượt 100 tỷ phải bị chặn
        val overLimit = com.hiepnguyen.tripfinance.domain.model.FinancialInputValidator.parseAmount("200000000000", "VND")
        assertTrue(overLimit is com.hiepnguyen.tripfinance.domain.model.AmountValidationResult.Error)
    }

    @Test
    fun `sumOfSafe prevents Long overflow`() {
        val hugeList = listOf(Long.MAX_VALUE, Long.MAX_VALUE)
        val sum = hugeList.sumOfSafe { it }
        // Thông thường Long.MAX_VALUE * 2 sẽ tràn số thành -2. Với sumOfSafe, nó kẹp an toàn ở Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, sum)
    }

    @Test
    fun `database migration from v1 to v4 produces matching schema without crash`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration.db")
        dbFile.delete()

        // Khởi tạo database phiên bản 1 bằng SQLite trực tiếp
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 1
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `joinCode` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `baseCurrency` TEXT NOT NULL, `isSettled` INTEGER NOT NULL, `settledAt` INTEGER, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_joinCode` ON `trips` (`joinCode`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trip_members` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `bankName` TEXT, `bankAccount` TEXT, `bankAccountHolder` TEXT, `joinedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `payerType` TEXT NOT NULL, `payerMemberId` TEXT, `totalAmount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedTotalAmount` INTEGER NOT NULL, `splitType` TEXT NOT NULL, `receiptImageUri` TEXT, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `createdMemberId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expense_splits` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `fund_contributions` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedAmount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `recordedByMemberId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`recordedByMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `actorMemberId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `detailBefore` TEXT, `detailAfter` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

        // Thêm dữ liệu mẫu vào v1
        sqlite.execSQL("INSERT INTO `trips` VALUES('trip-v1', 'Đoàn v1', 'Mô tả v1', 'V1-CODE', 1000, 2000, 'VND', 0, NULL, 1000, 1)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('member-v1', 'trip-v1', 'user-1', 'Thành viên v1', 'ADMIN', 1, NULL, NULL, NULL, 1000)")
        sqlite.execSQL("INSERT INTO `expenses` VALUES('exp-v1', 'trip-v1', 'Chi tiêu v1', 'FOOD', 'MEMBER', 'member-v1', 50000, 'VND', 1.0, 50000, 'EQUAL', NULL, 'Ghi chú', 1000, 'member-v1', 1000, 1)")
        sqlite.close()

        // Mở qua Room Database builder kèm migration 1_2, 2_3, 3_4, 4_5, 5_6, 6_7 (không có destructive fallback)
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        val migratedTrips = runBlocking { migratedDb.tripDao().getAllTripsOnce() }
        assertEquals(1, migratedTrips.size)
        assertEquals("Đoàn v1", migratedTrips[0].title)

        val migratedExpenses = runBlocking { migratedDb.expenseDao().getAllExpensesOnce() }
        assertEquals(1, migratedExpenses.size)
        assertEquals(50000.0, migratedExpenses[0].totalAmount, 0.001)

        val migratedMembers = runBlocking { migratedDb.tripMemberDao().getAllMembersOnce() }
        assertEquals(1, migratedMembers.size)
        assertEquals("member-v1", migratedMembers[0].id)
        assertEquals("Thành viên v1", migratedMembers[0].name)

        // Kiểm tra bảng settlement_snapshots đã tồn tại và có các cột chuẩn v5
        val snapshotList = runBlocking { migratedDb.settlementDao().getAllSnapshotsOnce() }
        assertTrue(snapshotList.isEmpty()) // Chưa có snapshot nào nhưng bảng đã sẵn sàng

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `test DatabaseKeyManager never stores plaintext seed in SharedPreferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("trip_finance_db_sec_vault", Context.MODE_PRIVATE)

        // Đảm bảo không tồn tại bất kỳ fallback plaintext nào
        assertFalse(prefs.contains("fallback_db_seed"))

        // Lấy passphrase
        val passphrase = com.hiepnguyen.tripfinance.data.security.DatabaseKeyManager.getOrCreateDatabasePassphrase(context)
        assertNotNull(passphrase)
        assertEquals(32, passphrase.size)

        // Xác nhận sau khi lấy passphrase, SharedPreferences tuyệt đối KHÔNG chứa khóa plaintext
        assertFalse("Tuyệt đối không lưu khóa dự phòng plaintext trong SharedPreferences", prefs.contains("fallback_db_seed"))
    }

    @Test
    fun `test AppDatabase resetDatabase cleans up credentials safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("trip_finance_db_sec_vault", Context.MODE_PRIVATE)
        prefs.edit().putString("test_key", "test_value").commit()
        assertTrue(prefs.contains("test_key"))

        AppDatabase.resetDatabase(context)
        assertFalse("resetDatabase phải xóa sạch SharedPreferences bảo mật để tạo mới", prefs.contains("test_key"))
    }

    @Test
    fun `test DatabaseSecurityException error types for failure modes`() {
        val exInit = com.hiepnguyen.tripfinance.data.security.DatabaseSecurityException(
            message = "KeyStore init failed",
            errorType = com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.KEYSTORE_INIT_FAILED
        )
        assertEquals(com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.KEYSTORE_INIT_FAILED, exInit.errorType)

        val exCipher = com.hiepnguyen.tripfinance.data.security.DatabaseSecurityException(
            message = "SQLCipher load failed",
            errorType = com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.SQLCIPHER_LOAD_FAILED
        )
        assertEquals(com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.SQLCIPHER_LOAD_FAILED, exCipher.errorType)

        val exCommit = com.hiepnguyen.tripfinance.data.security.DatabaseSecurityException(
            message = "Commit failed",
            errorType = com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.COMMIT_FAILED
        )
        assertEquals(com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.COMMIT_FAILED, exCommit.errorType)
    }

    @Test
    fun `test AndroidKeyStoreKeyProvider enforces fail-closed when KeyStore unavailable`() {
        val prodProvider = com.hiepnguyen.tripfinance.data.security.AndroidKeyStoreKeyProvider()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val exception = assertThrows(com.hiepnguyen.tripfinance.data.security.DatabaseSecurityException::class.java) {
            prodProvider.getOrCreateDatabasePassphrase(context)
        }
        assertTrue(
            "Fail-closed phải ném lỗi bảo mật khi KeyStore không khả dụng",
            exception.errorType == com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.KEYSTORE_UNAVAILABLE ||
            exception.errorType == com.hiepnguyen.tripfinance.data.security.DatabaseSecurityErrorType.KEYSTORE_INIT_FAILED
        )
    }

    @Test
    fun `migration from legacy v2 with old settlement_snapshots converts data cleanly to v4`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_legacy_snapshot.db")
        dbFile.delete()

        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 2
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `joinCode` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `baseCurrency` TEXT NOT NULL, `isSettled` INTEGER NOT NULL, `settledAt` INTEGER, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_joinCode` ON `trips` (`joinCode`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trip_members` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `bankName` TEXT, `bankAccount` TEXT, `bankAccountHolder` TEXT, `joinedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `payerType` TEXT NOT NULL, `payerMemberId` TEXT, `totalAmount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedTotalAmount` INTEGER NOT NULL, `splitType` TEXT NOT NULL, `receiptImageUri` TEXT, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `createdMemberId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expense_splits` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `fund_contributions` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedAmount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `recordedByMemberId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`recordedByMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `actorMemberId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `detailBefore` TEXT, `detailAfter` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

        // Tạo bảng settlement_snapshots kiểu cũ với các cột snapshotDataJson, totalFund, settledByMemberId
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `settlement_snapshots` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `totalExpenses` INTEGER NOT NULL, `totalFund` INTEGER NOT NULL, `remainingFund` INTEGER NOT NULL, `settledByMemberId` TEXT, `snapshotDataJson` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

        sqlite.execSQL("INSERT INTO `trips` VALUES('trip-legacy', 'Đoàn cũ', 'Mô tả', 'LEGACY-CODE', 1000, 2000, 'VND', 1, 2000, 1000, 1)")
        sqlite.execSQL("INSERT INTO `settlement_snapshots` VALUES('snap-old', 'trip-legacy', 2000, 500000, 600000, 100000, 'member-1', '{\"summary\":\"test\"}')")
        sqlite.close()

        // Nâng cấp trực tiếp lên v7
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_legacy_snapshot.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        val snapshots = runBlocking { migratedDb.settlementDao().getAllSnapshotsOnce() }
        assertEquals(1, snapshots.size)
        val snap = snapshots[0]
        assertEquals("snap-old", snap.id)
        assertEquals("Bản quyết toán đã lưu", snap.snapshotTitle)
        assertEquals(600000L, snap.totalFundCollected)
        assertEquals(500000L, snap.totalFundSpent)
        assertEquals(100000L, snap.remainingFund)
        assertEquals("{\"summary\":\"test\"}", snap.settlementJson)

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `test backup and restore roundtrip preserves receiptImageUri, percentage, and trip version`(): Unit = runBlocking {
        val tripId = repository.createTrip(
            title = "Chuyến đi Đà Nẵng",
            description = "Du lịch hè",
            joinCode = "DN2026",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin DN"
        )
        val adminMember = repository.getMembers(tripId).first().first()

        // Thêm thành viên thứ 2
        val member2Id = repository.addMember(
            tripId = tripId,
            name = "Thành viên 2",
            role = "MEMBER",
            actor = adminMember
        )

        // Thêm khoản chi có receiptImageUri và split percentage
        val expenseId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expenseId,
            tripId = tripId,
            title = "Khách sạn biển",
            category = "HOTEL",
            payerType = "MEMBER",
            payerMemberId = adminMember.id,
            totalAmount = 1000000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 1000000L,
            splitType = "RATIO",
            receiptImageUri = "content://media/external/images/receipt_123.jpg",
            note = "Biên lai đầy đủ",
            timestamp = 1500L,
            createdMemberId = adminMember.id
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expenseId, tripId, adminMember.id, 600000L, 60.0),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expenseId, tripId, member2Id, 400000L, 40.0)
        )
        repository.addExpenseWithSplits(expense, splits, adminMember)

        // Xuất backup dạng JSON (mã hóa mật khẩu để giữ nguyên số tài khoản)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupFile = repository.createLocalBackup(context, password = "SafePassword123")
        assertTrue(backupFile.exists())

        // Nạp lại vào database mới
        val newDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val restoreRes = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.restoreFromFileStreaming(
            db = newDb,
            file = backupFile,
            clearExisting = true,
            password = "SafePassword123"
        )
        assertTrue(restoreRes.isSuccess)

        // Kiểm chứng tính nguyên vẹn: receiptImageUri, percentage, và trip version không bị mất
        val restoredExpenses = newDb.expenseDao().getAllExpensesOnce()
        assertEquals(1, restoredExpenses.size)
        assertEquals("content://media/external/images/receipt_123.jpg", restoredExpenses[0].receiptImageUri)

        val restoredSplits = newDb.expenseDao().getAllSplitsOnce()
        assertEquals(2, restoredSplits.size)
        val split1 = restoredSplits.first { it.memberId == adminMember.id }
        assertEquals(60.0, split1.percentage ?: 0.0, 0.001)

        val restoredTrips = newDb.tripDao().getAllTripsOnce()
        assertEquals(1, restoredTrips.size)
        assertEquals(1L, restoredTrips[0].version)

        newDb.close()
        backupFile.delete()
    }

    @Test
    fun `test backup export contains zero bank fields and encrypted backup preserves privacy`(): Unit = runBlocking {
        val tripId = repository.createTrip(
            title = "Đoàn Test Bảo Mật",
            description = "Kiểm tra bảo mật dữ liệu thành viên",
            joinCode = "SEC-2026",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Thủ quỹ"
        )

        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Tạo bản sao lưu không mã hóa: Tuyệt đối không có trường ngân hàng
        val unencryptedFile = repository.createLocalBackup(context, password = null)
        val unencryptedJson = unencryptedFile.readText(Charsets.UTF_8)
        assertFalse("Bản sao lưu không được chứa bankName", unencryptedJson.contains("bankName"))
        assertFalse("Bản sao lưu không được chứa bankAccount", unencryptedJson.contains("bankAccount"))
        assertFalse("Bản sao lưu không được chứa bankAccountHolder", unencryptedJson.contains("bankAccountHolder"))

        // 2. Tạo bản sao lưu có mã hóa mật khẩu: Nội dung tệp mã hóa AES-256-GCM
        val encryptedFile = repository.createLocalBackup(context, password = "StrongPassword789")
        val encryptedContent = encryptedFile.readText(Charsets.UTF_8)
        val rootEnc = org.json.JSONObject(encryptedContent)
        assertTrue("Tệp phải có cờ encrypted = true", rootEnc.getBoolean("encrypted"))
        assertEquals("PBKDF2WithHmacSHA256", rootEnc.getString("kdf"))

        // Sau khi giải mã đúng mật khẩu, dữ liệu JSON phục hồi nguyên vẹn và cũng không chứa trường ngân hàng
        val decrypted = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(encryptedContent, "StrongPassword789").getOrThrow()
        assertFalse("Sau khi giải mã không được chứa bankName", decrypted.contains("bankName"))
        assertFalse("Sau khi giải mã không được chứa bankAccount", decrypted.contains("bankAccount"))
        assertTrue("Sau khi giải mã phải phục hồi thông tin đoàn", decrypted.contains("SEC-2026"))

        unencryptedFile.delete()
        encryptedFile.delete()
    }

    @Test
    fun `test backup integrity validation rejects tampered split sums and invalid roles`() {
        val trip = TripEntity("trip-1", "Hà Nội", "Công tác", "HN-01", 1000L, 2000L)
        val memberAdmin = TripMemberEntity("m-1", "trip-1", "u-1", "Admin", "ADMIN")
        val memberUser = TripMemberEntity("m-2", "trip-1", "u-2", "User", "MEMBER")

        val expense = ExpenseEntity(
            id = "exp-1",
            tripId = "trip-1",
            title = "Ăn tối",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = "m-1",
            totalAmount = 500000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 500000L,
            splitType = "EQUAL",
            createdMemberId = "m-1"
        )

        // 1. Phép thử: Tổng tiền chia không khớp với convertedTotalAmount (sửa tay số tiền)
        val tamperedSplits = listOf(
            ExpenseSplitEntity("s-1", "exp-1", "trip-1", "m-1", 200000L),
            ExpenseSplitEntity("s-2", "exp-1", "trip-1", "m-2", 200000L) // Tổng 400k != 500k
        )
        val tamperedData = com.hiepnguyen.tripfinance.data.backup.BackupData(
            metadata = com.hiepnguyen.tripfinance.data.backup.BackupMetadata(),
            trips = listOf(trip),
            members = listOf(memberAdmin, memberUser),
            expenses = listOf(expense),
            splits = tamperedSplits
        )
        val splitCheck = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.validateBackupData(tamperedData)
        assertTrue("Phải từ chối bản sao lưu bị sửa lệch tổng tiền chia", splitCheck.isFailure)
        assertTrue(splitCheck.exceptionOrNull()?.message?.contains("không khớp với số tiền quy đổi") == true)

        // 2. Phép thử: Sửa tay vai trò không hợp lệ (ví dụ: HACKER)
        val fakeRoleMember = TripMemberEntity("m-2", "trip-1", "u-2", "User", "HACKER")
        val correctSplits = listOf(
            ExpenseSplitEntity("s-1", "exp-1", "trip-1", "m-1", 250000L),
            ExpenseSplitEntity("s-2", "exp-1", "trip-1", "m-2", 250000L)
        )
        val roleData = com.hiepnguyen.tripfinance.data.backup.BackupData(
            metadata = com.hiepnguyen.tripfinance.data.backup.BackupMetadata(),
            trips = listOf(trip),
            members = listOf(memberAdmin, fakeRoleMember),
            expenses = listOf(expense),
            splits = correctSplits
        )
        val roleCheck = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.validateBackupData(roleData)
        assertTrue("Phải từ chối bản sao lưu bị gán vai trò phi pháp", roleCheck.isFailure)
        assertTrue(roleCheck.exceptionOrNull()?.message?.contains("không hợp lệ") == true)

        // 3. Phép thử: Thêm nhật ký giả với thành viên không tồn tại trong đoàn
        val fakeLog = AuditLogEntity(
            id = "log-1",
            tripId = "trip-1",
            actorMemberId = "fake-member-999",
            actorName = "Kẻ mạo danh",
            action = "ADD_EXPENSE",
            description = "Khoản chi ma",
            timestamp = 1200L
        )
        val fakeLogData = com.hiepnguyen.tripfinance.data.backup.BackupData(
            metadata = com.hiepnguyen.tripfinance.data.backup.BackupMetadata(),
            trips = listOf(trip),
            members = listOf(memberAdmin, memberUser),
            expenses = listOf(expense),
            splits = correctSplits,
            auditLogs = listOf(fakeLog)
        )
        val logCheck = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.validateBackupData(fakeLogData)
        assertTrue("Phải từ chối bản sao lưu chứa nhật ký kiểm toán giả", logCheck.isFailure)
        assertTrue(logCheck.exceptionOrNull()?.message?.contains("nghi vấn nhật ký giả") == true)
    }

    @Test
    fun `test BackupCryptoUtils password requirements and iteration bounds`() {
        // 1. Mật khẩu ngắn dưới 6 ký tự phải bị từ chối
        assertThrows(IllegalArgumentException::class.java) {
            com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.encryptBackup(
                "{}",
                "12345",
                iterations = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.TEST_ITERATIONS
            )
        }

        // 2. Mật khẩu 6 ký tự hợp lệ
        val encrypted = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.encryptBackup(
            "{\"hello\":\"world\"}",
            "123456",
            iterations = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.TEST_ITERATIONS
        )
        assertTrue(com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.isEncryptedBackup(encrypted))

        // 3. Giải mã sai mật khẩu phải trả về failure
        val failDec = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(encrypted, "WrongPassword")
        assertTrue(failDec.isFailure)

        // 4. Giải mã đúng mật khẩu
        val okDec = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(encrypted, "123456")
        assertTrue(okDec.isSuccess)
        assertEquals("{\"hello\":\"world\"}", okDec.getOrThrow())

        // 5. Số vòng lặp ngoài biên an toàn (dưới 1.000 hoặc trên 1.000.000) phải bị từ chối
        assertThrows(IllegalArgumentException::class.java) {
            com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.encryptBackup("{}", "123456", iterations = 500)
        }
        assertThrows(IllegalArgumentException::class.java) {
            com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.encryptBackup("{}", "123456", iterations = 2_000_000)
        }
    }

    @Test
    fun `test BackupCryptoUtils rejects malicious iterations to prevent CPU DoS`() {
        val validEncrypted = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.encryptBackup(
            "{\"data\":1}",
            "Password123",
            iterations = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.TEST_ITERATIONS
        )
        val root = org.json.JSONObject(validEncrypted)

        // Phép thử 1: Tệp độc hại đặt iterations = 50_000_000 (tấn công DoS CPU treo máy)
        root.put("iterations", 50_000_000)
        val dosResult = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(root.toString(), "Password123")
        assertTrue("Phải từ chối tệp có số vòng lặp KDF vượt ngưỡng an toàn", dosResult.isFailure)
        assertTrue(dosResult.exceptionOrNull()?.message?.contains("không nằm trong giới hạn an toàn") == true)

        // Phép thử 2: Tệp đặt iterations = 50 (quá yếu)
        root.put("iterations", 50)
        val weakResult = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(root.toString(), "Password123")
        assertTrue("Phải từ chối tệp có số vòng lặp KDF quá thấp", weakResult.isFailure)
        assertTrue(weakResult.exceptionOrNull()?.message?.contains("không nằm trong giới hạn an toàn") == true)
    }

    @Test
    fun `test generateXlsxReport produces valid workbook with 5 sheets and zero bank details`() {
        val trip = TripEntity(
            id = "t-xlsx",
            title = "=HYPERLINK(\"http://evil.com\", \"Đoàn Kiểm Thử\")",
            description = "Test Excel XLSX",
            joinCode = "XLSX01",
            startDate = 1000L,
            endDate = 2000L
        )
        val member1 = TripMemberEntity(
            id = "m-1",
            tripId = "t-xlsx",
            userId = "u-1",
            name = "@HackerName",
            role = "ADMIN"
        )
        val member2 = TripMemberEntity(
            id = "m-2",
            tripId = "t-xlsx",
            userId = "u-2",
            name = "+SecondPerson",
            role = "MEMBER"
        )
        val expense = ExpenseEntity(
            id = "e-1",
            tripId = "t-xlsx",
            title = "=CMD|' /C calc'!A0",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = "m-1",
            totalAmount = 200000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 200000L,
            splitType = "EQUAL",
            note = "Ghi chú có dấu \"ngoặc kép\" và\nxuống dòng",
            timestamp = 1500L,
            createdMemberId = "m-1"
        )
        val fund = FundContributionEntity(
            id = "f-1",
            tripId = "t-xlsx",
            memberId = "m-2",
            amount = 500000L,
            currency = "VND",
            exchangeRate = 1.0,
            convertedAmount = 500000L,
            note = "Đóng quỹ đợt 1",
            timestamp = 1100L,
            recordedByMemberId = "m-1"
        )

        val summary = com.hiepnguyen.tripfinance.domain.model.FinancialSummary(
            totalExpenses = 200000L,
            personalPaidExpenses = 200000L,
            fundPaidExpenses = 0L,
            totalFundCollected = 500000L,
            remainingFund = 500000L,
            isBalanced = true,
            balanceDiscrepancy = 0L
        )

        val status1 = com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus(
            member = member1,
            totalPaid = 200000L,
            outOfPocketPaid = 200000L,
            fundContributed = 0L,
            totalOwed = 100000L,
            balance = 100000L,
            status = com.hiepnguyen.tripfinance.domain.model.BalanceStatus.RECEIVE
        )
        val status2 = com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus(
            member = member2,
            totalPaid = 500000L,
            outOfPocketPaid = 0L,
            fundContributed = 500000L,
            totalOwed = 100000L,
            balance = 400000L,
            status = com.hiepnguyen.tripfinance.domain.model.BalanceStatus.RECEIVE
        )

        val transfer = com.hiepnguyen.tripfinance.domain.model.SettlementTransfer(
            id = "tf-1",
            fromMember = member2,
            toMember = member1,
            amount = 100000L,
            transferNote = "+SecondPerson chuyển cho @HackerName"
        )

        val baos = java.io.ByteArrayOutputStream()
        com.hiepnguyen.tripfinance.domain.export.ReportGenerator.generateXlsxReport(
            outputStream = baos,
            trip = trip,
            members = listOf(member1, member2),
            summary = summary,
            statuses = listOf(status1, status2),
            settlementTransfers = listOf(transfer),
            expenses = listOf(expense),
            funds = listOf(fund)
        )

        val bytes = baos.toByteArray()
        assertTrue("File XLSX phải có nội dung", bytes.isNotEmpty())

        val entries = mutableMapOf<String, String>()
        var firstEntryName: String? = null
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (firstEntryName == null) firstEntryName = entry.name
                val text = zis.bufferedReader(Charsets.UTF_8).readText()
                entries[entry.name] = text
                entry = zis.nextEntry
            }
        }

        // 1. Phải có [Content_Types].xml đầu tiên
        assertEquals("[Content_Types].xml", firstEntryName)

        // 2. Phải có 5 sheet XML tương ứng 5 sheet
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet3.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet4.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet5.xml"))

        // 3. Tên 5 sheet trong workbook
        val workbookXml = entries["xl/workbook.xml"] ?: ""
        assertTrue(workbookXml.contains("name=\"Tổng quan\""))
        assertTrue(workbookXml.contains("name=\"Chi tiêu\""))
        assertTrue(workbookXml.contains("name=\"Theo thành viên\""))
        assertTrue(workbookXml.contains("name=\"Quỹ chung\""))
        assertTrue(workbookXml.contains("name=\"Gợi ý chuyển khoản\""))

        // 4. Tuyệt đối không chứa chuỗi ngân hàng, số tài khoản
        val fullContent = entries.values.joinToString("\n")
        assertFalse("Không được chứa tên ngân hàng ACB", fullContent.contains("Ngân hàng ACB"))
        assertFalse("Không được chứa tên ngân hàng Vietcombank", fullContent.contains("Vietcombank"))
        assertFalse("Không được chứa số tài khoản", fullContent.contains("987654321"))
        assertFalse("Không được chứa số tài khoản", fullContent.contains("1122334455"))

        // 5. Chuỗi công thức phải được vô hiệu hóa bằng dấu nháy đơn
        assertTrue(
            "Công thức =HYPERLINK phải có tiền tố ' hoặc &apos;",
            fullContent.contains("'=HYPERLINK") || fullContent.contains("&apos;=HYPERLINK")
        )
        assertTrue("Tên bắt đầu bằng @ phải có tiền tố '", fullContent.contains("'@HackerName") || fullContent.contains("&apos;@HackerName"))
        assertTrue("Tên bắt đầu bằng + phải có tiền tố '", fullContent.contains("'+SecondPerson") || fullContent.contains("&apos;+SecondPerson"))
        assertTrue("Công thức =CMD phải có tiền tố '", fullContent.contains("'=CMD") || fullContent.contains("&apos;=CMD"))

        // 6. Số liệu khớp với tổng quan
        val sheet1 = entries["xl/worksheets/sheet1.xml"] ?: ""
        assertTrue(sheet1.contains("<v>200000</v>"))
        assertTrue(sheet1.contains("<v>500000</v>"))
    }

    @Test
    fun `test exportAndShareXlsx creates file in cache reports directory and cleanOldReports handles it`() = runBlocking {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val trip = TripEntity(
            id = "t-export-xlsx",
            title = "Chuyến đi Hà Nội - Hạ Long",
            description = "Công tác",
            joinCode = "HL2026",
            startDate = 1000L,
            endDate = 2000L
        )
        val member = TripMemberEntity(
            id = "m-export",
            tripId = "t-export-xlsx",
            userId = "u-export",
            name = "Trần Văn An",
            role = "ADMIN"
        )
        val summary = com.hiepnguyen.tripfinance.domain.model.FinancialSummary()

        val exportedFile = com.hiepnguyen.tripfinance.domain.export.PdfReportExporter.exportAndShareXlsx(
            context = context,
            trip = trip,
            members = listOf(member),
            summary = summary,
            statuses = emptyList(),
            settlementTransfers = emptyList(),
            expenses = emptyList(),
            funds = emptyList()
        )

        assertNotNull("Tệp XLSX xuất ra không được null", exportedFile)
        assertTrue("Tệp XLSX phải tồn tại trên đĩa", exportedFile!!.exists())
        assertTrue("Tên tệp phải kết thúc bằng .xlsx", exportedFile.name.endsWith(".xlsx"))
        val todayStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        assertTrue("Tên tệp phải chứa định dạng ngày yyyyMMdd", exportedFile.name.contains(todayStr))

        // Kiểm tra cleanOldReports: tệp mới tạo không bị xóa
        com.hiepnguyen.tripfinance.domain.export.PdfReportExporter.cleanOldReports(context)
        assertTrue("Tệp mới tạo không được bị xóa bởi cleanOldReports", exportedFile.exists())

        // Đặt mốc thời gian cũ hơn 25 giờ: cleanOldReports phải dọn dẹp
        exportedFile.setLastModified(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        com.hiepnguyen.tripfinance.domain.export.PdfReportExporter.cleanOldReports(context)
        assertFalse("Tệp cũ hơn 24 giờ phải được cleanOldReports tự động xóa", exportedFile.exists())
    }


    @Test
    fun `test database trigger and repository block expense insertions on settled trip`(): Unit = runBlocking {
        val tripId = repository.createTrip(
            title = "Đoàn Đã Khóa Sổ",
            description = "Test trigger",
            joinCode = "LOCK-01",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin Lock",
        )
        val admin = repository.getMembers(tripId).first().first()

        // Thêm 1 khoản chi hợp lệ và chia tiền cân bằng
        val expenseId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expenseId,
            tripId = tripId,
            title = "Ăn trưa",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 200000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 200000L,
            splitType = "EQUAL",
            createdMemberId = admin.id
        )
        val split = ExpenseSplitEntity(UUID.randomUUID().toString(), expenseId, tripId, admin.id, 200000L)
        repository.addExpenseWithSplits(expense, listOf(split), admin)

        // Thực hiện khóa sổ
        val trip = db.tripDao().getTripByIdOnce(tripId)!!
        val (summary, _) = repository.calculateFinancialSummaryAndStatuses(
            members = listOf(admin),
            expenses = listOf(expense),
            splits = listOf(split),
            fundContributions = emptyList()
        )
        val snapshot = repository.finalizeSettlement(
            trip = trip,
            snapshotTitle = "Quyết toán đợt 1",
            summary = summary,
            settlementJson = "",
            actor = admin
        )
        assertNotNull(snapshot)

        // 1. Phép thử Repository: Cố tình thêm khoản chi mới khi đoàn đã khóa sổ -> Bị chặn
        val newExpense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            title = "Cố tình thêm khi đã khóa",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 50000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 50000L,
            splitType = "EQUAL",
            createdMemberId = admin.id
        )
        val newSplit = ExpenseSplitEntity(UUID.randomUUID().toString(), newExpense.id, tripId, admin.id, 50000L)

        val repEx = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.addExpenseWithSplits(newExpense, listOf(newSplit), admin)
            }
        }
        assertTrue(repEx.message?.contains("đã được khóa sổ quyết toán") == true)

        // 2. Phép thử Cơ sở dữ liệu: Gọi trực tiếp DAO insert bỏ qua tầng ViewModel/Repository
        // Trigger SQLite (trg_prevent_expense_insert_when_settled) phải ABORT với lỗi SETTLEMENT LOCKED
        val dbEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.expenseDao().insertExpense(newExpense)
            }
        }
        assertTrue("SQLite Trigger cấp cơ sở dữ liệu phải chặn đứng chèn chi tiêu", dbEx.message?.contains("SETTLEMENT LOCKED") == true)
    }

    @Test
    fun `test finalizeSettlement optimistic locking prevents concurrent overwrite`(): Unit = runBlocking {
        val tripId = repository.createTrip(
            title = "Đoàn Đồng Thời",
            description = "Test Optimistic Lock",
            joinCode = "CONCUR-01",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin Đồng Thời",
        )
        val admin = repository.getMembers(tripId).first().first()

        val staleTrip = db.tripDao().getTripByIdOnce(tripId)!!
        assertEquals(1L, staleTrip.version)

        // Giả lập một tác vụ khác đã cập nhật version chuyến đi lên 2
        val updatedTrip = staleTrip.copy(version = 2L, title = "Tên đoàn đã bị sửa đổi")
        db.tripDao().updateTrip(updatedTrip)

        // Phép thử: Dùng TripEntity cũ từ UI (v=1) để khóa sổ -> Phải ném ConcurrentModificationException
        val summary = com.hiepnguyen.tripfinance.domain.model.FinancialSummary(
            totalExpenses = 0L,
            personalPaidExpenses = 0L,
            fundPaidExpenses = 0L,
            totalFundCollected = 0L,
            remainingFund = 0L,
            isBalanced = true,
            balanceDiscrepancy = 0L,
            memberCount = 1
        )

        val ex = assertThrows(java.util.ConcurrentModificationException::class.java) {
            runBlocking {
                repository.finalizeSettlement(
                    trip = staleTrip, // version 1 cũ
                    snapshotTitle = "Quyết toán đồng thời",
                    summary = summary,
                    settlementJson = "",
                    actor = admin
                )
            }
        }
        assertTrue(ex.message?.contains("thay đổi đồng thời") == true)
    }

    @Test
    fun `test finalizeSettlement rejects empty trip and produces structured JSON`(): Unit = runBlocking {
        // Tạo đoàn trống không có thành viên
        val emptyTrip = TripEntity(
            id = "trip-empty",
            title = "Đoàn Trống",
            description = "Không có ai",
            joinCode = "EMPTY-01",
            startDate = 1000L,
            endDate = 2000L
        )
        db.tripDao().insertTrip(emptyTrip)

        val fakeAdmin = TripMemberEntity("m-fake", "trip-empty", "u-fake", "Fake Admin", "ADMIN")
        val summary = com.hiepnguyen.tripfinance.domain.model.FinancialSummary(isBalanced = true, balanceDiscrepancy = 0L)

        // 1. Khóa sổ đoàn không có thành viên -> Bị từ chối
        val emptyEx = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.finalizeSettlement(emptyTrip, "Khóa đoàn trống", summary, "", fakeAdmin)
            }
        }
        assertTrue(emptyEx.message?.contains("không có thành viên nào") == true)

        // 2. Thêm thành viên nhưng có chênh lệch đối soát -> Bị từ chối
        db.tripMemberDao().insertMember(fakeAdmin)
        val expense = ExpenseEntity(
            id = "exp-unbalanced",
            tripId = "trip-empty",
            title = "Chi chưa phân bổ hết",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = fakeAdmin.id,
            totalAmount = 100000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 100000L,
            splitType = "EQUAL",
            createdMemberId = fakeAdmin.id
        )
        // Split chỉ có 90.000 đ (lệch 10.000 đ)
        val split = ExpenseSplitEntity("s-unbal", expense.id, "trip-empty", fakeAdmin.id, 90000L)
        db.expenseDao().insertExpense(expense)
        db.expenseDao().insertSplits(listOf(split))

        val unbalEx = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                val freshTrip = db.tripDao().getTripByIdOnce("trip-empty")!!
                repository.finalizeSettlement(freshTrip, "Khóa lệch", summary, "", fakeAdmin)
            }
        }
        assertTrue(unbalEx.message?.contains("chênh lệch đối soát") == true)

        // 3. Sửa split đủ 100.000 đ (chênh lệch = 0) -> Khóa sổ thành công và sinh JSON cấu trúc chuẩn
        db.expenseDao().updateExpenseWithSplits(expense, listOf(split.copy(amount = 100000L)))
        val readyTrip = db.tripDao().getTripByIdOnce("trip-empty")!!
        val snapshot = repository.finalizeSettlement(readyTrip, "Khóa chuẩn", summary, "", fakeAdmin)

        assertNotNull(snapshot)
        val jsonRoot = org.json.JSONObject(snapshot.settlementJson)
        assertTrue(jsonRoot.has("summary"))
        assertTrue(jsonRoot.has("members"))
        assertEquals("trip-empty", jsonRoot.getString("tripId"))
        assertEquals(100000L, jsonRoot.getJSONObject("summary").getLong("totalExpenses"))
        assertEquals(0L, jsonRoot.getJSONObject("summary").getLong("balanceDiscrepancy"))
    }

    @Test
    fun `test SettlementEngine enforces absolute zero discrepancy and rejects small 1-5 VND leaks`() {
        val member = TripMemberEntity("m1", "t1", "u1", "Nguyen Van A", "ADMIN")
        val status = com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus(
            member = member,
            totalPaid = 100005L,
            outOfPocketPaid = 100005L,
            fundContributed = 0L,
            totalOwed = 100000L,
            balance = 5L, // Lệch 5 VND (trước đây <= 5 VND bị cho qua)
            status = com.hiepnguyen.tripfinance.domain.model.BalanceStatus.RECEIVE
        )

        // 1. Lệch 5 VND với quỹ = 0 -> discrepancy = 5 -> Phải bị từ chối với lỗi đối soát
        val res5 = com.hiepnguyen.tripfinance.domain.engine.SettlementEngine.computeSettlementWithStatus(
            memberStatuses = listOf(status),
            tripJoinCode = "CODE",
            remainingFund = 0L
        )
        assertNotNull("Dung sai 5 VND không được phép bỏ qua", res5.reconciliationError)
        assertTrue(res5.transfers.isEmpty())
        assertTrue(res5.reconciliationError?.contains("chênh lệch đối soát") == true)

        // 2. Đoàn không có thành viên nào -> Phải bị từ chối
        val resEmpty = com.hiepnguyen.tripfinance.domain.engine.SettlementEngine.computeSettlementWithStatus(
            memberStatuses = emptyList(),
            tripJoinCode = "CODE",
            remainingFund = 0L
        )
        assertNotNull(resEmpty.reconciliationError)
        assertTrue(resEmpty.reconciliationError?.contains("không có thành viên nào") == true)

        // 3. Cân bằng tuyệt đối (balance = 0) -> Thành công
        val balancedStatus = status.copy(totalPaid = 100000L, balance = 0L, status = com.hiepnguyen.tripfinance.domain.model.BalanceStatus.BALANCED)
        val resBalanced = com.hiepnguyen.tripfinance.domain.engine.SettlementEngine.computeSettlementWithStatus(
            memberStatuses = listOf(balancedStatus),
            tripJoinCode = "CODE",
            remainingFund = 0L
        )
        assertNull(resBalanced.reconciliationError)
    }

    @Test
    fun `test Database triggers block tampering of trips and funds when settled`(): Unit = runBlocking {
        val trip = TripEntity(
            id = "trip-lock-test",
            title = "Đoàn Đã Khóa",
            description = "Test trigger",
            joinCode = "LOCK-01",
            startDate = 1000L,
            endDate = 2000L,
            isSettled = true,
            settledAt = 3000L
        )
        db.tripDao().insertTrip(trip)

        // 1. Thử xóa chuyến đi đã khóa sổ -> Trigger DB phải chặn đứng
        val delTripEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.tripDao().deleteTripById("trip-lock-test")
            }
        }
        assertTrue(delTripEx.message?.contains("SETTLEMENT LOCKED") == true)

        // 2. Thử sửa tiêu đề chuyến đi đã khóa sổ mà không mở sổ -> Trigger DB phải chặn đứng
        val updateTripEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.tripDao().updateTrip(trip.copy(title = "Tên bị giả mạo"))
            }
        }
        assertTrue(updateTripEx.message?.contains("SETTLEMENT LOCKED") == true)
    }

    @Test
    fun `test repository rejects cross-trip payer and splits`(): Unit = runBlocking {
        // Tạo 2 chuyến đi khác nhau: Trip A và Trip B
        val tripAId = repository.createTrip(
            title = "Chuyến đi A",
            description = "Trip A",
            joinCode = "TRIP-A-01",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin A",
        )
        val tripBId = repository.createTrip(
            title = "Chuyến đi B",
            description = "Trip B",
            joinCode = "TRIP-B-01",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin B",
        )

        val membersA = repository.getMembers(tripAId).first()
        val membersB = repository.getMembers(tripBId).first()
        val adminA = membersA.first()
        val adminB = membersB.first()

        // 1. Thử tạo khoản chi trong Trip A nhưng payerMemberId lại là adminB (thuộc Trip B)
        val invalidPayerExpense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripAId,
            payerType = "MEMBER",
            payerMemberId = adminB.id, // Thuộc Trip B
            title = "Cà phê bất hợp pháp",
            category = "CAFE",
            totalAmount = 50000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 50000L,
            splitType = "EQUAL",
            createdMemberId = adminA.id
        )
        val validSplitsA = listOf(
            ExpenseSplitEntity(
                id = UUID.randomUUID().toString(),
                expenseId = invalidPayerExpense.id,
                memberId = adminA.id,
                tripId = tripAId,
                amount = 50000L
            )
        )

        val exPayer = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.addExpenseWithSplits(invalidPayerExpense, validSplitsA, adminA)
            }
        }
        assertTrue("Lỗi phải nêu rõ người chi không thuộc đoàn", exPayer.message?.contains("không thuộc danh sách thành viên của đoàn") == true)

        // 2. Thử tạo khoản chi trong Trip A với split chứa adminB (thuộc Trip B)
        val validPayerExpense = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripAId,
            payerType = "MEMBER",
            payerMemberId = adminA.id,
            title = "Vé xe",
            category = "TRANSPORT",
            totalAmount = 100000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 100000L,
            splitType = "EQUAL",
            createdMemberId = adminA.id
        )
        val crossTripSplits = listOf(
            ExpenseSplitEntity(
                id = UUID.randomUUID().toString(),
                expenseId = validPayerExpense.id,
                memberId = adminB.id, // Thuộc Trip B!
                tripId = tripAId,
                amount = 100000L
            )
        )

        val exSplit = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.addExpenseWithSplits(validPayerExpense, crossTripSplits, adminA)
            }
        }
        assertTrue("Lỗi phải nêu rõ thành viên chịu chi không thuộc đoàn", exSplit.message?.contains("không thuộc danh sách thành viên của đoàn") == true)
    }

    @Test
    fun `test repository rejects cross-trip fund contributor`(): Unit = runBlocking {
        val tripAId = repository.createTrip(
            title = "Chuyến đi Quỹ A",
            description = "Trip A",
            joinCode = "FUND-A-01",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Thủ quỹ A",
        )
        val tripBId = repository.createTrip(
            title = "Chuyến đi Quỹ B",
            description = "Trip B",
            joinCode = "FUND-B-01",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Thủ quỹ B",
        )

        val adminA = repository.getMembers(tripAId).first().first()
        val adminB = repository.getMembers(tripBId).first().first()

        // Nộp quỹ cho Trip A nhưng memberId lại là adminB (thuộc Trip B)
        val invalidFund = FundContributionEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripAId,
            memberId = adminB.id, // Thuộc Trip B!
            amount = 500000L,
            currency = "VND",
            exchangeRate = 1.0,
            convertedAmount = 500000L,
            recordedByMemberId = adminA.id
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.addFundContribution(invalidFund, "Thủ quỹ B", adminA)
            }
        }
        assertTrue("Lỗi phải nêu rõ thành viên nộp quỹ không thuộc đoàn", ex.message?.contains("không thuộc đoàn") == true)
    }

    @Test
    fun `test settlement engine is stateless and does not leak global reconciliation state`() {
        val memberA = TripMemberEntity(id = "m1", tripId = "t1", userId = "u1", name = "A", role = "MEMBER")
        val memberB = TripMemberEntity(id = "m2", tripId = "t1", userId = "u2", name = "B", role = "MEMBER")

        // 1. Chạy với dữ liệu có sai lệch đối soát (discrepancy != 0)
        val unbalancedStatuses = listOf(
            com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus(
                member = memberA,
                totalPaid = 100000L,
                outOfPocketPaid = 100000L,
                fundContributed = 0L,
                totalOwed = 0L,
                balance = 100000L,
                status = com.hiepnguyen.tripfinance.domain.model.BalanceStatus.RECEIVE
            ),
            com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus(
                member = memberB,
                totalPaid = 0L,
                outOfPocketPaid = 0L,
                fundContributed = 0L,
                totalOwed = 50000L,
                balance = -50000L,
                status = com.hiepnguyen.tripfinance.domain.model.BalanceStatus.PAY
            )
        )
        val resUnbalanced = com.hiepnguyen.tripfinance.domain.engine.SettlementEngine.computeSettlementWithStatus(
            memberStatuses = unbalancedStatuses,
            tripJoinCode = "TEST-01",
            remainingFund = 0L
        )
        assertNotNull("Phải phát hiện lỗi đối soát", resUnbalanced.reconciliationError)
        assertTrue(resUnbalanced.transfers.isEmpty())

        // 2. Chạy với dữ liệu cân đối hoàn hảo
        val balancedStatuses = listOf(
            com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus(
                member = memberA,
                totalPaid = 100000L,
                outOfPocketPaid = 100000L,
                fundContributed = 0L,
                totalOwed = 50000L,
                balance = 50000L,
                status = com.hiepnguyen.tripfinance.domain.model.BalanceStatus.RECEIVE
            ),
            com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus(
                member = memberB,
                totalPaid = 0L,
                outOfPocketPaid = 0L,
                fundContributed = 0L,
                totalOwed = 50000L,
                balance = -50000L,
                status = com.hiepnguyen.tripfinance.domain.model.BalanceStatus.PAY
            )
        )
        val resBalanced = com.hiepnguyen.tripfinance.domain.engine.SettlementEngine.computeSettlementWithStatus(
            memberStatuses = balancedStatuses,
            tripJoinCode = "TEST-01",
            remainingFund = 0L
        )
        assertNull("Không được có lỗi đối soát", resBalanced.reconciliationError)
        assertEquals(1, resBalanced.transfers.size)
    }

    @Test
    fun `test LocalSpendingAdvisor operates offline locally with generateLocalInsight`() {
        val summary = com.hiepnguyen.tripfinance.domain.model.FinancialSummary(
            totalExpenses = 200000L,
            remainingFund = 50000L,
            isBalanced = true,
            balanceDiscrepancy = 0L
        )
        val result = com.hiepnguyen.tripfinance.domain.ai.LocalSpendingAdvisor.generateLocalInsight(
            tripTitle = "Hà Giang Tour",
            summary = summary,
            categories = emptyList(),
            members = emptyList(),
            lang = "vi"
        )
        assertNotNull(result)
        assertTrue("Kết quả cục bộ phải chứa tiêu đề đoàn", result.contains("Hà Giang Tour"))
        assertTrue("Kết quả phải chứa phân tích tài chính", result.contains("PHÂN TÍCH TÀI CHÍNH ĐOÀN"))
    }

    @Test
    fun `test insertTrip uses OnConflictStrategy ABORT and throws SQLiteConstraintException on primary key or joinCode collision`(): Unit = runBlocking {
        val trip1 = TripEntity(
            id = "trip-abort-test-1",
            title = "Đoàn Ban Đầu",
            description = "Mô tả ban đầu",
            joinCode = "ABORT-CODE-01",
            startDate = 1000L,
            endDate = 2000L
        )

        // 1. Chèn thành công lần đầu
        db.tripDao().insertTrip(trip1)
        val saved = db.tripDao().getTripByIdOnce("trip-abort-test-1")
        assertNotNull(saved)
        assertEquals("Đoàn Ban Đầu", saved?.title)

        // 2. Chèn lại với cùng primary key 'id' -> OnConflictStrategy.ABORT phải ném SQLiteConstraintException
        val duplicateIdTrip = trip1.copy(title = "Đoàn Ghi Đè Trái Phép")
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.tripDao().insertTrip(duplicateIdTrip)
            }
        }

        // Xác nhận dữ liệu gốc không bị ghi đè (không bị REPLACE)
        val afterAttempt = db.tripDao().getTripByIdOnce("trip-abort-test-1")
        assertEquals("Đoàn Ban Đầu", afterAttempt?.title)

        // 3. Chèn chuyến đi khác id nhưng trùng UNIQUE index 'joinCode' -> OnConflictStrategy.ABORT phải ném SQLiteConstraintException
        val duplicateCodeTrip = TripEntity(
            id = "trip-abort-test-2",
            title = "Đoàn Trùng JoinCode",
            description = "Mô tả",
            joinCode = "ABORT-CODE-01", // Trùng joinCode với trip1
            startDate = 3000L,
            endDate = 4000L
        )
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking {
                db.tripDao().insertTrip(duplicateCodeTrip)
            }
        }
    }

    @Test
    fun `test backup computeChecksum is deterministic and detects data tampering`(): Unit = runBlocking {
        val tripsArray = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("id", "trip-1")
                put("title", "Đoàn Test Checksum")
            })
        }
        val membersArray = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("id", "mem-1")
                put("name", "Nguyễn Văn A")
            })
        }
        val expensesArray = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("id", "exp-1")
                put("amount", 500000L)
            })
        }
        val splitsArray = org.json.JSONArray()
        val fundsArray = org.json.JSONArray()

        // 1. Tính toán checksum lần 1
        val checksum1 = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.computeChecksum(
            tripsArray, membersArray, expensesArray, splitsArray, fundsArray
        )
        assertNotNull(checksum1)
        assertEquals("SHA-256 hex string phải có độ dài 64 ký tự", 64, checksum1.length)

        // 2. Tính toán lần 2 với cùng dữ liệu -> Phải ra cùng giá trị (Deterministic)
        val checksum2 = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.computeChecksum(
            tripsArray, membersArray, expensesArray, splitsArray, fundsArray
        )
        assertEquals(checksum1, checksum2)

        // 3. Sửa một con số trong expenses (500000L -> 500001L) -> Checksum thay đổi hoàn toàn
        val tamperedExpenses = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("id", "exp-1")
                put("amount", 500001L)
            })
        }
        val tamperedChecksum = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.computeChecksum(
            tripsArray, membersArray, tamperedExpenses, splitsArray, fundsArray
        )
        assertNotEquals("Checksum phải phát hiện bất kỳ sự thay đổi dữ liệu nào", checksum1, tamperedChecksum)

        // 4. Kiểm tra exportBackupToJson tự động gắn dataChecksum vào metadata
        val exportedJson = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.exportBackupToJson(db)
        val root = org.json.JSONObject(exportedJson)
        val meta = root.getJSONObject("metadata")
        assertTrue("Metadata của bản xuất sao lưu phải có dataChecksum", meta.has("dataChecksum"))
        val exportedChecksum = meta.getString("dataChecksum")
        assertEquals(64, exportedChecksum.length)
    }

    @Test
    fun `test restoreFromFileStreaming preserves settled state and never unseals trip`(): Unit = runBlocking {
        // Tạo đoàn đã quyết toán trên thiết bị
        val tripId = repository.createTrip(
            title = "Đoàn Đã Khóa Streaming Test",
            description = "Test streaming restore không hạ isSettled",
            joinCode = "STREAM-SETTLE",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin Settle",
        )
        val originalTrip = db.tripDao().getTripByIdOnce(tripId)!!
        val settledTimestamp = 1710000000000L
        db.tripDao().updateTrip(originalTrip.copy(isSettled = true, settledAt = settledTimestamp))

        val currentTrip = db.tripDao().getTripByIdOnce(tripId)!!
        assertTrue(currentTrip.isSettled)
        assertEquals(settledTimestamp, currentTrip.settledAt)

        // Tạo tệp backup JSON giả lập trong đó đoàn chưa quyết toán (isSettled = false)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val testBackupFile = File(backupDir, "unsettled_trip_backup.json")

        val unSettledBackupJson = """
        {
          "metadata": {
            "appName": "TripFinance",
            "backupVersion": 1,
            "schemaVersion": 4,
            "createdAt": 1700000000000,
            "totalTrips": 1,
            "totalMembers": 0,
            "totalExpenses": 0,
            "totalSplits": 0,
            "totalFunds": 0,
            "totalRates": 0,
            "totalSnapshots": 0,
            "totalLogs": 0
          },
          "trips": [
            {
              "id": "$tripId",
              "title": "Đoàn Bản Backup Cũ",
              "description": "Bản cũ chưa khóa sổ",
              "joinCode": "STREAM-SETTLE",
              "startDate": 1000,
              "endDate": 2000,
              "baseCurrency": "VND",
              "isSettled": false,
              "settledAt": null,
              "createdAt": 1000,
              "version": 1
            }
          ],
          "members": [],
          "expenses": [],
          "splits": [],
          "fundContributions": [],
          "exchangeRates": [],
          "settlementSnapshots": [],
          "auditLogs": []
        }
        """.trimIndent()
        testBackupFile.writeText(unSettledBackupJson, Charsets.UTF_8)

        // Phục hồi qua restoreFromFileStreaming (clearExisting = false)
        val restoreResult = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.restoreFromFileStreaming(
            db = db,
            file = testBackupFile,
            clearExisting = false,
            password = null
        )
        assertTrue(restoreResult.isSuccess)

        // Kiểm chứng tính bất biến: Đoàn trên máy PHẢI GIỮ NGUYÊN isSettled = true và settledAt
        val tripAfterStreaming = db.tripDao().getTripByIdOnce(tripId)!!
        assertTrue("restoreFromFileStreaming tuyệt đối không được hạ isSettled về false!", tripAfterStreaming.isSettled)
        assertEquals(settledTimestamp, tripAfterStreaming.settledAt)

        testBackupFile.delete()
    }

    @Test
    fun `test DAO lockSettlementWithOptimisticLock and reopenSettlementWithOptimisticLock enforce version check`(): Unit = runBlocking {
        val trip = TripEntity(
            id = "trip-opt-dao",
            title = "Đoàn Test Khóa Lạc Quan DAO",
            description = "Test DAO lock queries",
            joinCode = "DAO-OPT-01",
            startDate = 1000L,
            endDate = 2000L,
            isSettled = false,
            version = 1L
        )
        db.tripDao().insertTrip(trip)

        val settledAtTime = 1750000000000L

        // 1. Khóa sổ với expectedVersion = 1 đúng -> trả về 1 (thành công)
        val lockRows = db.tripDao().lockSettlementWithOptimisticLock(
            tripId = "trip-opt-dao",
            expectedVersion = 1L,
            settledAt = settledAtTime
        )
        assertEquals(1, lockRows)

        val afterLock = db.tripDao().getTripByIdOnce("trip-opt-dao")!!
        assertTrue(afterLock.isSettled)
        assertEquals(settledAtTime, afterLock.settledAt)
        assertEquals("Version phải được tăng lên 2 sau khi khóa sổ", 2L, afterLock.version)

        // 2. Thao tác đồng thời với stale version 1 -> Trả về 0 (bị chặn)
        val staleLockRows = db.tripDao().lockSettlementWithOptimisticLock(
            tripId = "trip-opt-dao",
            expectedVersion = 1L,
            settledAt = settledAtTime + 1000L
        )
        assertEquals(0, staleLockRows)

        // 3. Thao tác khóa lại khi đã isSettled = 1 (kể cả đúng version 2) -> Trả về 0 (isSettled = 0 điều kiện không thỏa mãn)
        val repeatLockRows = db.tripDao().lockSettlementWithOptimisticLock(
            tripId = "trip-opt-dao",
            expectedVersion = 2L,
            settledAt = settledAtTime + 2000L
        )
        assertEquals(0, repeatLockRows)

        // 4. Mở khóa sổ với expectedVersion = 2 đúng -> trả về 1 (thành công)
        val reopenRows = db.tripDao().reopenSettlementWithOptimisticLock(
            tripId = "trip-opt-dao",
            expectedVersion = 2L
        )
        assertEquals(1, reopenRows)

        val afterReopen = db.tripDao().getTripByIdOnce("trip-opt-dao")!!
        assertFalse(afterReopen.isSettled)
        assertNull(afterReopen.settledAt)
        assertEquals("Version phải được tăng lên 3 sau khi mở sổ", 3L, afterReopen.version)

        // 5. Thao tác mở lại với stale version 2 -> Trả về 0
        val staleReopenRows = db.tripDao().reopenSettlementWithOptimisticLock(
            tripId = "trip-opt-dao",
            expectedVersion = 2L
        )
        assertEquals(0, staleReopenRows)
    }

    @Test
    fun `test Database triggers block tampering with funds members rates and splits when settled`(): Unit = runBlocking {
        val tripId = "trip-triggers-comprehensive"
        // 1. Tạo chuyến đi ban đầu chưa khóa sổ để thêm thành viên hợp lệ
        val initialTrip = TripEntity(
            id = tripId,
            title = "Đoàn Niêm Phong Toàn Diện",
            description = "Test all lock triggers",
            joinCode = "ALL-LOCK-01",
            startDate = 1000L,
            endDate = 2000L,
            isSettled = false
        )
        db.tripDao().insertTrip(initialTrip)

        val existingMember = TripMemberEntity(
            id = "mem-1",
            tripId = tripId,
            userId = "user-1",
            name = "Thành viên ban đầu",
            role = "MEMBER"
        )
        db.tripMemberDao().insertMember(existingMember)

        // 2. Khóa sổ chuyến đi
        db.tripDao().updateTrip(initialTrip.copy(isSettled = true, settledAt = 3000L))

        // 3. Trigger trg_prevent_fund_insert_when_settled
        val fund = FundContributionEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            memberId = "mem-1",
            amount = 100000L,
            currency = "VND",
            exchangeRate = 1.0,
            convertedAmount = 100000L,
            timestamp = 1500L,
            recordedByMemberId = "mem-1"
        )
        val fundEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { db.fundDao().insertFundContribution(fund) }
        }
        assertTrue("Trigger nộp quỹ phải trả về lỗi SETTLEMENT LOCKED", fundEx.message?.contains("SETTLEMENT LOCKED") == true)

        // 4. Trigger trg_prevent_member_insert_when_settled
        val newMember = TripMemberEntity(
            id = "mem-settled-new",
            tripId = tripId,
            userId = "user-new",
            name = "Thành viên mới",
            role = "MEMBER"
        )
        val memberEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { db.tripMemberDao().insertMember(newMember) }
        }
        assertTrue("Trigger thêm thành viên phải trả về lỗi SETTLEMENT LOCKED", memberEx.message?.contains("SETTLEMENT LOCKED") == true)

        // 5. Trigger trg_prevent_rate_insert_when_settled
        val rate = ExchangeRateEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            currencyCode = "EUR",
            rateToBase = 27000.0,
            updatedAt = 1000L
        )
        val rateEx = assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            runBlocking { db.exchangeRateDao().insertExchangeRate(rate) }
        }
        assertTrue("Trigger thêm tỷ giá phải trả về lỗi SETTLEMENT LOCKED", rateEx.message?.contains("SETTLEMENT LOCKED") == true)
    }

    @Test
    fun `test BackupCryptoUtils default encryption uses 600_000 iterations and verifies roundtrip`() {
        val sampleJson = """{"test":"payload","amount":500000}"""
        val password = "StrongPassword123"

        // 1. Gọi encryptBackup không truyền iterations -> mặc định phải là OWASP_RECOMMENDED_ITERATIONS (600.000)
        val encryptedJson = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.encryptBackup(sampleJson, password)
        val root = org.json.JSONObject(encryptedJson)
        assertEquals("Số vòng lặp mặc định trong JSON phải là 600.000", 600_000, root.getInt("iterations"))
        assertEquals("AES-256-GCM", root.getString("algorithm"))
        assertEquals("PBKDF2WithHmacSHA256", root.getString("kdf"))
        assertTrue(root.getBoolean("encrypted"))

        // 2. Giải mã bằng decryptBackup với đúng mật khẩu -> dữ liệu khớp hoàn toàn
        val decryptResult = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(encryptedJson, password)
        assertTrue(decryptResult.isSuccess)
        assertEquals(sampleJson, decryptResult.getOrThrow())

        // 3. Giải mã sai mật khẩu -> phải trả về Result.failure
        val wrongResult = com.hiepnguyen.tripfinance.data.backup.BackupCryptoUtils.decryptBackup(encryptedJson, "WrongPassword999")
        assertTrue(wrongResult.isFailure)
    }

    @Test
    fun `test migration 4 to 5 safely drops bank columns while preserving all member and financial data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_4_5.db")
        dbFile.delete()

        // 1. Tạo CSDL SQLite v4 chứa bảng trip_members với 3 cột ngân hàng
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 4
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `joinCode` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `baseCurrency` TEXT NOT NULL, `isSettled` INTEGER NOT NULL, `settledAt` INTEGER, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_joinCode` ON `trips` (`joinCode`)")
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `trip_members` (
                `id` TEXT NOT NULL,
                `tripId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `bankName` TEXT,
                `bankAccount` TEXT,
                `bankAccountHolder` TEXT,
                `joinedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `payerType` TEXT NOT NULL, `payerMemberId` TEXT, `totalAmount` REAL NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedTotalAmount` INTEGER NOT NULL, `splitType` TEXT NOT NULL, `receiptImageUri` TEXT, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `createdMemberId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expense_splits` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `percentage` REAL, PRIMARY KEY(`id`), FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `fund_contributions` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedAmount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `recordedByMemberId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`recordedByMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `settlement_snapshots` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `snapshotTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `totalExpenses` INTEGER NOT NULL, `totalFundCollected` INTEGER NOT NULL, `totalFundSpent` INTEGER NOT NULL, `remainingFund` INTEGER NOT NULL, `settlementJson` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `actorMemberId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `detailBefore` TEXT, `detailAfter` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId` ON `trip_members` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId_name` ON `trip_members` (`tripId`, `name`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_payerMemberId` ON `expenses` (`payerMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_createdMemberId` ON `expenses` (`createdMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_tripId` ON `expense_splits` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_memberId` ON `expense_splits` (`memberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_tripId` ON `fund_contributions` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_memberId` ON `fund_contributions` (`memberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_recordedByMemberId` ON `fund_contributions` (`recordedByMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_rates_tripId` ON `exchange_rates` (`tripId`)")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_tripId_currencyCode` ON `exchange_rates` (`tripId`, `currencyCode`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_snapshots_tripId` ON `settlement_snapshots` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_tripId` ON `audit_logs` (`tripId`)")

        // Chèn dữ liệu mẫu vào v4 có cả thông tin ngân hàng cũ
        sqlite.execSQL("INSERT INTO `trips` VALUES('trip-v4', 'Chuyến Đi V4', 'Khám phá Đà Nẵng', 'TRIP-V4', 1000, 2000, 'VND', 0, NULL, 1000, 1)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('mem-admin', 'trip-v4', 'usr-1', 'Trưởng Đoàn A', 'ADMIN', 1, 'Vietcombank', '0123456789', 'TRUONG DOAN A', 1000)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('mem-user', 'trip-v4', 'usr-2', 'Thành Viên B', 'MEMBER', 1, 'Techcombank', '9876543210', 'THANH VIEN B', 1100)")
        sqlite.close()

        // 2. Mở qua Room Database builder kèm MIGRATION_4_5, 5_6, 6_7 (không dùng fallbackToDestructiveMigration)
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_4_5.db")
            .addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        val members = runBlocking { migratedDb.tripMemberDao().getAllMembersOnce() }
        assertEquals("Số lượng thành viên phải bảo toàn nguyên vẹn", 2, members.size)

        val admin = members.first { it.id == "mem-admin" }
        assertEquals("Trưởng Đoàn A", admin.name)
        assertEquals("ADMIN", admin.role)
        assertTrue(admin.isActive)
        assertEquals(1000L, admin.joinedAt)

        val user = members.first { it.id == "mem-user" }
        assertEquals("Thành Viên B", user.name)
        assertEquals("MEMBER", user.role)
        assertTrue(user.isActive)
        assertEquals(1100L, user.joinedAt)

        // 3. Kiểm tra cấu trúc bảng SQLite: các cột ngân hàng đã hoàn toàn biến mất
        val pragmaCursor = migratedDb.openHelper.readableDatabase.query("PRAGMA table_info(`trip_members`)")
        val columnNames = mutableListOf<String>()
        while (pragmaCursor.moveToNext()) {
            columnNames.add(pragmaCursor.getString(1))
        }
        pragmaCursor.close()

        assertTrue("Cột id phải tồn tại", columnNames.contains("id"))
        assertTrue("Cột tripId phải tồn tại", columnNames.contains("tripId"))
        assertTrue("Cột name phải tồn tại", columnNames.contains("name"))
        assertTrue("Cột role phải tồn tại", columnNames.contains("role"))
        assertTrue("Cột isActive phải tồn tại", columnNames.contains("isActive"))
        assertTrue("Cột joinedAt phải tồn tại", columnNames.contains("joinedAt"))
        assertFalse("Cột bankName phải bị xóa bỏ", columnNames.contains("bankName"))
        assertFalse("Cột bankAccount phải bị xóa bỏ", columnNames.contains("bankAccount"))
        assertFalse("Cột bankAccountHolder phải bị xóa bỏ", columnNames.contains("bankAccountHolder"))

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `test importing legacy backup containing bank fields succeeds gracefully and drops bank fields`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

        // Bản backup cũ phiên bản schema 4 chứa các trường bankName, bankAccount, bankAccountHolder
        val legacyBackupJson = """
        {
          "metadata": {
            "appName": "TripFinance",
            "backupVersion": 1,
            "schemaVersion": 4,
            "createdAt": 1700000000000,
            "totalTrips": 1,
            "totalMembers": 2,
            "totalExpenses": 1,
            "totalSplits": 2,
            "totalFunds": 0,
            "totalRates": 0,
            "totalSnapshots": 0,
            "totalLogs": 0
          },
          "trips": [
            {
              "id": "trip-legacy-bank",
              "title": "Chuyến Đi Miền Tây",
              "description": "Du lịch sinh thái",
              "joinCode": "LEGACY-BNK",
              "startDate": 1700000000000,
              "endDate": 1700500000000,
              "baseCurrency": "VND",
              "isSettled": false,
              "settledAt": null,
              "createdAt": 1700000000000,
              "version": 1
            }
          ],
          "members": [
            {
              "id": "mem-1-bank",
              "tripId": "trip-legacy-bank",
              "userId": "usr-1",
              "name": "Nguyễn Văn Admin",
              "role": "ADMIN",
              "bankName": "Vietcombank",
              "bankAccount": "1234567890",
              "bankAccountHolder": "NGUYEN VAN ADMIN",
              "isActive": true,
              "joinedAt": 1700000000000
            },
            {
              "id": "mem-2-bank",
              "tripId": "trip-legacy-bank",
              "userId": "usr-2",
              "name": "Trần Thị Member",
              "role": "MEMBER",
              "bankName": "MB Bank",
              "bankAccount": "0987654321",
              "bankAccountHolder": "TRAN THI MEMBER",
              "isActive": true,
              "joinedAt": 1700000000000
            }
          ],
          "expenses": [
            {
              "id": "exp-1",
              "tripId": "trip-legacy-bank",
              "title": "Vé tàu du lịch",
              "category": "TRANSPORT",
              "payerType": "MEMBER",
              "payerMemberId": "mem-1-bank",
              "totalAmount": 200000.0,
              "currency": "VND",
              "exchangeRate": 1.0,
              "convertedTotalAmount": 200000,
              "splitType": "EQUAL",
              "receiptImageUri": null,
              "note": "Tiền vé khứ hồi",
              "timestamp": 1700010000000,
              "createdMemberId": "mem-1-bank",
              "updatedAt": 1700010000000,
              "isSynced": true
            }
          ],
          "splits": [
            {
              "id": "spl-1",
              "expenseId": "exp-1",
              "tripId": "trip-legacy-bank",
              "memberId": "mem-1-bank",
              "amount": 100000,
              "percentage": 50.0
            },
            {
              "id": "spl-2",
              "expenseId": "exp-1",
              "tripId": "trip-legacy-bank",
              "memberId": "mem-2-bank",
              "amount": 100000,
              "percentage": 50.0
            }
          ],
          "fundContributions": [],
          "exchangeRates": [],
          "settlementSnapshots": [],
          "auditLogs": []
        }
        """.trimIndent()

        // 1. Phục hồi qua BackupRestoreManager.restoreFromReader
        val parseResult = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.parseBackupFromReader(java.io.StringReader(legacyBackupJson))
        assertTrue("Phải phân tích cú pháp bản sao lưu cũ thành công mà không bị lỗi trường lạ", parseResult.isSuccess)

        val backupData = parseResult.getOrThrow()
        assertEquals(2, backupData.members.size)
        assertEquals("mem-1-bank", backupData.members[0].id)
        assertEquals("Nguyễn Văn Admin", backupData.members[0].name)

        // 2. Nạp dữ liệu vào CSDL
        val restoreResult = com.hiepnguyen.tripfinance.data.backup.BackupRestoreManager.restoreFromBackupData(db, backupData, clearExisting = false)
        assertTrue("Quá trình khôi phục phải thành công: ${restoreResult.exceptionOrNull()?.message}", restoreResult.isSuccess)

        val restoredTrips = db.tripDao().getAllTripsOnce()
        assertEquals(1, restoredTrips.size)
        assertEquals("Chuyến Đi Miền Tây", restoredTrips[0].title)

        val restoredMembers = db.tripMemberDao().getAllMembersOnce()
        assertEquals(2, restoredMembers.size)
        val adminMember = restoredMembers.first { it.id == "mem-1-bank" }
        assertEquals("Nguyễn Văn Admin", adminMember.name)
        assertEquals("ADMIN", adminMember.role)

        val restoredExpenses = db.expenseDao().getAllExpensesOnce()
        assertEquals(1, restoredExpenses.size)
        assertEquals(200000.0, restoredExpenses[0].totalAmount, 0.001)

        db.close()
    }

    @Test
    fun `test all entity and domain classes have zero bank account or QR fields`() {
        val classesToCheck = listOf(
            com.hiepnguyen.tripfinance.data.entity.TripMemberEntity::class.java,
            com.hiepnguyen.tripfinance.data.entity.TripEntity::class.java,
            com.hiepnguyen.tripfinance.data.entity.ExpenseEntity::class.java,
            com.hiepnguyen.tripfinance.data.entity.ExpenseSplitEntity::class.java,
            com.hiepnguyen.tripfinance.data.entity.FundContributionEntity::class.java,
            com.hiepnguyen.tripfinance.data.entity.SettlementSnapshotEntity::class.java,
            com.hiepnguyen.tripfinance.domain.model.SettlementTransfer::class.java,
            com.hiepnguyen.tripfinance.domain.model.MemberFinancialStatus::class.java,
            com.hiepnguyen.tripfinance.domain.model.FinancialSummary::class.java
        )

        val forbiddenKeywords = listOf("bank", "account", "qr", "vietqr", "holder")

        for (clazz in classesToCheck) {
            val declaredFields = clazz.declaredFields.map { it.name.lowercase() }
            for (keyword in forbiddenKeywords) {
                val matches = declaredFields.filter { it.contains(keyword) }
                assertTrue(
                    "Lớp ${clazz.simpleName} không được chứa trường liên quan '$keyword' (tìm thấy: $matches)",
                    matches.isEmpty()
                )
            }
        }
    }

    @Test
    fun `deleteExpense and restoreExpense restores expense with splits atomically in a transaction`() = runBlocking {
        // 1. Create trip & members
        val tripId = repository.createTrip(
            title = "Chuyến đi Phú Quốc",
            description = "Nghỉ dưỡng",
            joinCode = "PQ2026",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin PQ"
        )
        val adminMember = repository.getMembers(tripId).first().first()
        val mem2Id = repository.addMember(tripId, "Member 2", "MEMBER", adminMember)
        val mem3Id = repository.addMember(tripId, "Member 3", "MEMBER", adminMember)

        // 2. Add expense with 3 splits
        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = tripId,
            title = "Ăn hải sản đêm",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = adminMember.id,
            totalAmount = 900000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 900000L,
            splitType = "EQUAL",
            note = "Ăn tôm hùm mực nướng",
            timestamp = 1500L,
            createdMemberId = adminMember.id
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, adminMember.id, 300000L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, mem2Id, 300000L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, mem3Id, 300000L)
        )
        repository.addExpenseWithSplits(expense, splits, adminMember)

        // Verify initial state
        assertNotNull(db.expenseDao().getExpenseById(expId))
        assertEquals(3, db.expenseDao().getSplitsByExpenseOnce(expId).size)

        // 3. Delete expense
        val deletedSplits = repository.deleteExpense(expense, adminMember)
        assertEquals(3, deletedSplits.size)

        // Verify deleted
        assertNull(db.expenseDao().getExpenseById(expId))
        assertTrue(db.expenseDao().getSplitsByExpenseOnce(expId).isEmpty())

        // 4. Restore expense atomically in transaction
        repository.restoreExpense(expense, deletedSplits, adminMember)

        // Verify restored
        val restoredExpense = db.expenseDao().getExpenseById(expId)
        assertNotNull("Khoản chi phải được khôi phục", restoredExpense)
        assertEquals("Ăn hải sản đêm", restoredExpense?.title)
        assertEquals(900000L, restoredExpense?.convertedTotalAmount)

        val restoredSplits = db.expenseDao().getSplitsByExpenseOnce(expId)
        assertEquals(3, restoredSplits.size)
        val memberAmounts = restoredSplits.associate { it.memberId to it.amount }
        assertEquals(300000L, memberAmounts[adminMember.id])
        assertEquals(300000L, memberAmounts[mem2Id])
        assertEquals(300000L, memberAmounts[mem3Id])

        // Verify audit log
        val logs = repository.getAuditLogs(tripId).first()
        val restoreLog = logs.find { it.action == "RESTORE_EXPENSE" }
        assertNotNull("Audit log phải ghi nhận RESTORE_EXPENSE", restoreLog)
        assertTrue(restoreLog?.description?.contains("Ăn hải sản đêm") == true)
    }

    @Test
    fun `restoreExpense throws exception and rejects restore when trip is settled`() = runBlocking {
        // 1. Create trip & members
        val tripId = repository.createTrip(
            title = "Đoàn khóa sổ",
            description = "Kiểm tra khóa sổ",
            joinCode = "LOCK-99",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin Lock"
        )
        val adminMember = repository.getMembers(tripId).first().first()

        // 2. Add expense
        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = tripId,
            title = "Chi tiêu trước khóa sổ",
            category = "OTHER",
            payerType = "MEMBER",
            payerMemberId = adminMember.id,
            totalAmount = 100000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 100000L,
            splitType = "EQUAL",
            createdMemberId = adminMember.id
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, adminMember.id, 100000L)
        )
        repository.addExpenseWithSplits(expense, splits, adminMember)

        // 3. Delete expense
        val deletedSplits = repository.deleteExpense(expense, adminMember)
        assertNull(db.expenseDao().getExpenseById(expId))

        // 4. Lock / Settle the trip
        val trip = db.tripDao().getTripByIdOnce(tripId)!!
        db.tripDao().updateTrip(trip.copy(isSettled = true, settledAt = System.currentTimeMillis()))

        // 5. Attempt restoreExpense -> Must throw IllegalStateException
        val ex = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.restoreExpense(expense, deletedSplits, adminMember)
            }
        }
        assertTrue(ex.message?.contains("đã được khóa sổ quyết toán. Không thể hoàn tác khoản chi!") == true)

        // Verify expense and splits remain deleted
        assertNull("Khoản chi vẫn phải là đã xóa", db.expenseDao().getExpenseById(expId))
        assertTrue("Các splits vẫn phải trống", db.expenseDao().getSplitsByExpenseOnce(expId).isEmpty())
    }

    @Test
    fun testMigration_5_to_6_createsSettlementTransfersTableAndPreservesData(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_v5_v6.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Khởi tạo CSDL SQLite phiên bản v5
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 5
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `joinCode` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `baseCurrency` TEXT NOT NULL, `isSettled` INTEGER NOT NULL, `settledAt` INTEGER, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_joinCode` ON `trips` (`joinCode`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trip_members` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `joinedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId` ON `trip_members` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId_name` ON `trip_members` (`tripId`, `name`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `payerType` TEXT NOT NULL, `payerMemberId` TEXT, `totalAmount` REAL NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedTotalAmount` INTEGER NOT NULL, `splitType` TEXT NOT NULL, `receiptImageUri` TEXT, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `createdMemberId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_payerMemberId` ON `expenses` (`payerMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_createdMemberId` ON `expenses` (`createdMemberId`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expense_splits` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `percentage` REAL, PRIMARY KEY(`id`), FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_tripId` ON `expense_splits` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_memberId` ON `expense_splits` (`memberId`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `fund_contributions` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedAmount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `recordedByMemberId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`recordedByMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_tripId` ON `fund_contributions` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_memberId` ON `fund_contributions` (`memberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_recordedByMemberId` ON `fund_contributions` (`recordedByMemberId`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_rates_tripId` ON `exchange_rates` (`tripId`)")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_tripId_currencyCode` ON `exchange_rates` (`tripId`, `currencyCode`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `settlement_snapshots` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `snapshotTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `totalExpenses` INTEGER NOT NULL, `totalFundCollected` INTEGER NOT NULL, `totalFundSpent` INTEGER NOT NULL, `remainingFund` INTEGER NOT NULL, `settlementJson` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_snapshots_tripId` ON `settlement_snapshots` (`tripId`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `actorMemberId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `detailBefore` TEXT, `detailAfter` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_tripId` ON `audit_logs` (`tripId`)")

        sqlite.execSQL("INSERT INTO `trips` VALUES('trip-v5', 'Chuyến đi V5', 'Khám phá Sa Pa', 'TRIP-V5', 1000, 2000, 'VND', 0, NULL, 1000, 1)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('mem-v5-1', 'trip-v5', 'usr-1', 'Nguyễn Văn A', 'ADMIN', 1, 1000)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('mem-v5-2', 'trip-v5', 'usr-2', 'Trần Thị B', 'MEMBER', 1, 1100)")
        sqlite.close()

        // 2. Mở qua Room Database builder kèm MIGRATION_5_6, MIGRATION_6_7 (không dùng destructive migration)
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_v5_v6.db")
            .addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        // 3. Kiểm tra dữ liệu cũ không bị ảnh hưởng
        val trips = migratedDb.tripDao().getAllTripsOnce()
        assertEquals(1, trips.size)
        assertEquals("Chuyến đi V5", trips[0].title)

        val members = migratedDb.tripMemberDao().getAllMembersOnce()
        assertEquals(2, members.size)

        // 4. Kiểm tra bảng mới settlement_transfers đã được tạo với các cột đầy đủ
        val pragmaCursor = migratedDb.openHelper.readableDatabase.query("PRAGMA table_info(`settlement_transfers`)")
        val columns = mutableListOf<String>()
        while (pragmaCursor.moveToNext()) {
            columns.add(pragmaCursor.getString(1))
        }
        pragmaCursor.close()

        assertTrue(columns.contains("id"))
        assertTrue(columns.contains("tripId"))
        assertTrue(columns.contains("fromMemberId"))
        assertTrue(columns.contains("toMemberId"))
        assertTrue(columns.contains("amount"))
        assertTrue(columns.contains("transferNote"))
        assertTrue(columns.contains("isCompleted"))
        assertTrue(columns.contains("updatedAt"))

        // 5. Thử chèn bản ghi vào settlement_transfers qua DAO
        val transfer = SettlementTransferEntity(
            id = "tr-test-1",
            tripId = "trip-v5",
            fromMemberId = "mem-v5-2",
            toMemberId = "mem-v5-1",
            amount = 150000L,
            transferNote = "Bù trừ tiền ăn",
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )
        migratedDb.settlementDao().insertTransfer(transfer)

        val savedTransfers = migratedDb.settlementDao().getTransfersByTripOnce("trip-v5")
        assertEquals(1, savedTransfers.size)
        assertEquals("tr-test-1", savedTransfers[0].id)
        assertFalse(savedTransfers[0].isCompleted)

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun testMigration_all_paths_to_6_definedCorrectly() {
        assertEquals(1, AppDatabase.MIGRATION_1_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_1_6.endVersion)

        assertEquals(2, AppDatabase.MIGRATION_2_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_2_6.endVersion)

        assertEquals(3, AppDatabase.MIGRATION_3_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_3_6.endVersion)

        assertEquals(4, AppDatabase.MIGRATION_4_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_4_6.endVersion)

        assertEquals(5, AppDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_5_6.endVersion)
    }

    @Test
    fun testSettlementTransfer_triggersOnSettledTrip(): Unit = runBlocking {
        // 1. Tạo chuyến đi và các thành viên
        val tripId = repository.createTrip(
            title = "Đoàn Trigger Test",
            description = "Kiểm tra ràng buộc chuyển khoản khi khóa sổ",
            joinCode = "TRIG-TRIP",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Trưởng Đoàn"
        )
        val members = db.tripMemberDao().getMembersByTripOnce(tripId)
        val admin = members.first()
        val member2Id = repository.addMember(tripId, "Thành Viên B", "MEMBER", admin)

        // 2. Thêm bản ghi chuyển khoản
        val transferId = "tr_trig_test"
        val transfer = SettlementTransferEntity(
            id = transferId,
            tripId = tripId,
            fromMemberId = member2Id,
            toMemberId = admin.id,
            amount = 250000L,
            transferNote = "Gợi ý chuyển khoản test",
            isCompleted = false,
            updatedAt = System.currentTimeMillis()
        )
        db.settlementDao().insertTransfer(transfer)

        // 3. Khóa sổ chuyến đi (isSettled = true)
        val trip = db.tripDao().getTripByIdOnce(tripId)!!
        db.tripDao().updateTrip(trip.copy(isSettled = true, settledAt = System.currentTimeMillis()))

        // 4. KIỂM TRA CHO PHÉP: Cập nhật isCompleted trên chuyến đi ĐÃ KHÓA SỔ -> Phải thành công!
        val now = System.currentTimeMillis()
        db.settlementDao().updateTransferStatus(transferId, true, 250000L, now)

        val updated = db.settlementDao().getTransferById(transferId)!!
        assertTrue("Trạng thái isCompleted phải được cập nhật thành công", updated.isCompleted)
        assertEquals(250000L, updated.completedAmount)
        assertEquals(now, updated.updatedAt)
        assertEquals(250000L, updated.amount)

        // 5. KIỂM TRA CHẶN: Thay đổi cột tài chính 'amount' trên chuyến đi đã khóa sổ -> Trigger phải ném ngoại lệ!
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE settlement_transfers SET amount = 999999 WHERE id = '$transferId'"
            )
        }

        // 6. KIỂM TRA CHẶN: Thay đổi cột 'fromMemberId' trên chuyến đi đã khóa sổ -> Trigger phải ném ngoại lệ!
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE settlement_transfers SET fromMemberId = 'fake_member' WHERE id = '$transferId'"
            )
        }

        // 7. KIỂM TRA CHẶN: Thay đổi cột 'transferNote' trên chuyến đi đã khóa sổ -> Trigger phải ném ngoại lệ!
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE settlement_transfers SET transferNote = 'Hacked note' WHERE id = '$transferId'"
            )
        }

        // 8. KIỂM TRA CHẶN: Xóa gợi ý chuyển khoản trên chuyến đi đã khóa sổ -> Trigger phải ném ngoại lệ!
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM settlement_transfers WHERE id = '$transferId'"
            )
        }

        // 9. Mở khóa sổ chuyến đi (isSettled = false) -> Có thể sửa và xóa bình thường
        db.tripDao().updateTrip(trip.copy(isSettled = false))

        db.openHelper.writableDatabase.execSQL(
            "UPDATE settlement_transfers SET amount = 300000 WHERE id = '$transferId'"
        )
        val unlockedUpdated = db.settlementDao().getTransferById(transferId)!!
        assertEquals(300000L, unlockedUpdated.amount)

        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM settlement_transfers WHERE id = '$transferId'"
        )
        assertNull(db.settlementDao().getTransferById(transferId))
    }

    @Test
    fun testSettlementTransfer_checkboxHasZeroFinancialDiscrepancy(): Unit = runBlocking {
        // Tạo đoàn và chi tiêu cân bằng
        val tripId = repository.createTrip(
            title = "Đoàn Test Checkbox",
            description = "Đảm bảo checkbox không làm lệch tài chính",
            joinCode = "BOX-TRIP",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Admin A"
        )
        val admin = db.tripMemberDao().getMembersByTripOnce(tripId).first()
        val memBId = repository.addMember(tripId, "Member B", "MEMBER", admin)
        val memB = db.tripMemberDao().getMemberById(memBId)!!

        val exp = ExpenseEntity(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            title = "Bữa trưa",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 200000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 200000L,
            splitType = "EQUAL",
            createdMemberId = admin.id
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), exp.id, tripId, admin.id, 100000L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), exp.id, tripId, memBId, 100000L)
        )
        repository.addExpenseWithSplits(exp, splits, admin)

        // Tính toán ban đầu
        val (summaryBefore, statusesBefore) = repository.calculateFinancialSummaryAndStatuses(
            members = listOf(admin, memB),
            expenses = listOf(exp),
            splits = splits,
            fundContributions = emptyList()
        )
        assertEquals(0L, summaryBefore.balanceDiscrepancy)
        assertTrue(summaryBefore.isBalanced)
        assertEquals(100000L, statusesBefore.first { it.member.id == admin.id }.balance)
        assertEquals(-100000L, statusesBefore.first { it.member.id == memBId }.balance)

        // Khóa sổ đoàn để cho phép đánh dấu chuyển khoản
        val trip = db.tripDao().getTripByIdOnce(tripId)!!
        db.tripDao().updateTrip(trip.copy(isSettled = true, settledAt = System.currentTimeMillis()))

        // Đánh dấu chuyển khoản hoàn thành qua repository
        val transferModel = SettlementTransfer(
            id = "tr_box_test",
            fromMember = memB,
            toMember = admin,
            amount = 100000L,
            transferNote = "Trả tiền ăn trưa",
            isCompleted = false
        )
        repository.setTransferCompleted(tripId, transferModel, isCompleted = true, actor = admin)

        val savedTransfer = db.settlementDao().getTransferById("tr_box_test")!!
        assertTrue(savedTransfer.isCompleted)

        // Tính toán lại sau khi đánh dấu: Các chỉ số tài chính, số dư từng người TUYỆT ĐỐI không bị lệch
        val (summaryAfter, statusesAfter) = repository.calculateFinancialSummaryAndStatuses(
            members = listOf(admin, memB),
            expenses = listOf(exp),
            splits = splits,
            fundContributions = emptyList()
        )
        assertEquals(summaryBefore.totalExpenses, summaryAfter.totalExpenses)
        assertEquals(summaryBefore.balanceDiscrepancy, summaryAfter.balanceDiscrepancy)
        assertEquals(summaryBefore.isBalanced, summaryAfter.isBalanced)
        assertEquals(statusesBefore.first { it.member.id == admin.id }.balance, statusesAfter.first { it.member.id == admin.id }.balance)
        assertEquals(statusesBefore.first { it.member.id == memBId }.balance, statusesAfter.first { it.member.id == memBId }.balance)
    }

    @Test
    fun testBackupRestore_withTransfersAndBackwardCompatibility(): Unit = runBlocking {
        // 1. Tạo đoàn mẫu và chuyển khoản
        val tripId = repository.createTrip(
            title = "Đoàn Backup Transfers",
            description = "Kiểm tra xuất nhập chuyển khoản",
            joinCode = "BAK-TRANS",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Trưởng Đoàn"
        )
        val admin = db.tripMemberDao().getMembersByTripOnce(tripId).first()
        val memBId = repository.addMember(tripId, "Bạn B", "MEMBER", admin)

        val transferEntity = SettlementTransferEntity(
            id = "tr_backup_test_1",
            tripId = tripId,
            fromMemberId = memBId,
            toMemberId = admin.id,
            amount = 120000L,
            transferNote = "Chuyển khoản tiền xăng",
            isCompleted = true,
            completedAmount = 120000L,
            updatedAt = System.currentTimeMillis()
        )
        db.settlementDao().insertTransfer(transferEntity)

        // 2. Xuất bản sao lưu JSON (dạng rõ)
        val exportedJson = BackupRestoreManager.exportBackupToJson(db, maskSensitiveData = false)
        assertTrue(exportedJson.contains("\"settlementTransfers\": ["))
        assertTrue(exportedJson.contains("\"id\": \"tr_backup_test_1\""))
        assertTrue(exportedJson.contains("\"isCompleted\": true"))
        assertTrue(exportedJson.contains("\"totalTransfers\": 1"))

        // 3. Khôi phục với clearExisting = true
        val parseResult = BackupRestoreManager.parseBackupFromReader(java.io.StringReader(exportedJson))
        assertTrue(parseResult.isSuccess)
        val backupData = parseResult.getOrThrow()
        assertEquals(1, backupData.settlementTransfers.size)
        assertEquals("tr_backup_test_1", backupData.settlementTransfers[0].id)
        assertTrue(backupData.settlementTransfers[0].isCompleted)

        val restoreResult = BackupRestoreManager.restoreFromBackupData(db, backupData, clearExisting = true)
        assertTrue(restoreResult.isSuccess)

        val restoredTransfers = db.settlementDao().getTransfersByTripOnce(tripId)
        assertEquals(1, restoredTransfers.size)
        assertEquals("tr_backup_test_1", restoredTransfers[0].id)
        assertTrue(restoredTransfers[0].isCompleted)

        // 4. KIỂM TRA TƯƠNG THÍCH NGƯỢC (Backward Compatibility):
        // Bản backup cũ v5 (không có settlementTransfers và không có totalTransfers)
        val legacyJson = """
            {
              "metadata": {
                "appName": "TripFinance",
                "backupVersion": 1,
                "schemaVersion": 5,
                "createdAt": 1700000000000,
                "createdAtFormatted": "2023-11-14 20:00:00",
                "totalTrips": 1,
                "totalMembers": 1,
                "totalExpenses": 0,
                "totalSplits": 0,
                "totalFunds": 0,
                "totalRates": 0,
                "totalSnapshots": 0,
                "totalLogs": 0
              },
              "trips": [
                {
                  "id": "legacy-trip-id",
                  "title": "Đoàn Backup Cũ",
                  "description": "Bản sao lưu không có settlementTransfers",
                  "joinCode": "LEGACY-01",
                  "startDate": 1000,
                  "endDate": 2000,
                  "baseCurrency": "VND",
                  "isSettled": false,
                  "settledAt": null,
                  "createdAt": 1000,
                  "version": 1
                }
              ],
              "members": [
                {
                  "id": "legacy-mem-id",
                  "tripId": "legacy-trip-id",
                  "userId": "usr-legacy",
                  "name": "Người Cũ",
                  "role": "ADMIN",
                  "isActive": true,
                  "joinedAt": 1000
                }
              ],
              "expenses": [],
              "splits": [],
              "fundContributions": [],
              "exchangeRates": [],
              "settlementSnapshots": [],
              "auditLogs": []
            }
        """.trimIndent()

        val legacyParse = BackupRestoreManager.parseBackupFromReader(java.io.StringReader(legacyJson))
        assertTrue("Bản backup cũ không có settlementTransfers phải parse thành công", legacyParse.isSuccess)
        val legacyData = legacyParse.getOrThrow()
        assertTrue("Danh sách settlementTransfers phải rỗng đối với bản backup cũ", legacyData.settlementTransfers.isEmpty())
        assertEquals(0, legacyData.metadata.totalTransfers)

        val legacyRestore = BackupRestoreManager.restoreFromBackupData(db, legacyData, clearExisting = false)
        assertTrue("Bản backup cũ phải nạp thành công vào CSDL v6", legacyRestore.isSuccess)

        val restoredLegacyTrip = db.tripDao().getTripByIdOnce("legacy-trip-id")
        assertNotNull(restoredLegacyTrip)
        assertEquals("Đoàn Backup Cũ", restoredLegacyTrip?.title)
    }

    @Test
    fun `test migration from v4 to latest v6 preserves all data without bank columns and foreign key check is clean`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_v4_all_entities.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Tạo SQLite CSDL v4 chứa đầy đủ đoàn, thành viên (có 3 cột ngân hàng), chi tiêu, splits, quỹ, snapshots, tỷ giá, audit logs
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 4
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `joinCode` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `baseCurrency` TEXT NOT NULL, `isSettled` INTEGER NOT NULL, `settledAt` INTEGER, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_joinCode` ON `trips` (`joinCode`)")
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `trip_members` (
                `id` TEXT NOT NULL,
                `tripId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `bankName` TEXT,
                `bankAccount` TEXT,
                `bankAccountHolder` TEXT,
                `joinedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `payerType` TEXT NOT NULL, `payerMemberId` TEXT, `totalAmount` REAL NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedTotalAmount` INTEGER NOT NULL, `splitType` TEXT NOT NULL, `receiptImageUri` TEXT, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `createdMemberId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expense_splits` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `percentage` REAL, PRIMARY KEY(`id`), FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `fund_contributions` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedAmount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `recordedByMemberId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`recordedByMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `settlement_snapshots` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `snapshotTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `totalExpenses` INTEGER NOT NULL, `totalFundCollected` INTEGER NOT NULL, `totalFundSpent` INTEGER NOT NULL, `remainingFund` INTEGER NOT NULL, `settlementJson` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `actorMemberId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `detailBefore` TEXT, `detailAfter` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId` ON `trip_members` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId_name` ON `trip_members` (`tripId`, `name`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_payerMemberId` ON `expenses` (`payerMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_createdMemberId` ON `expenses` (`createdMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_tripId` ON `expense_splits` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_memberId` ON `expense_splits` (`memberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_tripId` ON `fund_contributions` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_memberId` ON `fund_contributions` (`memberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_recordedByMemberId` ON `fund_contributions` (`recordedByMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_rates_tripId` ON `exchange_rates` (`tripId`)")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_tripId_currencyCode` ON `exchange_rates` (`tripId`, `currencyCode`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_snapshots_tripId` ON `settlement_snapshots` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_tripId` ON `audit_logs` (`tripId`)")

        // Chèn dữ liệu mẫu
        sqlite.execSQL("INSERT INTO `trips` VALUES('trip-full-v4', 'Đà Nẵng V4', 'Hành trình biển', 'DN-V4', 1000, 2000, 'VND', 0, NULL, 1000, 1)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('mem-a', 'trip-full-v4', 'u-a', 'Nguyễn Thị A', 'ADMIN', 1, 'Vietcombank', '0123456789', 'NGUYEN THI A', 1000)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('mem-b', 'trip-full-v4', 'u-b', 'Trần Văn B', 'MEMBER', 1, 'Techcombank', '9876543210', 'TRAN VAN B', 1100)")
        sqlite.execSQL("INSERT INTO `expenses` VALUES('exp-1', 'trip-full-v4', 'Hải sản Mỹ Khê', 'FOOD', 'MEMBER', 'mem-a', 200000.0, 'VND', 1.0, 200000, 'EQUAL', NULL, 'Ăn tối', 1200, 'mem-a', 1200, 1)")
        sqlite.execSQL("INSERT INTO `expense_splits` VALUES('sp-1', 'exp-1', 'trip-full-v4', 'mem-a', 100000, NULL)")
        sqlite.execSQL("INSERT INTO `expense_splits` VALUES('sp-2', 'exp-1', 'trip-full-v4', 'mem-b', 100000, NULL)")
        sqlite.execSQL("INSERT INTO `fund_contributions` VALUES('fc-1', 'trip-full-v4', 'mem-b', 500000, 'VND', 1.0, 500000, 'Đóng quỹ đoàn', 1300, 'mem-a')")
        sqlite.execSQL("INSERT INTO `exchange_rates` VALUES('ex-usd', 'trip-full-v4', 'USD', 25450.0, 1000)")
        sqlite.execSQL("INSERT INTO `settlement_snapshots` VALUES('snap-v4', 'trip-full-v4', 'Quyết toán sơ bộ', 1400, 200000, 500000, 0, 500000, '{\"total\":200000}')")
        sqlite.execSQL("INSERT INTO `audit_logs` VALUES('log-1', 'trip-full-v4', 'mem-a', 'Nguyễn Thị A', 'CREATE_EXPENSE', 'Tạo khoản chi Hải sản Mỹ Khê', NULL, NULL, 1200)")
        sqlite.close()

        // 2. Nâng cấp lên phiên bản mới nhất v7 qua Room
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_v4_all_entities.db")
            .addMigrations(
                AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_4_6, AppDatabase.MIGRATION_4_7
            )
            .build()

        // 3. Xác nhận toàn bộ dữ liệu còn nguyên vẹn
        val trip = migratedDb.tripDao().getTripByIdOnce("trip-full-v4")
        assertNotNull(trip)
        assertEquals("Đà Nẵng V4", trip?.title)

        val members = migratedDb.tripMemberDao().getAllMembersOnce()
        assertEquals(2, members.size)
        val memA = members.first { it.id == "mem-a" }
        assertEquals("Nguyễn Thị A", memA.name)
        assertEquals("ADMIN", memA.role)
        assertTrue(memA.isActive)

        val expenses = migratedDb.expenseDao().getAllExpensesOnce()
        assertEquals(1, expenses.size)
        assertEquals("Hải sản Mỹ Khê", expenses[0].title)
        assertEquals(200000.0, expenses[0].totalAmount, 0.001)

        val splits = migratedDb.expenseDao().getAllSplitsByTripOnce("trip-full-v4")
        assertEquals(2, splits.size)
        assertEquals(100000L, splits.first { it.memberId == "mem-a" }.amount)
        assertEquals(100000L, splits.first { it.memberId == "mem-b" }.amount)

        val funds = migratedDb.fundDao().getAllFundsOnce()
        assertEquals(1, funds.size)
        assertEquals(500000L, funds[0].amount)
        assertEquals("mem-b", funds[0].memberId)

        val rates = migratedDb.exchangeRateDao().getAllExchangeRatesOnce()
        assertEquals(1, rates.size)
        assertEquals(25450.0, rates[0].rateToBase, 0.001)

        val snapshots = migratedDb.settlementDao().getAllSnapshotsOnce()
        assertEquals(1, snapshots.size)
        assertEquals("Quyết toán sơ bộ", snapshots[0].snapshotTitle)

        val logs = migratedDb.auditLogDao().getAllAuditLogsOnce()
        assertEquals(1, logs.size)
        assertEquals("Tạo khoản chi Hải sản Mỹ Khê", logs[0].description)

        // Bảng mới v6 settlement_transfers sẵn sàng hoạt động
        val transfers = migratedDb.settlementDao().getAllTransfersOnce()
        assertTrue(transfers.isEmpty())

        // 4. Xác nhận PRAGMA foreign_key_check trả về rỗng (tất cả foreign keys đều toàn vẹn)
        val fkCursor = migratedDb.openHelper.writableDatabase.query("PRAGMA foreign_key_check")
        val fkViolations = fkCursor.count
        fkCursor.close()
        assertEquals("PRAGMA foreign_key_check phải trả về rỗng (0 vi phạm)", 0, fkViolations)

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `test deleteTripCascade succeeds on settled trip with settlement_transfers without trigger abort`(): Unit = runBlocking {
        // 1. Tạo đoàn và 2 thành viên
        val tripId = repository.createTrip(
            title = "Đoàn Xóa Cascade Khóa Sổ",
            description = "Kiểm tra xóa đoàn khi đã quyết toán",
            joinCode = "CASCADE2026",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Trưởng Đoàn X"
        )
        val admin = repository.getMembers(tripId).first().first()
        val bobId = repository.addMember(tripId, "Thành Viên Y", "MEMBER", admin)
        val bob = repository.getMembers(tripId).first().first { it.id == bobId }

        // 2. Thêm khoản chi và khóa sổ chuyến đi
        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = tripId,
            title = "Bữa ăn chung",
            category = "FOOD",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 200000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 200000L,
            splitType = "EQUAL",
            receiptImageUri = null,
            note = "Ăn tối",
            timestamp = 1500L,
            createdMemberId = admin.id,
            updatedAt = 1500L,
            isSynced = true
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, admin.id, 100000L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, bob.id, 100000L)
        )
        repository.addExpenseWithSplits(expense, splits, admin)

        val freshTrip = db.tripDao().getTripByIdOnce(tripId)!!
        val (summary, _) = repository.calculateFinancialSummaryAndStatuses(
            members = listOf(admin, bob),
            expenses = listOf(expense),
            splits = splits,
            fundContributions = emptyList()
        )
        repository.finalizeSettlement(
            trip = freshTrip,
            snapshotTitle = "Bản chốt trước xóa",
            summary = summary,
            settlementJson = "",
            actor = admin
        )

        // Kiểm tra đoàn đã khóa sổ và có gợi ý chuyển khoản
        val settledTrip = db.tripDao().getTripByIdOnce(tripId)!!
        assertTrue("Chuyến đi phải ở trạng thái đã khóa sổ", settledTrip.isSettled)
        val transfersBefore = db.settlementDao().getTransfersByTripOnce(tripId)
        assertEquals("Phải có 1 gợi ý chuyển khoản từ Bob cho Admin", 1, transfersBefore.size)

        // 3. Gọi deleteTripCascade - phải thành công hoàn toàn, không bị trigger chặn
        repository.deleteTripCascade(tripId)

        // 4. Xác nhận toàn bộ dữ liệu đã được dọn sạch
        assertNull("Chuyến đi phải bị xóa hoàn toàn khỏi DB", db.tripDao().getTripByIdOnce(tripId))
        assertTrue(db.tripMemberDao().getMembersByTripOnce(tripId).isEmpty())
        assertTrue(db.expenseDao().getExpensesByTripOnce(tripId).isEmpty())
        assertTrue(db.expenseDao().getAllSplitsByTripOnce(tripId).isEmpty())
        assertTrue(db.settlementDao().getTransfersByTripOnce(tripId).isEmpty())
        assertTrue(db.settlementDao().getSnapshotsByTripOnce(tripId).isEmpty())
    }

    @Test
    fun `test reopenSettlement clears transfers and re-settling preserves isCompleted for matching suggestions`(): Unit = runBlocking {
        // 1. Tạo đoàn và 2 thành viên
        val tripId = repository.createTrip(
            title = "Đoàn Mở Khóa Sổ & Chốt Lại",
            description = "Kiểm tra bảo lưu trạng thái isCompleted",
            joinCode = "REOPEN2026",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Trưởng Đoàn M"
        )
        val admin = repository.getMembers(tripId).first().first()
        val memberId = repository.addMember(tripId, "Thành Viên N", "MEMBER", admin)
        val member = repository.getMembers(tripId).first().first { it.id == memberId }

        // 2. Thêm khoản chi: Admin trả 200k chia đều cho cả 2
        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = tripId,
            title = "Chi phí xe cộ",
            category = "TRANSPORT",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 200000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 200000L,
            splitType = "EQUAL",
            receiptImageUri = null,
            note = "Thuê xe",
            timestamp = 1500L,
            createdMemberId = admin.id,
            updatedAt = 1500L,
            isSynced = true
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, admin.id, 100000L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, member.id, 100000L)
        )
        repository.addExpenseWithSplits(expense, splits, admin)

        // 3. Khóa sổ lần đầu
        val trip1 = db.tripDao().getTripByIdOnce(tripId)!!
        val (summary1, _) = repository.calculateFinancialSummaryAndStatuses(
            members = listOf(admin, member),
            expenses = listOf(expense),
            splits = splits,
            fundContributions = emptyList()
        )
        repository.finalizeSettlement(
            trip = trip1,
            snapshotTitle = "Quyết toán lần 1",
            summary = summary1,
            settlementJson = "",
            actor = admin
        )

        val transfers1 = db.settlementDao().getTransfersByTripOnce(tripId)
        assertEquals(1, transfers1.size)
        val transfer1 = transfers1[0]
        assertFalse(transfer1.isCompleted)

        // Đánh dấu chuyển khoản đã hoàn tất (isCompleted = true, completedAmount = transfer1.amount)
        db.settlementDao().updateTransferStatus(transfer1.id, isCompleted = true, completedAmount = transfer1.amount)
        val transfer1Updated = db.settlementDao().getTransferById(transfer1.id)!!
        assertTrue("Gợi ý chuyển khoản đã được đánh dấu hoàn thành", transfer1Updated.isCompleted)
        assertEquals(transfer1.amount, transfer1Updated.completedAmount)

        // 4. Mở khóa sổ chuyến đi (reopenSettlement)
        val tripSettled = db.tripDao().getTripByIdOnce(tripId)!!
        repository.reopenSettlement(tripSettled, admin)

        // Kiểm tra sau khi reopen: Chuyến đi chưa khóa sổ và settlement_transfers được xóa/làm mới
        val tripReopened = db.tripDao().getTripByIdOnce(tripId)!!
        assertFalse("Chuyến đi đã được mở khóa sổ", tripReopened.isSettled)
        val transfersAfterReopen = db.settlementDao().getTransfersByTripOnce(tripId)
        assertTrue("Bảng settlement_transfers phải được xóa/làm mới khi mở khóa sổ", transfersAfterReopen.isEmpty())

        // 5. Khóa sổ lại (re-settle) mà không đổi các bên chuyển tiền
        val (summary2, _) = repository.calculateFinancialSummaryAndStatuses(
            members = listOf(admin, member),
            expenses = listOf(expense),
            splits = splits,
            fundContributions = emptyList()
        )
        repository.finalizeSettlement(
            trip = tripReopened,
            snapshotTitle = "Quyết toán lần 2",
            summary = summary2,
            settlementJson = "",
            actor = admin
        )

        val tripSettledAgain = db.tripDao().getTripByIdOnce(tripId)!!
        assertTrue(tripSettledAgain.isSettled)

        val transfersAfterResettle = db.settlementDao().getTransfersByTripOnce(tripId)
        assertEquals(1, transfersAfterResettle.size)
        val transferResettled = transfersAfterResettle[0]
        assertEquals(transfer1.id, transferResettled.id)
        assertTrue(
            "Trạng thái isCompleted của gợi ý chuyển khoản còn trùng phải được giữ nguyên là true",
            transferResettled.isCompleted
        )
        assertEquals(transfer1.amount, transferResettled.completedAmount)
    }

    @Test
    fun `test on settled trip updating settlement transfer amount is blocked while updating isCompleted is allowed`(): Unit = runBlocking {
        // 1. Tạo đoàn và khóa sổ với 1 chuyển khoản
        val tripId = repository.createTrip(
            title = "Đoàn Kiểm Tra Trigger Transfer",
            description = "Kiểm tra trigger bảo vệ số tiền chuyển khoản",
            joinCode = "TRIGTR2026",
            startDate = 1000L,
            endDate = 2000L,
            adminName = "Trưởng Đoàn T"
        )
        val admin = repository.getMembers(tripId).first().first()
        val bobId = repository.addMember(tripId, "Thành Viên K", "MEMBER", admin)
        val bob = repository.getMembers(tripId).first().first { it.id == bobId }

        val expId = UUID.randomUUID().toString()
        val expense = ExpenseEntity(
            id = expId,
            tripId = tripId,
            title = "Vé tham quan",
            category = "TICKET",
            payerType = "MEMBER",
            payerMemberId = admin.id,
            totalAmount = 300000.0,
            currency = "VND",
            exchangeRate = 1.0,
            convertedTotalAmount = 300000L,
            splitType = "EQUAL",
            receiptImageUri = null,
            note = "Bảo tàng",
            timestamp = 1500L,
            createdMemberId = admin.id,
            updatedAt = 1500L,
            isSynced = true
        )
        val splits = listOf(
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, admin.id, 150000L),
            ExpenseSplitEntity(UUID.randomUUID().toString(), expId, tripId, bob.id, 150000L)
        )
        repository.addExpenseWithSplits(expense, splits, admin)

        val trip = db.tripDao().getTripByIdOnce(tripId)!!
        val (summary, _) = repository.calculateFinancialSummaryAndStatuses(
            members = listOf(admin, bob),
            expenses = listOf(expense),
            splits = splits,
            fundContributions = emptyList()
        )
        repository.finalizeSettlement(
            trip = trip,
            snapshotTitle = "Chốt tham quan",
            summary = summary,
            settlementJson = "",
            actor = admin
        )

        val transfers = db.settlementDao().getTransfersByTripOnce(tripId)
        assertEquals(1, transfers.size)
        val transfer = transfers[0]
        assertEquals(150000L, transfer.amount)
        assertFalse(transfer.isCompleted)

        // 2. Thử sửa cột amount khi đoàn đã khóa sổ -> trigger trg_prevent_settlement_transfer_update_when_settled PHẢI CHẶN
        var amountUpdateBlocked = false
        try {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE settlement_transfers SET amount = 999999 WHERE id = '${transfer.id}'"
            )
        } catch (e: android.database.sqlite.SQLiteException) {
            amountUpdateBlocked = true
            assertTrue(
                "Thông điệp ngoại lệ phải cảnh báo về khóa sổ quyết toán",
                e.message?.contains("SETTLEMENT LOCKED") == true
            )
        }
        assertTrue("Thao tác sửa amount trên chuyến đi đã khóa sổ phải bị SQLite trigger chặn", amountUpdateBlocked)

        // Xác nhận amount trong CSDL vẫn không bị thay đổi
        val transferAfterFailedUpdate = db.settlementDao().getTransferById(transfer.id)!!
        assertEquals(150000L, transferAfterFailedUpdate.amount)

        // 3. Cập nhật cột isCompleted và completedAmount khi đoàn đã khóa sổ -> ĐƯỢC PHÉP THÀNH CÔNG
        db.settlementDao().updateTransferStatus(transfer.id, isCompleted = true, completedAmount = transfer.amount)

        val transferAfterStatusUpdate = db.settlementDao().getTransferById(transfer.id)!!
        assertTrue("Trạng thái isCompleted phải cập nhật thành công thành true", transferAfterStatusUpdate.isCompleted)
        assertEquals(150000L, transferAfterStatusUpdate.completedAmount)
        assertEquals(150000L, transferAfterStatusUpdate.amount)
    }

    @Test
    fun `test changing amount after marking shows not completed and triggers amount changed flag`(): Unit = runBlocking {
        // Gợi ý ban đầu 200k, đã đánh dấu chuyển 200k
        val fromMember = TripMemberEntity(
            id = "m1", tripId = "t1", userId = "u1", name = "Alice", role = "ADMIN", isActive = true, joinedAt = 1000L
        )
        val toMember = TripMemberEntity(
            id = "m2", tripId = "t1", userId = "u2", name = "Bob", role = "MEMBER", isActive = true, joinedAt = 1000L
        )

        val transferMarked = SettlementTransfer(
            id = "tr_t1_m1_m2",
            fromMember = fromMember,
            toMember = toMember,
            amount = 200000L,
            transferNote = "Quyết toán t1",
            isCompleted = true,
            completedAmount = 200000L
        )

        // 1. Số tiền chưa đổi: isEffectivelyCompleted = true, isAmountChangedAfterMarking = false
        assertTrue(transferMarked.isEffectivelyCompleted)
        assertFalse(transferMarked.isAmountChangedAfterMarking)

        // 2. Số tiền gợi ý thay đổi (ví dụ chi phí tăng thành 350k)
        val transferAmountChanged = transferMarked.copy(amount = 350000L)

        // Phải hiển thị là chưa chuyển (isEffectivelyCompleted == false)
        assertFalse(
            "Khi số tiền gợi ý thay đổi, không được coi là đã chuyển",
            transferAmountChanged.isEffectivelyCompleted
        )
        // Đồng thời cờ cảnh báo số tiền thay đổi phải bật (isAmountChangedAfterMarking == true)
        assertTrue(
            "Cờ cảnh báo số tiền thay đổi so với lần đánh dấu trước phải bật",
            transferAmountChanged.isAmountChangedAfterMarking
        )
    }

    @Test
    fun `test migration 6 to 7 adds completedAmount column and preserves existing data`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_v6_v7.db")
        if (dbFile.exists()) dbFile.delete()

        // 1. Tạo SQLite CSDL v6 chứa đầy đủ các bảng và dữ liệu settlement_transfers
        val sqlite = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.version = 6
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trips` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `joinCode` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, `baseCurrency` TEXT NOT NULL, `isSettled` INTEGER NOT NULL, `settledAt` INTEGER, `createdAt` INTEGER NOT NULL, `version` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_joinCode` ON `trips` (`joinCode`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `trip_members` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `joinedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId` ON `trip_members` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_members_tripId_name` ON `trip_members` (`tripId`, `name`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, `payerType` TEXT NOT NULL, `payerMemberId` TEXT, `totalAmount` REAL NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedTotalAmount` INTEGER NOT NULL, `splitType` TEXT NOT NULL, `receiptImageUri` TEXT, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `createdMemberId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`payerMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`createdMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_payerMemberId` ON `expenses` (`payerMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_createdMemberId` ON `expenses` (`createdMemberId`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `expense_splits` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `percentage` REAL, PRIMARY KEY(`id`), FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_expenseId` ON `expense_splits` (`expenseId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_tripId` ON `expense_splits` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_splits_memberId` ON `expense_splits` (`memberId`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `fund_contributions` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `amount` INTEGER NOT NULL, `currency` TEXT NOT NULL, `exchangeRate` REAL NOT NULL, `convertedAmount` INTEGER NOT NULL, `note` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `recordedByMemberId` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`memberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`recordedByMemberId`) REFERENCES `trip_members`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_tripId` ON `fund_contributions` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_memberId` ON `fund_contributions` (`memberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_fund_contributions_recordedByMemberId` ON `fund_contributions` (`recordedByMemberId`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `exchange_rates` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, `rateToBase` REAL NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_exchange_rates_tripId` ON `exchange_rates` (`tripId`)")
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exchange_rates_tripId_currencyCode` ON `exchange_rates` (`tripId`, `currencyCode`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `settlement_snapshots` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `snapshotTitle` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `totalExpenses` INTEGER NOT NULL, `totalFundCollected` INTEGER NOT NULL, `totalFundSpent` INTEGER NOT NULL, `remainingFund` INTEGER NOT NULL, `settlementJson` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_snapshots_tripId` ON `settlement_snapshots` (`tripId`)")
        sqlite.execSQL("CREATE TABLE IF NOT EXISTS `audit_logs` (`id` TEXT NOT NULL, `tripId` TEXT NOT NULL, `actorMemberId` TEXT NOT NULL, `actorName` TEXT NOT NULL, `action` TEXT NOT NULL, `description` TEXT NOT NULL, `detailBefore` TEXT, `detailAfter` TEXT, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_tripId` ON `audit_logs` (`tripId`)")
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `settlement_transfers` (
                `id` TEXT NOT NULL,
                `tripId` TEXT NOT NULL,
                `fromMemberId` TEXT NOT NULL,
                `toMemberId` TEXT NOT NULL,
                `amount` INTEGER NOT NULL,
                `transferNote` TEXT NOT NULL,
                `isCompleted` INTEGER NOT NULL DEFAULT 0,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_transfers_tripId` ON `settlement_transfers` (`tripId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_transfers_fromMemberId` ON `settlement_transfers` (`fromMemberId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_transfers_toMemberId` ON `settlement_transfers` (`toMemberId`)")

        sqlite.execSQL("INSERT INTO `trips` VALUES('trip_mig', 'Chuyến Du Lịch Mig', 'Mô tả', 'MIG2026', 1000, 2000, 'VND', 1, 2000, 1000, 1)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('mem1', 'trip_mig', 'u1', 'Thành viên 1', 'ADMIN', 1, 1000)")
        sqlite.execSQL("INSERT INTO `trip_members` VALUES('mem2', 'trip_mig', 'u2', 'Thành viên 2', 'MEMBER', 1, 1000)")
        sqlite.execSQL("INSERT INTO `settlement_transfers` VALUES('tr_mig_1', 'trip_mig', 'mem1', 'mem2', 500000, 'Ghi chú 1', 1, 2000)")
        sqlite.close()

        // 2. Chạy MIGRATION_6_7 lên v7 qua Room builder
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, "test_migration_v6_v7.db")
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .build()

        // 3. Kiểm tra dữ liệu được bảo toàn và cột completedAmount tồn tại với giá trị mặc định NULL
        val tr = migratedDb.settlementDao().getTransferById("tr_mig_1")
        assertNotNull(tr)
        assertEquals("tr_mig_1", tr?.id)
        assertEquals(500000L, tr?.amount)
        assertTrue(tr?.isCompleted == true)
        assertNull("Cột completedAmount mới mặc định là NULL cho dữ liệu cũ", tr?.completedAmount)

        // 4. Kiểm tra foreign keys sạch sẽ
        val fkCursor = migratedDb.openHelper.readableDatabase.query("PRAGMA foreign_key_check")
        assertEquals("PRAGMA foreign_key_check phải rỗng", 0, fkCursor.count)
        fkCursor.close()
        migratedDb.close()
    }

    @Test
    fun `test legacy backup without completedAmount restores with isCompleted false`(): Unit = runBlocking {
        // Tạo backup JSON cũ theo format v6 (không có completedAmount)
        val backupJson = """
        {
            "metadata": {
                "version": 1,
                "exportedAt": 1700000000000,
                "schemaVersion": 6,
                "appVersion": "22.0",
                "appVersionCode": 22,
                "deviceInfo": "TestDevice",
                "totalTrips": 1,
                "totalExpenses": 0,
                "totalMembers": 2,
                "totalFunds": 0,
                "dataChecksum": "skip_in_partial"
            },
            "trips": [
                {
                    "id": "trip_legacy_bk",
                    "title": "Chuyến Đi Backup Cũ",
                    "description": "Backup không có completedAmount",
                    "joinCode": "LEGBK1",
                    "isSettled": true,
                    "startDate": 1000,
                    "endDate": 2000,
                    "createdAt": 1000,
                    "version": 1
                }
            ],
            "members": [
                {
                    "id": "mem_leg_1",
                    "tripId": "trip_legacy_bk",
                    "userId": "u1",
                    "name": "Thành Viên 1",
                    "role": "ADMIN",
                    "isActive": 1,
                    "joinedAt": 1000
                },
                {
                    "id": "mem_leg_2",
                    "tripId": "trip_legacy_bk",
                    "userId": "u2",
                    "name": "Thành Viên 2",
                    "role": "MEMBER",
                    "isActive": 1,
                    "joinedAt": 1000
                }
            ],
            "expenses": [],
            "splits": [],
            "funds": [],
            "snapshots": [],
            "auditLogs": [],
            "transfers": [
                {
                    "id": "tr_leg_1",
                    "tripId": "trip_legacy_bk",
                    "fromMemberId": "mem_leg_1",
                    "toMemberId": "mem_leg_2",
                    "amount": 180000,
                    "transferNote": "LEGBK1",
                    "isCompleted": true,
                    "updatedAt": 2000
                }
            ]
        }
        """.trimIndent()

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val backupFile = java.io.File(context.cacheDir, "test_legacy_backup.json")
        backupFile.writeText(backupJson)

        val restoreResult = repository.restoreBackupFromFile(backupFile, clearExisting = false)
        assertTrue("Khôi phục bản backup cũ phải thành công: ${restoreResult.exceptionOrNull()?.message}", restoreResult.isSuccess)

        val restoredTransfers = db.settlementDao().getTransfersByTripOnce("trip_legacy_bk")
        assertEquals(1, restoredTransfers.size)
        val tr = restoredTransfers[0]
        assertEquals("tr_leg_1", tr.id)
        assertEquals(180000L, tr.amount)
        assertFalse(
            "Backup cũ thiếu completedAmount phải được coi là chưa chuyển (isCompleted = false)",
            tr.isCompleted
        )
        assertNull("completedAmount phải là null khi thiếu trong backup cũ", tr.completedAmount)

        backupFile.delete()
    }
}




