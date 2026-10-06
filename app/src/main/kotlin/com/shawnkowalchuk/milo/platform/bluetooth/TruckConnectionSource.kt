package com.shawnkowalchuk.milo.platform.bluetooth

/**
 * Answers one question: is the truck connected right now?
 *
 * This is the "reading" of ADR-002. Connect and disconnect events are only hints; whenever the
 * trip rules need the truth (at a reconcile, when a timer fires, when a button is pressed, once
 * a minute during a trip) the trip controller asks here.
 *
 * It is a suspend function because on most Android versions the answer comes from a Bluetooth
 * profile proxy, which Android hands over asynchronously. It never blocks the thread it is
 * called on. The real implementation is [BluetoothTruckConnection].
 */
fun interface TruckConnectionSource {
    suspend fun read(): TruckReading
}

/**
 * What one look at the truck's connection found.
 *
 * @param evidence how the answer was reached, in words, for the event log: which Bluetooth
 * profile listed the truck, or what stood in the way of an answer.
 */
data class TruckReading(val answer: Answer, val evidence: String) {
    enum class Answer {
        CONNECTED,
        NOT_CONNECTED,

        /**
         * The phone could not say: MilO is not allowed to use Bluetooth, or Bluetooth did not
         * answer in time. It is never taken for "connected", and nothing starts on it.
         */
        UNKNOWN,
    }

    val connected: Boolean get() = answer == Answer.CONNECTED
    val known: Boolean get() = answer != Answer.UNKNOWN

    companion object {
        fun connected(evidence: String) = TruckReading(Answer.CONNECTED, evidence)

        fun notConnected(evidence: String) = TruckReading(Answer.NOT_CONNECTED, evidence)

        fun unknown(evidence: String) = TruckReading(Answer.UNKNOWN, evidence)
    }
}
