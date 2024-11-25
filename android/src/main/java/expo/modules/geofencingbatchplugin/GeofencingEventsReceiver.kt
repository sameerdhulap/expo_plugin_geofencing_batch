package expo.modules.geofencingbatchplugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.batch.android.Batch
import com.batch.android.BatchEventAttributes
import com.webgeoservices.woosmapgeofencingcore.database.POI
import com.webgeoservices.woosmapgeofencingcore.database.WoosmapDb
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GeofencingEventsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofencingReceiver"
    }

    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast")
        executorService.execute {
            try {
                Log.d(TAG, "executorService.execute")
                val regionData = JSONObject(intent.getStringExtra("regionLog") ?: "{}")
                val attributes = BatchEventAttributes().apply {
                    put("identifier", regionData.getString("identifier"))
                    put("event_name", regionData.getString("eventname"))
                    put("longitude", regionData.getDouble("longitude"))
                    put("latitude", regionData.getDouble("latitude"))
                    put("date", regionData.getLong("date"))
                    put("did_enter", regionData.getBoolean("didenter"))
                    put("radius", regionData.getDouble("radius"))
                    put("spent_time", regionData.getInt("spenttime"))
                    put("from_position_detection", regionData.getBoolean("frompositiondetection"))
                }

                // Fetch the POI from the database
                val poi = WoosmapDb.getInstance(context).getPOIsDAO()
                    .getPOIbyStoreId(regionData.getString("identifier"))
                poi?.let {
                    it.idStore?.let { idStore -> attributes.put("id_store", idStore) }
                    it.name?.let { name -> attributes.put("name", name) }
                    it.address?.let { address -> attributes.put("address", address) }
                    it.city?.let { city -> attributes.put("city", city) }
                    it.contact?.let { contact -> attributes.put("contact", contact) }
                    it.countryCode?.let { countryCode -> attributes.put("country_code", countryCode) }
                    attributes.put("distance", it.distance)
                    it.types?.let { types -> attributes.put("types", types) }
                    it.tags?.let { tags -> attributes.put("tags", tags) }
                    it.zipCode?.let { zipCode -> attributes.put("zip_code", zipCode) }

                    it.userProperties?.let { userProperties ->
                        val userPropertiesJson = JSONObject(userProperties)
                        processJSONObject(userPropertiesJson, attributes, "")
                    }

                    // Track the event with Batch
                    try {
                        Batch.Profile.trackEvent(regionData.getString("eventname"), attributes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tracking event in Batch", e)
                    }
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Error processing geofencing event", exception)
            }
        }
    }

    // Helper function to process JSONObject recursively
    @Throws(JSONException::class)
    private fun processJSONObject(jsonObject: JSONObject, attributes: BatchEventAttributes, parentKey: String) {
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            if (attributes.attributes.size == 20) break
            val key = keys.next()
            val value = jsonObject[key]
            val fullKey = if (parentKey.isEmpty()) key else "${parentKey}_$key"
            val formattedKey = fullKey.replace(Regex("([A-Z])"), "_$1").lowercase()

            when (value) {
                is JSONObject -> processJSONObject(value, attributes, formattedKey)
                is String -> attributes.put(formattedKey, value)
                is Int -> attributes.put(formattedKey, value)
                is Double -> attributes.put(formattedKey, value)
                is Long -> attributes.put(formattedKey, value)
                is Boolean -> attributes.put(formattedKey, value)
            }
        }
    }
}