package com.smsforwarder.data.repository

import com.smsforwarder.data.dao.FilterRuleDao
import com.smsforwarder.data.dao.ForwardLogDao
import com.smsforwarder.data.dao.ForwardNumberDao
import com.smsforwarder.data.model.FilterRule
import com.smsforwarder.data.model.ForwardLog
import com.smsforwarder.data.model.ForwardNumber

class ForwarderRepository(
    private val filterRuleDao: FilterRuleDao,
    private val forwardNumberDao: ForwardNumberDao,
    private val forwardLogDao: ForwardLogDao
) {
    val allFilterRules = filterRuleDao.getAll()
    val allForwardNumbers = forwardNumberDao.getAll()
    val allForwardLogs = forwardLogDao.getAll()

    suspend fun getEnabledRules() = filterRuleDao.getEnabledRules()
    suspend fun getEnabledNumbers() = forwardNumberDao.getEnabledNumbers()

    suspend fun insertRule(rule: FilterRule) = filterRuleDao.insert(rule)
    suspend fun updateRule(rule: FilterRule) = filterRuleDao.update(rule)
    suspend fun deleteRule(rule: FilterRule) = filterRuleDao.delete(rule)

    suspend fun insertNumber(number: ForwardNumber) = forwardNumberDao.insert(number)
    suspend fun updateNumber(number: ForwardNumber) = forwardNumberDao.update(number)
    suspend fun deleteNumber(number: ForwardNumber) = forwardNumberDao.delete(number)

    suspend fun insertLog(log: ForwardLog) = forwardLogDao.insert(log)
    suspend fun clearLogs() = forwardLogDao.deleteAll()
}
