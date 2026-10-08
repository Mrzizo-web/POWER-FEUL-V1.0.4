package com.example.data.engine

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.WalletSmsEntity
import com.example.data.local.entity.WalletTransactionEntity
import com.example.domain.model.SmsProcessingStatus
import com.example.domain.model.WalletTransferStatus
import java.util.UUID

sealed class IngestResult {
    data class Created(val tx: WalletTransactionEntity, val isOutOfShift: Boolean) : IngestResult()
    data class Duplicate(val tx: WalletTransactionEntity, val reason: String) : IngestResult()
    data class Unmatched(val sms: WalletSmsEntity) : IngestResult()
}

class WalletMatchingEngine(private val db: AppDatabase) {
    private val walletDao = db.walletDao()
    private val shiftDao = db.shiftDao()
    private val auditLogDao = db.auditLogDao()

    suspend fun ingestSms(
        sender: String,
        body: String,
        receivedAt: Long = System.currentTimeMillis()
    ): IngestResult {
        val parsed = WalletParserRegistry.parseSms(sender, body)
        val smsId = UUID.randomUUID().toString()

        if (parsed == null || parsed.amount <= 0) {
            val sms = WalletSmsEntity(
                id = smsId,
                walletCode = "UNKNOWN",
                receivedAt = receivedAt,
                sender = sender,
                body = body,
                processingStatus = SmsProcessingStatus.UNMATCHED
            )
            walletDao.insertSms(sms)
            return IngestResult.Unmatched(sms)
        }

        // 1. Check for primary duplicate by (walletCode + transactionId) if txId is present
        var existingTx: WalletTransactionEntity? = null
        if (parsed.transactionId.isNotBlank()) {
            existingTx = walletDao.findTransactionByTxId(parsed.walletCode, parsed.transactionId)
        }

        // 2. Check for potential duplicate by amount + sender + 10-minute time window
        if (existingTx == null && parsed.sender.isNotBlank()) {
            val window = 10 * 60 * 1000L
            existingTx = walletDao.findPotentialDuplicate(
                walletCode = parsed.walletCode,
                amount = parsed.amount,
                sender = parsed.sender,
                minTime = receivedAt - window,
                maxTime = receivedAt + window
            )
        }

        if (existingTx != null) {
            // Duplicate detected! Save SMS, link to existing transaction, but DO NOT create new financial transaction
            val sms = WalletSmsEntity(
                id = smsId,
                walletCode = parsed.walletCode,
                receivedAt = receivedAt,
                sender = sender,
                body = body,
                parsedAmount = parsed.amount,
                parsedTransactionId = parsed.transactionId,
                parsedSender = parsed.sender,
                processingStatus = SmsProcessingStatus.DUPLICATE,
                matchedTransactionId = existingTx.id
            )
            walletDao.insertSms(sms)

            auditLogDao.insertLog(
                AuditLogEntity(
                    userId = "SYSTEM",
                    userName = "SMS Gateway",
                    userRole = "SYSTEM",
                    action = "DUPLICATE_SMS_DETECTED",
                    entityType = "WALLET_TX",
                    entityId = existingTx.id,
                    newValue = "${parsed.amount} ريال (${parsed.transactionId})",
                    notes = "تم تجاهل التكرار وربط الرسالة بالحوالة السابقة"
                )
            )

            return IngestResult.Duplicate(existingTx, "رسالة مكررة لنفس الحوالة رقم ${existingTx.transactionId}")
        }

        // 3. Find open shift AT THE EXACT TIME OF RECEIPT (receivedAt)
        // In local room, check if open shift exists
        val openShift = shiftDao.getCurrentOpenShiftSync()
        val isOutOfShift = (openShift == null)

        val txId = UUID.randomUUID().toString()
        val tx = WalletTransactionEntity(
            id = txId,
            walletCode = parsed.walletCode,
            amount = parsed.amount,
            transactionId = parsed.transactionId.ifEmpty { "TX-${System.currentTimeMillis() % 100000}" },
            sender = parsed.sender.ifEmpty { sender },
            receivedAt = receivedAt,
            shiftId = openShift?.id,
            cashierId = openShift?.userId,
            cashierName = openShift?.userName,
            status = if (isOutOfShift) WalletTransferStatus.OUT_OF_SHIFT else WalletTransferStatus.MATCHED,
            originalSmsId = smsId,
            notes = if (isOutOfShift) "وصلت خارج الدوام" else "مرتبطة بشفت ${openShift?.userName}"
        )
        walletDao.insertTransaction(tx)

        val sms = WalletSmsEntity(
            id = smsId,
            walletCode = parsed.walletCode,
            receivedAt = receivedAt,
            sender = sender,
            body = body,
            parsedAmount = parsed.amount,
            parsedTransactionId = parsed.transactionId,
            parsedSender = parsed.sender,
            processingStatus = SmsProcessingStatus.PROCESSED,
            matchedTransactionId = txId
        )
        walletDao.insertSms(sms)

        auditLogDao.insertLog(
            AuditLogEntity(
                userId = "SYSTEM",
                userName = "SMS Gateway",
                userRole = "SYSTEM",
                action = if (isOutOfShift) "WALLET_TX_OUT_OF_SHIFT" else "WALLET_TX_RECEIVED",
                entityType = "WALLET_TX",
                entityId = txId,
                newValue = "${tx.amount} ريال (${tx.walletCode})",
                notes = if (isOutOfShift) "حوالة واردة بدون شفت مفتوح" else "حوالة واردة لشفت ${openShift?.userName}"
            )
        )

        return IngestResult.Created(tx, isOutOfShift)
    }

