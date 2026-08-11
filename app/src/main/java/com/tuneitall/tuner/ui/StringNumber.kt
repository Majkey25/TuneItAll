package com.tuneitall.tuner.ui

internal fun instrumentStringNumber(index: Int, stringCount: Int): Int {
    require(stringCount > 0) { "String count must be positive" }
    require(index in 0 until stringCount) { "String index must fit the instrument" }
    return stringCount - index
}
