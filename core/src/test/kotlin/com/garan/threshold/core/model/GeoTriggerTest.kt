package com.garan.threshold.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTriggerTest {

    // Reference point: London (51.5074, -0.1278)
    // At lat 51.5074, 1 degree of latitude is ~111,195 meters (~0.0008993 deg per 100m).
    private val center = LocationFix(latitude = 51.5074, longitude = -0.1278)

    private fun offsetNorthMeters(origin: LocationFix, meters: Double): LocationFix {
        val deltaLat = meters / 111_194.9266
        return LocationFix(
            latitude = origin.latitude + deltaLat,
            longitude = origin.longitude,
        )
    }

    @Test
    fun `LocationFix distanceMetersTo returns zero for identical coordinates`() {
        assertEquals(0.0, center.distanceMetersTo(center), 0.01)
    }

    @Test
    fun `LocationFix distanceMetersTo computes accurate Haversine distance`() {
        val point250mNorth = offsetNorthMeters(center, 250.0)
        val distance = center.distanceMetersTo(point250mNorth)
        assertEquals(250.0, distance, 0.5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LocationFix rejects out-of-range latitude`() {
        LocationFix(latitude = 91.0, longitude = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `LocationFix rejects out-of-range longitude`() {
        LocationFix(latitude = 0.0, longitude = -181.0)
    }

    @Test
    fun `GeoTrigger fails safe to inactive when currentLocation is null for both INSIDE and OUTSIDE`() {
        val insideTrigger = GeoTrigger(
            latitude = center.latitude,
            longitude = center.longitude,
            radiusMeters = 250,
            mode = GeoTriggerMode.INSIDE,
        )
        val outsideTrigger = insideTrigger.copy(mode = GeoTriggerMode.OUTSIDE)

        assertFalse(insideTrigger.isActive(currentLocation = null, wasPreviouslyActive = false))
        assertFalse(insideTrigger.isActive(currentLocation = null, wasPreviouslyActive = true))
        assertFalse(outsideTrigger.isActive(currentLocation = null, wasPreviouslyActive = false))
        assertFalse(outsideTrigger.isActive(currentLocation = null, wasPreviouslyActive = true))
    }

    @Test
    fun `INSIDE mode activates within radius and applies +25m hysteresis buffer on exit`() {
        val trigger = GeoTrigger(
            latitude = center.latitude,
            longitude = center.longitude,
            radiusMeters = 250,
            mode = GeoTriggerMode.INSIDE,
        )

        val inside200m = offsetNorthMeters(center, 200.0)
        val boundaryBuffer265m = offsetNorthMeters(center, 265.0) // Inside 250 + 25 = 275m buffer
        val outside290m = offsetNorthMeters(center, 290.0) // Beyond 275m buffer

        // When NOT previously active:
        assertTrue(trigger.isActive(inside200m, wasPreviouslyActive = false))
        assertFalse(trigger.isActive(boundaryBuffer265m, wasPreviouslyActive = false))
        assertFalse(trigger.isActive(outside290m, wasPreviouslyActive = false))

        // When PREVIOUSLY active (hysteresis buffer prevents flapping at 265m):
        assertTrue(trigger.isActive(inside200m, wasPreviouslyActive = true))
        assertTrue(trigger.isActive(boundaryBuffer265m, wasPreviouslyActive = true))
        assertFalse(trigger.isActive(outside290m, wasPreviouslyActive = true))
    }

    @Test
    fun `OUTSIDE mode activates beyond radius and applies -25m hysteresis buffer on re-entry`() {
        val trigger = GeoTrigger(
            latitude = center.latitude,
            longitude = center.longitude,
            radiusMeters = 250,
            mode = GeoTriggerMode.OUTSIDE,
        )

        val deepInside200m = offsetNorthMeters(center, 200.0) // Inside 250 - 25 = 225m threshold
        val boundaryBuffer235m = offsetNorthMeters(center, 235.0) // Between 225m and 250m
        val outside280m = offsetNorthMeters(center, 280.0)

        // When NOT previously active: must be > 250m
        assertFalse(trigger.isActive(deepInside200m, wasPreviouslyActive = false))
        assertFalse(trigger.isActive(boundaryBuffer235m, wasPreviouslyActive = false))
        assertTrue(trigger.isActive(outside280m, wasPreviouslyActive = false))

        // When PREVIOUSLY active: stays active down to 225m, deactivates at <= 225m
        assertTrue(trigger.isActive(outside280m, wasPreviouslyActive = true))
        assertTrue(trigger.isActive(boundaryBuffer235m, wasPreviouslyActive = true))
        assertFalse(trigger.isActive(deepInside200m, wasPreviouslyActive = true))
    }

    @Test
    fun `GeoCoordinateParser parses decimal lat lng strings`() {
        val parsed = GeoCoordinateParser.parse("51.5074, -0.1278") as? CoordinateParseResult.Success
        assertNotNull(parsed)
        assertEquals(51.5074, parsed!!.location.latitude, 0.0001)
        assertEquals(-0.1278, parsed.location.longitude, 0.0001)

        val spaceSeparated = GeoCoordinateParser.parse("  -33.8688   151.2093 ") as? CoordinateParseResult.Success
        assertNotNull(spaceSeparated)
        assertEquals(-33.8688, spaceSeparated!!.location.latitude, 0.0001)
        assertEquals(151.2093, spaceSeparated.location.longitude, 0.0001)
    }

    @Test
    fun `GeoCoordinateParser parses geo URIs including query coordinates`() {
        val standardGeo = GeoCoordinateParser.parse("geo:51.5074,-0.1278?z=16") as? CoordinateParseResult.Success
        assertNotNull(standardGeo)
        assertEquals(51.5074, standardGeo!!.location.latitude, 0.0001)
        assertEquals(-0.1278, standardGeo.location.longitude, 0.0001)

        val queryGeo = GeoCoordinateParser.parse(
            "geo:0,0?q=37.4220,-122.0841(Googleplex)",
        ) as? CoordinateParseResult.Success
        assertNotNull(queryGeo)
        assertEquals(37.4220, queryGeo!!.location.latitude, 0.0001)
        assertEquals(-122.0841, queryGeo.location.longitude, 0.0001)
    }

    @Test
    fun `GeoCoordinateParser parses DMS format`() {
        val dms = GeoCoordinateParser.parse("""51°30'26.6"N 0°07'40.1"W""") as? CoordinateParseResult.Success
        assertNotNull(dms)
        assertEquals(51.5074, dms!!.location.latitude, 0.001)
        assertEquals(-0.1278, dms.location.longitude, 0.001)
    }

    @Test
    fun `GeoCoordinateParser flags web URLs and invalid text distinctly`() {
        assertEquals(
            CoordinateParseResult.UrlNotSupported,
            GeoCoordinateParser.parse("https://maps.app.goo.gl/xyz123"),
        )
        assertEquals(
            CoordinateParseResult.Invalid,
            GeoCoordinateParser.parse("not a coordinate"),
        )
        assertNull(GeoCoordinateParser.parseOrNull("https://maps.google.com"))
    }
}
