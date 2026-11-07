package com.onevoice.nativeapp

data class Narrator(val label: String, val code: String, val speakerId: Int)

object VoiceCatalog {
    // Official Kokoro 1.0 53-speaker ID map.
    val voices = listOf(
        Narrator("Heart — US female", "af_heart", 3),
        Narrator("Bella — US female", "af_bella", 2),
        Narrator("Michael — US male", "am_michael", 16),
        Narrator("Fenrir — US male", "am_fenrir", 14),
        Narrator("Emma — British female", "bf_emma", 21),
        Narrator("George — British male", "bm_george", 26)
    )
}
