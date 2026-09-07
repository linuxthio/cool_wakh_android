package com.wakh.app.data.repository

import com.wakh.app.data.local.db.ContactDao
import com.wakh.app.data.local.db.ContactEntity
import kotlinx.coroutines.flow.Flow

class ContactRepository(
    private val contactDao: ContactDao,
) {
    fun observeContacts(): Flow<List<ContactEntity>> = contactDao.observeAll()

    suspend fun getContact(phoneNumber: String): ContactEntity? = contactDao.getByPhoneNumber(phoneNumber)

    /** Ajoute un contact simplement à partir de son numéro de téléphone. */
    suspend fun addContact(phoneNumber: String, displayName: String) {
        contactDao.upsert(ContactEntity(phoneNumber = phoneNumber, displayName = displayName))
    }

    suspend fun removeContact(contact: ContactEntity) {
        contactDao.delete(contact)
    }
}
