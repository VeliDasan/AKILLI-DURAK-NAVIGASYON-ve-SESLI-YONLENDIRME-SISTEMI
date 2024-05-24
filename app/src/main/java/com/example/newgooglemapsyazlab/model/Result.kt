package com.example.newgooglemapsyazlab.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.sql.Time

@Parcelize
data class Result(
    var distance:String,
    var time:String
):Parcelable

