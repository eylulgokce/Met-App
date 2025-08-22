package com.example.met_app

import android.annotation.SuppressLint

@SuppressLint("DefaultLocale")
fun formatHms(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}