package com.pse.pse.models

import com.google.firebase.Timestamp

data class DepositModel (

    val id: String = "",
    val userId:String="",
    val txnId:String="",
    val amount:String="",
    var createdAt: Timestamp? = null



)


