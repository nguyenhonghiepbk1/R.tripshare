package com.hiepnguyen.tripfinance.data.security

import android.content.Context
import androidx.annotation.VisibleForTesting
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class DatabaseSecurityErrorType {
    KEYSTORE_INIT_FAILED,
    KEYSTORE_ACCESS_FAILED,
    KEYSTORE_UNAVAILABLE,
    KEYSTORE_KEY_NOT_FOUND,
    DECRYPTION_FAILED,
    SQLCIPHER_LOAD_FAILED,
    COMMIT_FAILED,
    UNKNOWN
}

/**
 * Ngoại lệ bảo mật cơ sở dữ liệu khi không thể truy cập khóa mã hóa từ phần cứng Android KeyStore
 * hoặc khi thư viện SQLCipher gặp sự cố.
 */
class DatabaseSecurityException(
    message: String,
    cause: Throwable? = null,
    val errorType: DatabaseSecurityErrorType = DatabaseSecurityErrorType.UNKNOWN
) : SecurityException(message, cause)

/**
 * Giao diện cung cấp khóa giải mã cơ sở dữ liệu.
 * Cho phép phân tách môi trường thiết bị thật (AndroidKeyStoreKeyProvider)
 * và môi trường kiểm thử (InMemoryKeyProvider / FakeKeyProvider) qua DI thủ công.
 */
interface KeyProvider {
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray
    fun hasExistingCredentials(context: Context): Boolean
    fun clearDatabaseCredentials(context: Context): Boolean
}

/**
 * Triển khai KeyProvider trên môi trường thiết bị Android thực tế sử dụng Android KeyStore phần cứng.
 * - Tuân thủ tuyệt đối kiến trúc Fail-Closed (không bao giờ mở CSDL nếu KeyStore hỏng).
 * - Không chứa bất kỳ nhánh rẽ hay điều kiện kiểm thử nào.
 */
class AndroidKeyStoreKeyProvider : KeyProvider {

    companion object {
        private const val TAG = "AndroidKeyStoreKeyProvider"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "TripFinance_Room_DB_MasterKey"
        private const val PREFS_NAME = "trip_finance_db_sec_vault"
        private const val KEY_ENCRYPTED_PASSPHRASE = "enc_db_passphrase"
        private const val KEY_IV = "enc_db_iv"
        private const val GCM_TAG_LENGTH = 128
        private const val PASSPHRASE_LENGTH_BYTES = 32
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    override fun hasExistingCredentials(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null) != null &&
                prefs.getString(KEY_IV, null) != null
    }

    @Synchronized
    override fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Dọn dẹp bất kỳ khóa fallback plaintext cũ nào nếu có để triệt tiêu lỗ hổng rò rỉ dữ liệu
        if (prefs.contains("fallback_db_seed")) {
            prefs.edit().remove("fallback_db_seed").commit()
        }

