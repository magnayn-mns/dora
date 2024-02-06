package com.mns.dora

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*

val PROJECT = "pim-kafka-connector"
val JSESSIONID = null // don't fetch from JIRA
val JIRA_PROJECT = "PRICE"
val GH_TOKEN = "<<TOKEN>>"

//val otel = sdk()
//val tracer = otel.getTracer("io.opentelemetry.example")

suspend fun main(vararg args: String) = coroutineScope {
  try {
    PROJECT = args[0];

      //  go_active() // Active Items
      runBlocking {
         //go_historical()
          go_unmerged_pr()
     }
    //go_jira()

//      go_analytics()

  } catch(ex:Exception) {
      ex.printStackTrace();

  }
    println("Done.");
    System.exit(0);
}

suspend fun go_unmerged_pr() {
    val github = Github(PROJECT, GH_TOKEN)

    github.getBranches().forEach {
        println("${it}")
    }

    val unmergedPRs = github.getOpenPullRequests()
    println("${unmergedPRs.size}")
    unmergedPRs.forEach {
     //   println("PR ${it.number}, ${it.baseRef?.name}, ${it.baseRef?.id}, ${it.headRef?.name}, ${it.headRef?.id} ")
        println("  origin/${it.headRef?.name}")
    }


}


suspend fun go_analytics() {

    val ds:DataScrobbler = DataScrobbler(PROJECT, JSESSIONID, GH_TOKEN )

    // Get all the merged data
    ds.initialiseWithMergedPRs(15000)

    // get the JIRA tickets
   // ds.jira?.getTickets("HPDLP", 1, 3000)
   // ds.jira?.getTickets("NAV", 1, 2100)



}


suspend fun go_jira() {
    val ds:DataScrobbler = DataScrobbler(PROJECT, JSESSIONID, GH_TOKEN )

   // ds.jira.getTicket("SHOPBE-329")

    for(i in 240 downTo 1 ){
        println(i)
        ds.jira?.getTicket("${JIRA_PROJECT}-" + i)
    }


    dumpJIRAData(ds, "${JIRA_PROJECT}.csv")
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

    val ds:DataScrobbler = DataScrobbler(PROJECT, JSESSIONID, GH_TOKEN )
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
    if( ds.jira == null )
        return;

    val tickets = ds.jira.tickets.values
    val formatter = SimpleDateFormat("yyyy-MM-dd")
    File(fileName).printWriter().use { out ->

        out.print("Key,Title,Created,Status,Story Points,Story Points at Start,Started,Done")

        val states = ds.jira.seenStates
        states.forEach{
            out.print(",")
            out.print(it)
        }
        out.println(",Duration")

        tickets.forEach {
            println("Process " + it.key)
            val started = it.statusChanges.find { it.to == "In development" || it.to.equals("In Progress", true) }?.timestamp
            val done = it.statusChanges.findLast { it.to == "Done" }?.timestamp

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

            val m = it.durationInStates
            states.forEach{

                if( m[it] != null )
                    out.print(m[it]?.toFractionalHours())
                out.print(",")
            }


            /// End
            if( started != null && done != null ) {
                val dur = Duration.between(started, done)
                out.print(dur.toFractionalHours())
            }
            out.println("")


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


    val ds:DataScrobbler = DataScrobbler(PROJECT, JSESSIONID, GH_TOKEN )
    ds.initialiseWithMergedPRs(2000)
    var now = Date();
    val formatter = SimpleDateFormat("MMMdd")
    dumpMergedPRs(ds, "${formatter.format(now)}/${PROJECT}_merged_lttc.csv")
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

    File(fileName).parentFile.mkdirs();

    File(fileName).printWriter().use { out ->

        out.println("PR,Author,Created,TITLE,JIRA,LTTC,LTTC_PR,LTTC_OLD")
        ds.pullRequests()
            .filter { !it.isDraft  } // && !it.author.equals("renovate") && !it.author.equals("dependabot") }
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

fun dumpMergedPRs(ds:DataScrobbler, `fileName`:String) {
    var timelines = ds.timelines

    val visitor = DumpVisitor() //OtelVisitor(tracer)
    timelines.forEach {
        println("---")
        it.accept(visitor)
    }


    val formatter = SimpleDateFormat("yyyy-MM-dd")
    File(fileName).parentFile.mkdirs()

    File(fileName).printWriter().use { out ->

        out.println("PR,Author,Created,RFR,Merged,TITLE,JIRA,LTTC,LTTC_PR,LTTC_OLD")
        ds.pullRequests()
            .filter { !it.isDraft } //&& !it.author.equals("renovate")&& !it.author.equals("dependabot")}
            .forEach {
                var dt = "";
                var item = it.pr_ready_review?:it.createdAt;
                if( item != null )
                    dt = formatter.format(Date.from(item));

            out.println(
                "${it.number},${it.author},${formatter.format(Date.from(it.createdAt))},${dt},${
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















