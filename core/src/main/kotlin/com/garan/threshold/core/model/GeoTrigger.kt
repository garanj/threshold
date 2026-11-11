package com.garan.threshold.core.model

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin geographic coordinate pair in WGS84 decimal degrees.
 */
data class LocationFix(val latitude: Double, val longitude: Double) {
    init {
        require(!latitude.isNaN() && latitude in -90.0..90.0) {
            "Latitude must be within [-90.0, 90.0], was $latitude"
        }
        require(!longitude.isNaN() && longitude in -180.0..180.0) {
            "Longitude must be within [-180.0, 180.0], was $longitude"
        }
    }

    /**
     * Computes the great-circle Haversine distance in meters between this coordinate and [other].
     */
    fun distanceMetersTo(other: LocationFix): Double {
        if (this == other) return 0.0
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLatRad = Math.toRadians(other.latitude - latitude)
        val deltaLonRad = Math.toRadians(other.longitude - longitude)

        val sinLat = sin(deltaLatRad / 2.0)
        val sinLon = sin(deltaLonRad / 2.0)
        val a = (sinLat * sinLat) + (cos(lat1Rad) * cos(lat2Rad) * sinLon * sinLon)
        val c = 2.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return EARTH_RADIUS_METERS * c
    }

    fun formattedCoordinates(): String = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

/**
 * Polarity mode for a [GeoTrigger] circular zone.
 */
enum class GeoTriggerMode {
    /** Active while the user is within the circular zone radius. */
    INSIDE,

    /** Active while the user is outside (away from) the circular zone radius. */
    OUTSIDE,
}

/**
 * Defines a single circular geographic zone trigger for a custom profile.
 *
 * Includes a [HYSTERESIS_BUFFER_METERS] exit buffer (+25m when exiting [GeoTriggerMode.INSIDE],
 * -25m when re-entering to exit [GeoTriggerMode.OUTSIDE]) so GPS drift near the zone perimeter
 * never flaps the profile state.
 *
 * When [currentLocation] is `null` (e.g. Location Services disabled or permission missing),
 * both [GeoTriggerMode.INSIDE] and [GeoTriggerMode.OUTSIDE] fail-safe to `false` (inactive).
 */
data class GeoTrigger(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val mode: GeoTriggerMode = GeoTriggerMode.INSIDE,
) {
    init {
        require(!latitude.isNaN() && latitude in -90.0..90.0) {
            "Latitude must be within [-90.0, 90.0], was $latitude"
        }
        require(!longitude.isNaN() && longitude in -180.0..180.0) {
            "Longitude must be within [-180.0, 180.0], was $longitude"
        }
        require(radiusMeters > 0) {
            "Radius must be positive, was $radiusMeters"
        }
    }

    val center: LocationFix
        get() = LocationFix(latitude, longitude)

    fun distanceMetersFrom(currentLocation: LocationFix): Double = center.distanceMetersTo(currentLocation)

    /**
     * Evaluates whether this geo trigger is active given [currentLocation] and [wasPreviouslyActive].
     */
    fun isActive(currentLocation: LocationFix?, wasPreviouslyActive: Boolean = false): Boolean {
        if (currentLocation == null) return false

        val distance = distanceMetersFrom(currentLocation)
        return when (mode) {
            GeoTriggerMode.INSIDE -> {
                val threshold = if (wasPreviouslyActive) {
                    radiusMeters.toDouble() + HYSTERESIS_BUFFER_METERS
                } else {
                    radiusMeters.toDouble()
                }
                distance <= threshold
            }
            GeoTriggerMode.OUTSIDE -> {
                val threshold = if (wasPreviouslyActive) {
                    (radiusMeters.toDouble() - HYSTERESIS_BUFFER_METERS).coerceAtLeast(0.0)
                } else {
                    radiusMeters.toDouble()
                }
                distance > threshold
            }
        }
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 250
        const val HYSTERESIS_BUFFER_METERS = 25.0
        val PRESET_RADII = listOf(100, 250, 500, 1000)

        fun formatRadius(meters: Int): String = if (meters >= 1000 && meters % 1000 == 0) {
            "${meters / 1000}km"
        } else {
            "${meters}m"
        }

        fun formatDistance(meters: Double): String = if (meters >= 1000.0) {
            String.format(Locale.US, "%.1f km", meters / 1000.0)
        } else {
            "${meters.roundToInt()}m"
        }
    }
}

/**
 * Result of parsing user-supplied coordinate text.
 */
sealed interface CoordinateParseResult {
    data class Success(val location: LocationFix) : CoordinateParseResult
    data object UrlNotSupported : CoordinateParseResult
    data object Invalid : CoordinateParseResult
}

/**
 * Pure-Kotlin coordinate parser supporting decimal pairs (`51.5074, -0.1278`),
 * `geo:` URIs (`geo:51.5074,-0.1278` or `geo:0,0?q=51.5074,-0.1278`), and DMS strings
 * (`51°30'26.6"N 0°07'40.1"W`).
 */
object GeoCoordinateParser {

