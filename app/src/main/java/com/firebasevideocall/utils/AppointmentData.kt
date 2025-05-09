package com.firebasevideocall.utils

data class AppointmentData(
    var id : String? = null,
    var userEmail : String? = null,
    var note : String? = null,
    val date : String? = null,
    val hour : String? = null,
    val time : String? = null, //hour + time - enddate
    var status: Status = Status.AVAILABLE,
    var location : String? = null
)