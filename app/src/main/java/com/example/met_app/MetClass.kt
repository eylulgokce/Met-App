package com.example.met_app

enum class MetClass(val label: String) {
    SEDENTARY("Sedentary"),
    LIGHT("Light"),
    MODERATE("Moderate"),
    VIGOROUS("Vigorous");



    companion object {
        @JvmStatic
        fun fromIndex(index: Int): MetClass {
            return values().getOrNull(index)
                ?: throw IllegalArgumentException("Invalid index: $index")
        }
    }
}