    suspend fun updateTransactionStatus(
        txId: String,
        newStatus: WalletTransferStatus,
        reason: String,
        userId: String,
        userName: String,
        userRole: String
    ): Result<WalletTransactionEntity> {
        val tx = walletDao.getTransactionById(txId) ?: return Result.failure(Exception("الحوالة غير موجودة"))
        val prev = tx.status
        val updated = tx.copy(
            status = newStatus,
            rejectionReason = if (newStatus == WalletTransferStatus.REJECTED) reason else tx.rejectionReason,
            updatedAt = System.currentTimeMillis()
        )
        walletDao.updateTransaction(updated)

        auditLogDao.insertLog(
            AuditLogEntity(
                userId = userId,
                userName = userName,
                userRole = userRole,
                action = "UPDATE_WALLET_TX_STATUS",
                entityType = "WALLET_TX",
                entityId = txId,
                previousValue = prev.name,
                newValue = newStatus.name,
                notes = reason.ifEmpty { "تعديل حالة الحوالة إلى ${newStatus.titleAr}" }
            )
        )
        return Result.success(updated)
    }

    suspend fun reassignShift(
        txId: String,
        newShiftId: String,
        cashierId: String,
        cashierName: String,
        userId: String,
        userName: String,
        userRole: String,
        reason: String
    ): Result<WalletTransactionEntity> {
        val tx = walletDao.getTransactionById(txId) ?: return Result.failure(Exception("الحوالة غير موجودة"))
        val prevShift = tx.shiftId ?: "خارج الدوام"
        val updated = tx.copy(
            shiftId = newShiftId,
            cashierId = cashierId,
            cashierName = cashierName,
            status = WalletTransferStatus.MATCHED,
            notes = "تمت إعادة الربط بالشفت بواسطة $userName: $reason",
            updatedAt = System.currentTimeMillis()
        )
        walletDao.updateTransaction(updated)

        auditLogDao.insertLog(
            AuditLogEntity(
                userId = userId,
                userName = userName,
                userRole = userRole,
                action = "REASSIGN_WALLET_TX_SHIFT",
                entityType = "WALLET_TX",
                entityId = txId,
                previousValue = prevShift,
                newValue = newShiftId,
                notes = "إعادة إسناد الحوالة: $reason"
            )
        )
        return Result.success(updated)
    }
}
