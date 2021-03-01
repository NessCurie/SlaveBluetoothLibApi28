package com.github.slavebluetooth.model

/**
 * @param name 没有name时name会为number
 */
data class BluetoothCallLog(val type: Int, val name: String, val number: String, val time: Long) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BluetoothCallLog

        if (type != other.type) return false
        if (name != other.name) return false
        if (number != other.number) return false
        if (time != other.time) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + name.hashCode()
        result = 31 * result + number.hashCode()
        result = 31 * result + time.hashCode()
        return result
    }

}