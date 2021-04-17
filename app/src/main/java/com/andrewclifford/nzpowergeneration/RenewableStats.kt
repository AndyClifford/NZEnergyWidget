package com.andrewclifford.nzpowergeneration

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import kotlinx.coroutines.*
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.IOException


/**
 * Implementation of App Widget functionality.
 */
class RenewableStats : AppWidgetProvider() {

    override fun onUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
    ) {

        val intent = Intent(context, RenewableStats::class.java)
        val pending = PendingIntent.getService(context, 0, intent, 0)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
        val interval = (1000 * 60).toLong()
        alarm.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), interval, pending)

        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created

    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
) {
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.renewable_stats)

    GlobalScope.launch() {
        while (true) {

            try {
                val response: Connection.Response = Jsoup.connect("https://www.transpower.co.nz/power-system-live-data").timeout(10000).execute()

                if (response.statusCode() == 200) {
                    val doc = response.parse()
                    val table: Elements = doc.select("table[id=pgen-table]") // Get the generation table

                    val values: Elements = table.select("span[class=generation]") // Extract values from the generation table
                    var total = 0F
                    var renewable = 0F
                    val renewableIndexes = arrayOf(0, 1, 2, 7, 8)

                    for ((i, value: Element) in values.withIndex()) {
                        total += value.text().toFloat()
                        if (renewableIndexes.contains(i)) {
                            renewable += value.text().toFloat()
                        }
                    }
                    val percentRenewable = ((renewable / total) * 100.0).toInt()

                    if (percentRenewable >= 75) {
                        views.setTextColor(R.id.percentage_text, Color.parseColor("#FF1FCC26"))
                    } else {
                        views.setTextColor(R.id.percentage_text, Color.parseColor("#FFE80909"))
                    }

                    views.setTextViewTextSize(R.id.percentage_text, TypedValue.COMPLEX_UNIT_SP, 40F)
                    views.setTextViewText(R.id.percentage_text, percentRenewable.toString())
                } else {
                    views.setTextColor(R.id.percentage_text, Color.parseColor("#000000"))
                    views.setTextViewTextSize(R.id.percentage_text, TypedValue.COMPLEX_UNIT_SP, 32F)
                    views.setTextViewText(R.id.percentage_text, "NA")
                }
            } catch (e: IOException) {
                views.setTextViewTextSize(R.id.percentage_text, TypedValue.COMPLEX_UNIT_SP, 32F)
                views.setTextViewText(R.id.percentage_text, "NA")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            delay(300000)
        }
    }
}