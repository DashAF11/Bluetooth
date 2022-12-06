package com.example.blemedium.utils

fun String.convertToList(): List<String> {
    return listOf(*this.split(",").toTypedArray())
}