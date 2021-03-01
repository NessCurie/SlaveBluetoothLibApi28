package com.github.slavebluetooth.model

data class BluetoothPhonebook(val name: String, val number: String) {

    override fun equals(other: Any?): Boolean {
        if (other is BluetoothPhonebook) {
            return this.name == other.name && this.number == other.number
        }
        return false
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + number.hashCode()
        return result
    }
}