    private val DECIMAL_PAIR_REGEX = Regex(
        """^\s*([+-]?\d+(?:\.\d+)?)\s*(?:,\s*|\s+)\s*([+-]?\d+(?:\.\d+)?)\s*$""",
    )

    private val GEO_QUERY_COORD_REGEX = Regex(
        """[?&]q=([+-]?\d+(?:\.\d+)?)\s*,\s*([+-]?\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    private val GEO_PATH_COORD_REGEX = Regex(
        """^geo:\s*([+-]?\d+(?:\.\d+)?)\s*,\s*([+-]?\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    private val DMS_PAIR_REGEX = Regex(
        """^\s*(\d{1,3})\s*[°dD]\s*(\d{1,2})\s*['′mM]\s*(\d{1,2}(?:\.\d+)?)\s*["″sS]?\s*([NSEWnsew])\s*[,/\s]+\s*(\d{1,3})\s*[°dD]\s*(\d{1,2})\s*['′mM]\s*(\d{1,2}(?:\.\d+)?)\s*["″sS]?\s*([NSEWnsew])\s*$""",
    )

    fun parse(input: String): CoordinateParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return CoordinateParseResult.Invalid

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("maps.app.goo.gl", ignoreCase = true)
        ) {
            return CoordinateParseResult.UrlNotSupported
        }

        // 1. Check `geo:` URI format
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            val queryMatch = GEO_QUERY_COORD_REGEX.find(trimmed)
            if (queryMatch != null) {
                val lat = queryMatch.groupValues[1].toDoubleOrNull()
                val lon = queryMatch.groupValues[2].toDoubleOrNull()
                createValidFix(lat, lon)?.let { return CoordinateParseResult.Success(it) }
            }
            val pathMatch = GEO_PATH_COORD_REGEX.find(trimmed)
            if (pathMatch != null) {
                val lat = pathMatch.groupValues[1].toDoubleOrNull()
                val lon = pathMatch.groupValues[2].toDoubleOrNull()
                createValidFix(lat, lon)?.let { return CoordinateParseResult.Success(it) }
            }
            return CoordinateParseResult.Invalid
        }

        // 2. Check standard decimal pair (`lat, lng` or `lat lng`)
        val decimalMatch = DECIMAL_PAIR_REGEX.matchEntire(trimmed)
        if (decimalMatch != null) {
            val lat = decimalMatch.groupValues[1].toDoubleOrNull()
            val lon = decimalMatch.groupValues[2].toDoubleOrNull()
            return createValidFix(lat, lon)?.let { CoordinateParseResult.Success(it) }
                ?: CoordinateParseResult.Invalid
        }

        // 3. Check DMS pair (`51°30'26.6"N 0°07'40.1"W`)
        val dmsMatch = DMS_PAIR_REGEX.matchEntire(trimmed)
        if (dmsMatch != null) {
            val deg1 = dmsMatch.groupValues[1].toDoubleOrNull() ?: return CoordinateParseResult.Invalid
            val min1 = dmsMatch.groupValues[2].toDoubleOrNull() ?: return CoordinateParseResult.Invalid
            val sec1 = dmsMatch.groupValues[3].toDoubleOrNull() ?: return CoordinateParseResult.Invalid
            val dir1 = dmsMatch.groupValues[4].uppercase(Locale.US)

            val deg2 = dmsMatch.groupValues[5].toDoubleOrNull() ?: return CoordinateParseResult.Invalid
            val min2 = dmsMatch.groupValues[6].toDoubleOrNull() ?: return CoordinateParseResult.Invalid
            val sec2 = dmsMatch.groupValues[7].toDoubleOrNull() ?: return CoordinateParseResult.Invalid
            val dir2 = dmsMatch.groupValues[8].uppercase(Locale.US)

            if (dir1 !in setOf("N", "S") || dir2 !in setOf("E", "W")) {
                return CoordinateParseResult.Invalid
            }
            val latSign = if (dir1 == "S") -1.0 else 1.0
            val lonSign = if (dir2 == "W") -1.0 else 1.0
            val lat = latSign * (deg1 + (min1 / 60.0) + (sec1 / 3600.0))
            val lon = lonSign * (deg2 + (min2 / 60.0) + (sec2 / 3600.0))
            return createValidFix(lat, lon)?.let { CoordinateParseResult.Success(it) }
                ?: CoordinateParseResult.Invalid
        }

        return CoordinateParseResult.Invalid
    }

    fun parseOrNull(input: String): LocationFix? = (parse(input) as? CoordinateParseResult.Success)?.location

    private fun createValidFix(lat: Double?, lon: Double?): LocationFix? {
        if (lat == null || lon == null) return null
        if (lat.isNaN() || lon.isNaN()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return LocationFix(latitude = lat, longitude = lon)
    }
}
