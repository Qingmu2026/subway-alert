package com.subwayalert.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * 用户路线配置
 */
data class SubwayRoute(
    val city: String,
    val line: String,
    val start: String,
    val end: String,
    val alertBeforeStations: Int = 2
)

/**
 * 路线管理器
 */
class RouteManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("subway_route", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveRoute(route: SubwayRoute) {
        prefs.edit().putString("route", gson.toJson(route)).apply()
    }

    fun getRoute(): SubwayRoute? {
        val json = prefs.getString("route", null) ?: return null
        return try {
            gson.fromJson(json, SubwayRoute::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clearRoute() {
        prefs.edit().remove("route").apply()
    }

    fun hasRoute(): Boolean = getRoute() != null
}