        val encryptedPassphraseB64 = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null)
        val ivB64 = prefs.getString(KEY_IV, null)

        if (encryptedPassphraseB64 != null && ivB64 != null) {
            // Đã có khóa mã hóa trước đó: Giải mã an toàn bằng Android KeyStore phần cứng
            var lastException: Throwable? = null
            for (attempt in 1..MAX_RETRY_ATTEMPTS) {
                try {
                    val masterKey = getOrCreateMasterKey()
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                    val encryptedBytes = Base64.decode(encryptedPassphraseB64, Base64.NO_WRAP)
                    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)
                    return cipher.doFinal(encryptedBytes)
                } catch (e: Throwable) {
                    lastException = e
                    Log.w(TAG, "Lần giải mã KeyStore thứ $attempt thất bại: ${e.message}")
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        try {
                            Thread.sleep(50L * attempt)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                        }
                    }
                }
            }

            // FAIL-CLOSED: Trên thiết bị thật ném ngoại lệ rõ ràng, không fallback
            throw DatabaseSecurityException(
                "Không thể truy xuất khóa mã hóa từ Android KeyStore sau $MAX_RETRY_ATTEMPTS lần thử: ${lastException?.message}. " +
                        "Dữ liệu được khóa an toàn để tránh bị xâm nhập.",
                lastException,
                DatabaseSecurityErrorType.DECRYPTION_FAILED
            )
        } else {
            // Lần khởi tạo đầu tiên:
            try {
                val masterKey = getOrCreateMasterKey()
                val rawPassphrase = ByteArray(PASSPHRASE_LENGTH_BYTES)
                SecureRandom().nextBytes(rawPassphrase)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, masterKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(rawPassphrase)

                // DÙNG commit() ĐỒNG BỘ ĐỂ ĐẢM BẢO KHÓA ĐƯỢC LƯU VÀO ĐĨA VẬT LÝ TRƯỚC KHI TRẢ VỀ CHO ROOM
                val committed = prefs.edit()
                    .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                    .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .commit()

                if (!committed) {
                    throw DatabaseSecurityException(
                        "Không thể lưu khóa mã hóa CSDL vào bộ nhớ an toàn (SharedPreferences commit thất bại). Dừng khởi tạo CSDL để tránh mất dữ liệu.",
                        null,
                        DatabaseSecurityErrorType.COMMIT_FAILED
                    )
                }

                return rawPassphrase
            } catch (e: DatabaseSecurityException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Lỗi tạo khóa mới bằng Android KeyStore: ${e.message}", e)
                throw DatabaseSecurityException(
                    "Không thể khởi tạo khóa bảo mật phần cứng Android KeyStore: ${e.message}",
                    e,
                    DatabaseSecurityErrorType.KEYSTORE_INIT_FAILED
                )
            }
        }
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (e: Throwable) {
            throw DatabaseSecurityException(
                "Không thể kết nối dịch vụ phần cứng Android KeyStore: ${e.message}",
                e,
                DatabaseSecurityErrorType.KEYSTORE_UNAVAILABLE
            )
        }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            try {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenSpec)
                return keyGenerator.generateKey()
            } catch (e: Throwable) {
                throw DatabaseSecurityException(
                    "Không thể tạo Master Key mới trong Android KeyStore: ${e.message}",
                    e,
                    DatabaseSecurityErrorType.KEYSTORE_INIT_FAILED
                )
            }
        }

        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                ?: throw DatabaseSecurityException(
                    "Không thể đọc Master Key từ Android KeyStore (Entry không tồn tại hoặc bị xóa)",
                    null,
                    DatabaseSecurityErrorType.KEYSTORE_KEY_NOT_FOUND
                )
            entry.secretKey
        } catch (e: DatabaseSecurityException) {
            throw e
        } catch (e: Throwable) {
            throw DatabaseSecurityException(
                "Lỗi khi truy xuất Master Key từ Android KeyStore: ${e.message}",
                e,
                DatabaseSecurityErrorType.KEYSTORE_ACCESS_FAILED
            )
        }
    }

    override fun clearDatabaseCredentials(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Không thể xóa alias KeyStore: ${e.message}")
        }
        return prefs.edit().clear().commit()
    }
}

/**
 * Triển khai KeyProvider giả lập trong bộ nhớ phục vụ kiểm thử đơn vị hoặc JVM/Robolectric.
 * Cho phép tiêm (inject) qua DI thủ công để tránh phụ thuộc phần cứng Android KeyStore.
 */
class InMemoryKeyProvider(
    private val passphrase: ByteArray = "TripFinance_Test_Secret_Key_32B!".toByteArray().copyOf(32)
) : KeyProvider {
    private var hasCredentials = false

    override fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        hasCredentials = true
        return passphrase
    }

    override fun hasExistingCredentials(context: Context): Boolean = hasCredentials

    override fun clearDatabaseCredentials(context: Context): Boolean {
        hasCredentials = false
        val prefs = context.getSharedPreferences("trip_finance_db_sec_vault", Context.MODE_PRIVATE)
        return prefs.edit().clear().commit()
    }
}

/**
 * Quản lý khóa mã hóa CSDL SQLite tại chỗ (SQLCipher) an toàn thông qua phần cứng Android KeyStore.
 * - Khóa gốc (Master Key) được tạo và lưu trữ bên trong Hardware Security Module / TEE / StrongBox.
 * - Khóa giải mã CSDL (32 bytes ngẫu nhiên) được mã hóa bằng AES-256-GCM bảo vệ toàn vẹn.
 * - Sử dụng SharedPreferences commit() đồng bộ đảm bảo tính bền vững (Persistence Guarantee).
 * - Cơ chế thử lại (Retry) với độ trễ cho lỗi tạm thời của KeyStore daemon.
 * - Không cho phép mở CSDL không mã hóa (Fail-Closed Architecture).
 * - Hỗ trợ tiêm KeyProvider tùy biến cho kiểm thử đơn vị.
 */
object DatabaseKeyManager : KeyProvider {

    @Volatile
    var keyProvider: KeyProvider = AndroidKeyStoreKeyProvider()
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal set

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun resetKeyProvider() {
        keyProvider = AndroidKeyStoreKeyProvider()
    }

    fun isTestEnvironment(): Boolean {
        return keyProvider !is AndroidKeyStoreKeyProvider
    }

    override fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        return keyProvider.getOrCreateDatabasePassphrase(context)
    }

    override fun hasExistingCredentials(context: Context): Boolean {
        return keyProvider.hasExistingCredentials(context)
    }

    override fun clearDatabaseCredentials(context: Context): Boolean {
        return keyProvider.clearDatabaseCredentials(context)
    }
}
