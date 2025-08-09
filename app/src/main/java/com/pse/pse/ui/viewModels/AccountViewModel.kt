package com.pse.pse.ui.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pse.pse.models.AccountModel
import com.pse.pse.models.AnnouncementModel
import com.pse.pse.data.repository.AccountRepository
import com.pse.pse.models.AccountWithUser

class AccountViewModel : ViewModel() {
    private val repository = AccountRepository()
    private val _announcementLiveData = MutableLiveData<List<AnnouncementModel>?>()
    // Expose it as LiveData
    val announcementLiveData: LiveData<List<AnnouncementModel>?> get() = _announcementLiveData
    private val _announcementImageUrls = MutableLiveData<List<String>?>()
    val announcementImageUrls: LiveData<List<String>?> get() = _announcementImageUrls

    var hasShownAnnouncement = false

    fun getAccount(uId: String?): LiveData<AccountModel?> {
        return repository.getAccount(uId)
    }
    fun getAnnouncements() {
        repository.getAnnouncements { announcements ->
            _announcementLiveData.postValue(announcements)
        }
    }
    fun getAnnouncementImageUrls() {
        repository.getAnnouncementImageUrls { urls ->
            _announcementImageUrls.postValue(urls)
        }
    }

    fun getAccountWithUser(uId: String?): LiveData<AccountWithUser?> {
        return repository.getAccountWithUser(uId)
    }

}