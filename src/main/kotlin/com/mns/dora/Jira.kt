package com.mns.dora

import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Duration

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.*
import kotlin.Exception
import kotlin.collections.HashMap


val dtf = DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .appendPattern("Z")
    .toFormatter(Locale.ENGLISH)

fun toInstant(d:String): Instant {
    val odt = OffsetDateTime.parse(d, dtf)

    return odt.toInstant()
}

class StatusChange(val timestamp:Instant, val field:String, val from:String, val to:String) {
}

class JiraTicket(val key: String, val jsonObject: JsonObject) {


    var hist = jsonObject.jsonObject.get("changelog")?.jsonObject?.get("histories")?.jsonArray

    val created:Instant get() {
        try {
            return toInstant(jsonObject.get("fields")?.jsonObject?.get("created")?.jsonPrimitive?.content ?: "")
        } catch(e:Exception) {
            return Instant.now()
        }
    }

    val title:String get() {
        return jsonObject.get("fields")?.jsonObject?.get("summary")?.jsonPrimitive?.content ?: ""
    }

    fun init() {
    }

    val changes:List<StatusChange> get() {
        val items: ArrayList<StatusChange> = ArrayList()
        hist?.forEach { items.addAll(process(it as JsonObject)) }

        items.sortBy { it.timestamp}
        return items
    }

    val durationInStates:Map<String, Duration> get() {
        val results = HashMap<String,Duration>()

        var clock:Instant = created
        var last:String? = null

        statusChanges.forEach {

            val now:Instant = it.timestamp
            val duration = Duration.between(clock, now)

            if( results.containsKey(it.from) ) {
                results.put(it.from, results.get(it.from)!!.plus(duration))
            } else {
                results.put(it.from,duration)
            }
            last = it.to
            clock=now
        }

        if( last != null ) {
            val duration = Duration.between(clock, Instant.now())
            if( results.containsKey(last) ) {
                results.put(last!!, results.get(last)!!.plus(duration))
            } else {
                results.put(last!!,duration)
            }
        }

        // If we got zero results, it's never transitioned
        if(results.isEmpty()) {
            results.put(status, Duration.between(created, Instant.now()))
        }

        return results
    }

    val statusChanges:List<StatusChange> get() {
        return changes.filter { it.field == "status"}
    }

    val storyPointsAtStart: Double get() {
        var sp = "0"
        changes.forEach {
            if( it.field == "Story Points" )
                sp = it.to
            if( it.field == "status" && it.to == "In development") {
                try {
                    return sp.toDouble()
                }
                catch(e:Exception) {
                    return 0.0
                }
            }
        }
        return 0.0;
    }

    val storyPoints: Double get() {
        val spp = jsonObject.get("fields")?.jsonObject?.get("customfield_10002")?.jsonPrimitive
        if( spp != null )
            return spp.doubleOrNull ?: 0.0;
        return 0.0
    }

    val status: String get() {
        return jsonObject.get("fields")?.jsonObject?.get("status")?.jsonObject?.get("statusCategory")?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "";
    }

    fun process(je:JsonObject):List<StatusChange> {

        val items:ArrayList<StatusChange> = ArrayList()
        val wh = toInstant(je.get("created")?.jsonPrimitive?.content ?: "")

        je.get("items")?.jsonArray?.forEach {
                var field = it.jsonObject.get("field")?.jsonPrimitive?.content
                var fromStatus = it.jsonObject.get("fromString")?.jsonPrimitive?.content?:""
                var toStatus = it.jsonObject.get("toString")?.jsonPrimitive?.content?:""


            items.add( StatusChange(wh, field?:"", fromStatus,toStatus))

            }



        return items
    }
}


class JiraFactory(val jSessionId:String) {

    val tickets = HashMap<String, JiraTicket>()

    val seenStates:Set<String> get() {
        // Cheesy
        val states = HashSet<String>()
        tickets.values.forEach{
            it.statusChanges.forEach {
                states.add(it.from)
                states.add(it.to)
            }
        }
        return states
    }

    fun getTicket(key: String):JiraTicket? {

        if( tickets.containsKey(key))
            return tickets[key]!!

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://jira.marksandspencer.app/rest/api/2/issue/${key}?expand=changelog")
            .header("Cookie", "JSESSIONID=${jSessionId}")
            .build()

        var resp = client.newCall(request).execute()
        val body = resp.body?.string() ?: ""

        try {
            var obj = Json.parseToJsonElement(body)


            var tkey = obj.jsonObject?.get("key")?.jsonPrimitive?.content
            if( tkey == null )
                return null

            var theTicket = JiraTicket(tkey ?: "", obj.jsonObject)
            theTicket.init()


            tickets[key] = theTicket

            return theTicket
        } catch(e:Exception) {
            println("Error in response ${body}")
            throw e
        }
    }

}

fun ticketIdFromString(title: String?): String? {
    if( title?.startsWith("#")?:true)
        return null;

    var regex = "[a-z]+-[0-9]+".toRegex(RegexOption.IGNORE_CASE);
    return regex.find(title ?: "")?.value?.uppercase()
}

fun teamIdFromTicketId(ticketId: String): String {
    var regex = "[A-Z]+".toRegex(RegexOption.IGNORE_CASE);
    return regex.find(ticketId)?.value?.uppercase() ?: "none"
}


