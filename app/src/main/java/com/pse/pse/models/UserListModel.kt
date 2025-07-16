package com.pse.pse.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserListModel(
    val uid: String = "",
    val name: String = "",
    val lName: String = "",
    val status: String = ""
) : Parcelable
