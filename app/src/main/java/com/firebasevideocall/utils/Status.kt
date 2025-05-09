package com.firebasevideocall.utils

enum class Status(val status: String) {
    AVAILABLE("AVAILABLE"),
    REQUESTED("REQUESTED"),
    ACCEPTED("ACCEPTED"),
    UNAVAILABLE("UNAVAILABLE"),
    DENIED("DENIED"),
    PASSED("PASSED")
}