package com.mns.dora

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.util.*

val PROJECT = "customer-order"
val JSESSIONID = "4C86F19C6D4159E458F58B6AA83EAF76"
val JIRA_PROJECT = "HPDLP"

val otel = sdk()
val tracer = otel.getTracer("io.opentelemetry.example")

suspend fun main(vararg args: String) = coroutineScope {
  //  go_active() // Active Items
 //   go_historical()
    go_jira()
}

suspend fun go_jira() {
    val ds:DataScrobbler = DataScrobbler(PROJECT, JSESSIONID )

    ds.jira.getTicket("HPDLP-2600")

    for(i in 2695 downTo 1 ){
        println(i)
        ds.jira.getTicket("HPDLP-" + i)
    }


    dumpJIRAData(ds, "hpdlp_jira.csv")
}

fun dump(ticket:JiraTicket) {
    println("---")
    println(ticket.key)
    println(ticket.storyPoints)
    println(ticket.storyPointsAtStart)

    /*
    ticket.changes.forEach {
        println("${it.timestamp} ${it.field}: ${it.from}->${it.to}")
    }*/

    val started = ticket.statusChanges.find { it.to == "In development" }?.timestamp
    val done = ticket.statusChanges.find { it.to == "Done" }?.timestamp

    println(started)
    println(done)

}

suspend fun go_active() {

    val ds:DataScrobbler = DataScrobbler(PROJECT, JSESSIONID )
    ds.initialiseWithOpenPulls(1000)
    //ds.processPullRequest(537)
    dumpActivePRs(ds, "price_active_lttc.csv")
    dumpInfo(ds)
}

fun Duration.toFractionalHours():String {
    val mins = this.toMinutes().toDouble() / 60
    return String.format("%.1f", mins)
}


fun dumpJIRAData(ds:DataScrobbler, fileName: String) {
    val tickets = ds.jira.tickets.values
    val formatter = SimpleDateFormat("yyyy-MM-dd")
    File(fileName).printWriter().use { out ->

        out.println("Key,Title,Created,Status,Story Points,Story Points at Start,Started,Done,Duration")
        tickets.forEach {
            println("Process " + it.key)
            val started = it.statusChanges.find { it.to == "In development" }?.timestamp
            val done = it.statusChanges.find { it.to == "Done" }?.timestamp

            out.print(it.key + ",")
            out.print(it.title.replace(",","-") + ",")
            out.print(formatter.format(Date.from(it.created)) +",")
            out.print(it.status + ",")
            out.print(it.storyPoints.toString() + ",")
            out.print(it.storyPointsAtStart.toString() + ",")
            if( started != null )
            {
                out.print(formatter.format(Date.from(started)) + ",")
            }
            else
            {
                out.print(",")
            }

            if( done != null )
            {
                out.print(formatter.format(Date.from(done)) + ",")
            }
            else
            {
                out.print(",")
            }


            /// End
            if( started != null && done != null ) {
                val dur = Duration.between(started, done)
                out.print(dur.toFractionalHours())
            }
            out.println("")

        }
    }

}

suspend fun go_historical() {

    val ds:DataScrobbler = DataScrobbler(PROJECT, JSESSIONID )
    ds.initialiseWithMergedPRs(2000)

    dumpMergedPRs(ds, "co_merged_lttc.csv")
    dumpInfo(ds)
}

fun dumpInfo(ds:DataScrobbler) {
    var timelines = ds.timelines

    val visitor = DumpVisitor() //OtelVisitor(tracer)
    timelines.forEach {
        println("---")
        it.accept(visitor)
    }
}



fun dumpActivePRs(ds:DataScrobbler, fileName: String) {
    val formatter = SimpleDateFormat("yyyy-MM-dd")

    File(fileName).printWriter().use { out ->

        out.println("PR,Author,Created,TITLE,JIRA,LTTC,LTTC_PR,LTTC_OLD")
        ds.pullRequests()
            .filter { !it.isDraft && !it.author.equals("renovate") && !it.author.equals("dependabot") }
            .forEach {
            out.println(
                "${it.number},${it.author},${formatter.format(Date.from(it.createdAt))},${
                    it.title.replace(
                        ",",
                        "-"
                    )
                },${ticketIdFromString(it.title)},${it.lttc.toFractionalHours()},${it.lttc_pr.toFractionalHours()},${it.lttc_old.toFractionalHours()}"
            )
        }
    }
}

fun dumpMergedPRs(ds:DataScrobbler, fileName:String) {
    var timelines = ds.timelines

    val visitor = DumpVisitor() //OtelVisitor(tracer)
    timelines.forEach {
        println("---")
        it.accept(visitor)
    }

    /*
    val otelVisitor = OtelVisitor(tracer)
    timelines.forEach{
        it.accept(otelVisitor)
    }

     */

    val formatter = SimpleDateFormat("yyyy-MM-dd")

    File(fileName).printWriter().use { out ->

        out.println("PR, Author,Created,Merged,TITLE,JIRA,LTTC,LTTC_PR,LTTC_OLD")
        ds.pullRequests()
            .filter { !it.isDraft && !it.author.equals("renovate")&& !it.author.equals("dependabot")}
            .forEach {
            out.println(
                "${it.number},${it.author},${formatter.format(Date.from(it.createdAt))},${
                    formatter.format(
                        Date.from(
                            it.mergedAt
                        )
                    )
                },${
                    it.title.replace(
                        ",",
                        "-"
                    )
                },${ticketIdFromString(it.title)},${it.lttc.toFractionalHours()},${it.lttc_pr.toFractionalHours()},${it.lttc_old.toFractionalHours()}"
            )
        }
    }

}










