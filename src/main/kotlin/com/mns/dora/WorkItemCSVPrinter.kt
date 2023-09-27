package com.mns.dora

import java.io.File
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*

val formatter = SimpleDateFormat("yyyy-MM-dd")

fun dumpWorkItemsCSV(ds:DataScrobbler, fileName:String) {
    var timelines = ds.timelines


    var i:Int =0;


    File(fileName).printWriter().use { out ->


        if( ds.jira != null ) {
            out.print(jiraHeader(ds))
        }

        out.print(",PRS,Author,Created,Merged,TITLE,JIRA,LTTC,LTTC_PR,LTTC_OLD")


        out.println("Index");

        out.println();

        ds.workItems.values.forEach {



            if( it.jiraTicket != null ) {
                val ticket = it.jiraTicket!!


                out.print(",");

                if( ds.jira != null )
                    out.print(jiraPart(ds.jira, ticket))

                out.print(it.pullRequests.size)



                out.println(i++);
            }
        }


//        ds.jira?.tickets?.values?.forEach {
//            it.
//
//            out.println(
//                "${it.number},${it.author},${formatter.format(Date.from(it.createdAt))},${
//                    formatter.format(
//                        Date.from(
//                            it.mergedAt
//                        )
//                    )
//                },${
//                    it.title.replace(
//                        ",",
//                        "-"
//                    )
//                },${ticketIdFromString(it.title)},${it.lttc.toFractionalHours()},${it.lttc_pr.toFractionalHours()},${it.lttc_old.toFractionalHours()}"
//            )
//        }

        ds.pullRequests()
            .filter { !it.isDraft } //&& !it.author.equals("renovate")&& !it.author.equals("dependabot")}
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

fun jiraHeader(ds: DataScrobbler): List<String> {
    var header = mutableListOf( "Key","Title","Created","Status","Story Points","Story Points at Start","Started","Done");

    val states = ds.jira?.seenStates
    states?.forEach {
        header.add(it)
    }
    header.add("Duration");

    return header
}

fun jiraPart(jiraFactory: JiraFactory, ticket: JiraTicket):String {

    var result:String = ""

    if( ticket == null ) {
        return ",".repeat(9 + jiraFactory.seenStates.size)
    }

    val started = ticket.statusChanges.find { it.to == "In development" || it.to.equals("In Progress", true) }?.timestamp
    val done = ticket.statusChanges.findLast { it.to == "Done" }?.timestamp

    result += ticket.key + ","
    result += ticket.title.replace(",","-") + ","
    result += formatter.format(Date.from(ticket.created)) +","

    result += ticket.status + ","
    result += ticket.storyPoints.toString() + ","
    result += ticket.storyPointsAtStart.toString() + ","
    if( started != null )
    {
        result += formatter.format(Date.from(started))
    }

    result += ","


    if( done != null )
    {
        result += formatter.format(Date.from(done)) + ","
    }

    val m = ticket.durationInStates
    jiraFactory.seenStates.forEach{

        if( m[it] != null )
            result += (m[it]?.toFractionalHours());

        result += ","
    }


    /// End
    if( started != null && done != null ) {
        val dur = Duration.between(started, done)
        result += dur.toFractionalHours()
    }


    return result;
}
