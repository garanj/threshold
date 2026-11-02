package com.garan.threshold.core.model

/**
 * Uniquely identifies a launchable application component across multi-user and Android Work profiles.
 *
 * @property packageName The Android application package name (e.g. `com.android.dialer`).
 * @property activityName The fully-qualified launcher activity name (e.g. `com.android.dialer.DialtactsActivity`).
 * @property userSerial User handle serial number (0 for primary personal user, >0 for work profiles).
 */
data class ComponentKey(val packageName: String, val activityName: String, val userSerial: Long = 0L) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(activityName.isNotBlank()) { "activityName must not be blank" }
        require(userSerial >= 0L) { "userSerial must be non-negative" }
    }

    /**
     * Precomputed flat string identifier: `packageName/activityName#userSerial`.
     * Cached at construction so UI list keys (`LazyColumn`) never allocate strings on recomposition.
     */
    val id: String = "$packageName/$activityName#$userSerial"

    /**
     * Serializes this key to a flat string format: `packageName/activityName#userSerial`.
     */
    fun flattenToString(): String = id

    companion object {
        /**
         * Parses a flattened string formatted as `packageName/activityName` or `packageName/activityName#userSerial`.
         */
        fun fromFlatString(flatString: String): ComponentKey {
            val slashIndex = flatString.indexOf('/')
            require(slashIndex > 0 && slashIndex < flatString.length - 1) {
                "Invalid ComponentKey format: '$flatString'. Expected 'packageName/activityName[#userSerial]'"
            }

            val pkg = flatString.substring(0, slashIndex)
            val rest = flatString.substring(slashIndex + 1)

            val hashIndex = rest.indexOf('#')
            val (activity, serial) = if (hashIndex >= 0) {
                val act = rest.substring(0, hashIndex)
                val serStr = rest.substring(hashIndex + 1)
                val ser = serStr.toLongOrNull() ?: throw IllegalArgumentException(
                    "Invalid userSerial in ComponentKey: '$serStr'",
                )
                act to ser
            } else {
                rest to 0L
            }

            return ComponentKey(
                packageName = pkg,
                activityName = activity,
                userSerial = serial,
            )
        }
    }
}
