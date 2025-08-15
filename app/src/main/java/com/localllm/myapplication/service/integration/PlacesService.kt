package com.localllm.myapplication.service.integration

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.*
import com.google.android.gms.location.Geofence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Service for Google Places API integration
 * Used for location search and geofencing setup
 */
class PlacesService(
    private val context: Context,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "PlacesService"
    }

    private var placesClient: PlacesClient? = null
    private var isInitialized = false

    /**
     * Initialize the Places API
     */
    fun initialize(): Result<Unit> {
        return try {
            if (!Places.isInitialized()) {
                Places.initialize(context, apiKey)
            }
            placesClient = Places.createClient(context)
            isInitialized = true
            Log.d(TAG, "Places API initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Places API", e)
            Result.failure(e)
        }
    }

    /**
     * Search for places by query text
     */
    suspend fun searchPlaces(
        query: String,
        location: LatLng? = null,
        radiusMeters: Int = 50000
    ): Result<List<PlaceSearchResult>> = withContext(Dispatchers.IO) {
        if (!isInitialized || placesClient == null) {
            return@withContext Result.failure(Exception("Places API not initialized"))
        }

        return@withContext suspendCoroutine { continuation ->
            try {
                // Define the fields to return
                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS,
                    Place.Field.TYPES,
                    Place.Field.VIEWPORT
                )

                // Create the search request
                val request = if (location != null) {
                    FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .setLocationBias(
                            RectangularBounds.newInstance(
                                LatLng(
                                    location.latitude - 0.05,
                                    location.longitude - 0.05
                                ),
                                LatLng(
                                    location.latitude + 0.05,
                                    location.longitude + 0.05
                                )
                            )
                        )
                        .build()
                } else {
                    FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .build()
                }

                placesClient!!.findAutocompletePredictions(request)
                    .addOnSuccessListener { response ->
                        val results = response.autocompletePredictions.map { prediction ->
                            PlaceSearchResult(
                                placeId = prediction.placeId,
                                name = prediction.getPrimaryText(null).toString(),
                                address = prediction.getSecondaryText(null).toString(),
                                latitude = null, // Will be fetched separately if needed
                                longitude = null,
                                types = emptyList() // Will be fetched separately if needed
                            )
                        }
                        Log.d(TAG, "Found ${results.size} places for query: $query")
                        continuation.resume(Result.success(results))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Place search failed for query: $query", exception)
                        continuation.resume(Result.failure(exception))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error during place search", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Get detailed information about a specific place
     */
    suspend fun getPlaceDetails(placeId: String): Result<PlaceDetails> = withContext(Dispatchers.IO) {
        if (!isInitialized || placesClient == null) {
            return@withContext Result.failure(Exception("Places API not initialized"))
        }

        return@withContext suspendCoroutine { continuation ->
            try {
                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS,
                    Place.Field.PHONE_NUMBER,
                    Place.Field.WEBSITE_URI,
                    Place.Field.TYPES,
                    Place.Field.VIEWPORT,
                    Place.Field.RATING
                )

                val request = FetchPlaceRequest.newInstance(placeId, placeFields)

                placesClient!!.fetchPlace(request)
                    .addOnSuccessListener { response ->
                        val place = response.place
                        val details = PlaceDetails(
                            placeId = place.id ?: "",
                            name = place.name ?: "Unknown",
                            address = place.address ?: "",
                            latitude = place.latLng?.latitude ?: 0.0,
                            longitude = place.latLng?.longitude ?: 0.0,
                            phoneNumber = place.phoneNumber,
                            websiteUri = place.websiteUri?.toString(),
                            types = emptyList(), // Place types API not available in current version
                            rating = place.rating?.toDouble() ?: 0.0
                        )
                        Log.d(TAG, "Fetched details for place: ${details.name}")
                        continuation.resume(Result.success(details))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to fetch place details for ID: $placeId", exception)
                        continuation.resume(Result.failure(exception))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching place details", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Find nearby places by location and type
     */
    suspend fun findNearbyPlaces(
        location: LatLng,
        radiusMeters: Int,
        placeTypes: List<Place.Type> = emptyList()
    ): Result<List<PlaceSearchResult>> = withContext(Dispatchers.IO) {
        if (!isInitialized || placesClient == null) {
            return@withContext Result.failure(Exception("Places API not initialized"))
        }

        return@withContext suspendCoroutine { continuation ->
            try {
                // Note: Nearby search requires Places API Web Service or different implementation
                // This is a simplified version using autocomplete with location bias
                val request = FindAutocompletePredictionsRequest.builder()
                    .setQuery("") // Empty query to get nearby places
                    .setLocationBias(
                        RectangularBounds.newInstance(
                            LatLng(
                                location.latitude - (radiusMeters * 0.00001),
                                location.longitude - (radiusMeters * 0.00001)
                            ),
                            LatLng(
                                location.latitude + (radiusMeters * 0.00001),
                                location.longitude + (radiusMeters * 0.00001)
                            )
                        )
                    )
                    .setTypeFilter(
                        if (placeTypes.isNotEmpty()) 
                            TypeFilter.ESTABLISHMENT 
                        else 
                            TypeFilter.ESTABLISHMENT
                    )
                    .build()

                placesClient!!.findAutocompletePredictions(request)
                    .addOnSuccessListener { response ->
                        val results = response.autocompletePredictions.map { prediction ->
                            PlaceSearchResult(
                                placeId = prediction.placeId,
                                name = prediction.getPrimaryText(null).toString(),
                                address = prediction.getSecondaryText(null).toString(),
                                latitude = null,
                                longitude = null,
                                types = emptyList()
                            )
                        }
                        Log.d(TAG, "Found ${results.size} nearby places")
                        continuation.resume(Result.success(results))
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Nearby places search failed", exception)
                        continuation.resume(Result.failure(exception))
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching nearby places", e)
                continuation.resume(Result.failure(e))
            }
        }
    }

    /**
     * Get current location using device GPS (requires location permission)
     */
    suspend fun getCurrentLocation(): Result<LatLng> = withContext(Dispatchers.IO) {
        // This would require location services integration
        // For now, return a default location (you'll need to implement this)
        return@withContext Result.failure(Exception("Current location not implemented yet"))
    }
}

/**
 * Data class for place search results
 */
data class PlaceSearchResult(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val types: List<String>
)

/**
 * Data class for detailed place information
 */
data class PlaceDetails(
    val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val phoneNumber: String? = null,
    val websiteUri: String? = null,
    val types: List<String> = emptyList(),
    val rating: Double = 0.0
)

/**
 * Data class for geofence configuration
 */
data class GeofenceConfig(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val transitionTypes: Int, // Geofence.GEOFENCE_TRANSITION_ENTER, etc.
    val expirationDuration: Long = Geofence.NEVER_EXPIRE,
    val notificationResponsiveness: Int = 5000, // 5 seconds
    val placeId: String? = null
)