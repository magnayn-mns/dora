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






fun convertTicketToTimeline(ticket:JiraTicket): TimelineParent {
    val full = TimelineParent("Jira Ticket ${ticket.key}")
    full.attributes.put("type", "JIRA")

    ticket.changes.forEach{ println(".. ticket status ${it.from} -> ${it.to}")}

    var dev = ticket.changes.find {
        val status = it.to.uppercase()
        val found = (status.equals("IN DEVELOPMENT") || status.equals("IN DEVELOPEMENT")|| status.equals("IN PROGRESS"))
        found
    }

    var done = ticket.changes.findLast { it.to.uppercase().equals("DONE") }

    if( dev != null ) {
        val leadTime = Timeline("LeadTime", ticket.created, dev.timestamp)
        full.add(leadTime)

        if( done != null )
        {
            val devTime = Timeline("Dev Time", dev.timestamp, done.timestamp)
            full.add(devTime)
        }
    }

    return full;
}

fun convertPRtoTimeline(pr: PRData): TimelineParent {
    val cmdt = pr.firstCommitDate
    val prdate = pr.createdAt
    val mergedAt = pr.mergedAt


    // Occasionally, the PR

    var rebased = false
    if (prdate.isBefore(cmdt)) {
        rebased = true
    }


    val full = TimelineParent("Development #${pr.number} ${pr.title}")

    val prSpan = Timeline("Pull Request ${pr.number}", pr.createdAt, pr.mergedAt)
    prSpan.attributes["pr_number"] = pr.number.toLong()
    prSpan.attributes["type"] = "PR"
    prSpan.attributes["rebased"] = rebased
    prSpan.attributes.put("pr_author", pr.author)
    prSpan.attributes.put("additions", pr.additions)
    prSpan.attributes.put("deletions", pr.deletions)
    prSpan.attributes.put("changedFiles", pr.changedFiles)
    prSpan.attributes.put("isDraft", pr.isDraft)
    full.add(prSpan)

    var pr_ready_review: Instant? = null
    var pr_1st_review: Instant? = null


    pr.timelineItems.filter { it?.timeStamp != null }.forEach {
        prSpan.addEvent(it?.item?.__typename ?: "", it?.timeStamp ?: Instant.MAX)

        if (it?.item?.timelineData?.onPullRequestReview != null && pr_1st_review == null) {
            pr_1st_review = it?.timeStamp
        }

        if (it?.item?.timelineData?.onReadyForReviewEvent != null && pr_ready_review == null) {
            pr_ready_review = it?.timeStamp
        }
    }

    if (pr_ready_review != null) {
        var reviewSpan = Timeline("Pull Request to Request Review", prdate, pr_ready_review!!)
        reviewSpan.attributes.put("type", "PR_TO_REVIEW_REQUEST")
        // TODO: Maybe.. child of PR?
        prSpan.add(reviewSpan)


        if (pr_1st_review != null) {

            if (pr_1st_review!!.isAfter(pr_ready_review)) {
                var ttfr = Timeline("Request Review to review", pr_ready_review!!, pr_1st_review!!)
                ttfr.attributes.put("type", "REQUEST_REVIEW_TO_REVIEW")
                // TODO: Maybe.. child of PR?
                prSpan.add(ttfr)
            }
        }

    } else if (pr_1st_review != null) {
        // Time to first review
        var ttfrev = Timeline("Time to first review", prdate, pr_1st_review!!)
        ttfrev.attributes.put("type", "FIRST_REVIEW")
        // TODO: Maybe.. child of PR?
        prSpan.add(ttfrev)
    }

    if( pr_ready_review == null ) {
        // assume it was created normally and not in draft.
        pr_ready_review = pr.createdAt
    }

    val lttc_old = Timeline("Commit_to_merge", pr.firstCommitDate, pr.mergedAt)
    lttc_old.attributes["type"] = "1ST_COMMIT_TO_MERGE"
    full.add(lttc_old)

    if( pr.mergedAt != null ) {
        prSpan.attributes.put("lttc_pr", Duration.between(pr.createdAt, pr.mergedAt))
        prSpan.attributes.put("lttc_old", Duration.between(pr.firstCommitDate, pr.mergedAt))
    } else {
        prSpan.attributes.put("lttc_pr", Duration.between(pr.createdAt, Instant.now()))
        prSpan.attributes.put("lttc_old", Duration.between(pr.firstCommitDate, Instant.now()))
    }



    if( pr_ready_review != null ) {
        if( pr.mergedAt != null )
            prSpan.attributes.put("lttc", Duration.between(pr_ready_review, pr.mergedAt))
        else
            prSpan.attributes.put("lttc", Duration.between(pr_ready_review, Instant.now()))
    }
    else
        println("Warn: no lttc on #${pr.number}")


    return full
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


