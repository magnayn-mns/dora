package com.mns.dora

import com.apollographql.apollo3.ApolloClient
import com.github.GetAllPullRequestsQuery
import com.github.GetOpenPullRequestsQuery
import com.github.GetPRDetailsQuery
import com.github.GetTeamMembersQuery
import com.github.fragment.PullRequest
import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.MetricBatchSenderFactory
import com.newrelic.telemetry.OkHttpPoster
import com.newrelic.telemetry.http.HttpPoster
import com.newrelic.telemetry.metrics.Gauge
import com.newrelic.telemetry.metrics.MetricBatchSender
import com.newrelic.telemetry.metrics.MetricBuffer
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import kotlin.collections.ArrayList





class PRData(val number: Int, val repo: String) {



    private var pr_details: com.github.GetPRDetailsQuery.PullRequest? = null

    suspend fun init(apolloClient: ApolloClient) {
        var data = apolloClient.query(GetPRDetailsQuery(repo, number)).execute();
        pr_details = data?.data?.repository?.pullRequest
    }

    val isDraft:Boolean get() {
        return pr_details?.isDraft ?: false
    }

    val firstCommitDate: Instant
        get() {
            val commit = commits?.get(0)
            val firstCommitDate = commit?.node?.pullRequestCommit?.commit?.committedDate


            return Instant.parse(firstCommitDate?.toString())
        }
/*
    if( pr.mergedAt != null ) {
        prSpan.attributes.put("lttc_pr", Duration.between(pr.createdAt, pr.mergedAt))
        prSpan.attributes.put("lttc_old", Duration.between(pr.firstCommitDate, pr.mergedAt))
    } else {
        prSpan.attributes.put("lttc_pr", Duration.between(pr.createdAt, Instant.now()))
        prSpan.attributes.put("lttc_old", Duration.between(pr.firstCommitDate, Instant.now()))
    }



    if( pr_ready_review != null ) {
        if( pr.mergedAt != null )
            prSpan.attributes.put("lttc", )
        else
            prSpan.attributes.put("lttc", Duration.between(pr_ready_review, Instant.now()))
  */
    val pr_ready_review:Instant? get() {
        return timelineItems.findLast {  it?.item?.timelineData?.onReadyForReviewEvent != null }
            ?.timeStamp
    }
    val lttc:Duration get() {
        return Duration.between(pr_ready_review?:createdAt, mergedAt?:Instant.now())
    }

    val lttc_pr:Duration get() {
        return Duration.between(createdAt, mergedAt?:Instant.now())
    }

    val lttc_old:Duration get() {
        return Duration.between(firstCommitDate, mergedAt?:Instant.now())
    }

    val commits
        get() =
            pr_details?.commits?.edges
    val additions
        get() =
            pr_details?.additions ?: 0


    val deletions
        get() = pr_details?.deletions ?: 0


    val changedFiles
        get() = pr_details?.changedFiles ?: 0

    val timeline
        get() = pr_details?.timelineItems?.nodes ?: ArrayList()

    val timelineItems
        get() = timeline.map {
            if (it != null) {
                PrTimelineItem(it)
            } else {
                null
            }
        }

    val createdAt: Instant get() = Instant.parse(pr_details?.createdAt.toString())
    val mergedAt: Instant? get() {
        val merged = pr_details?.mergedAt
        if( merged == null )
            return null
        return Instant.parse(merged.toString())
    }


    val title get() = pr_details?.title ?: ""

    val author get() = "${pr_details?.author?.login}"
}

class PrTimelineItem(public val item: GetPRDetailsQuery.Node3) {

    val timeStamp: Instant?
        get() {
            if (item.timelineData.onPullRequestReview != null) {
                return Instant.parse(item.timelineData.onPullRequestReview?.createdAt?.toString())
            }

            if (item.timelineData.onIssueComment != null) {
                return Instant.parse(item.timelineData.onIssueComment?.createdAt?.toString())
            }

            if (item.timelineData.onLabeledEvent != null) {
                return Instant.parse(item.timelineData.onLabeledEvent?.createdAt?.toString())
            }

            if (item.timelineData.onReadyForReviewEvent != null) {
                return Instant.parse(item.timelineData.onReadyForReviewEvent?.createdAt?.toString())
            }

            if (item.timelineData.onAddedToMergeQueueEvent != null) {
                return Instant.parse(item.timelineData.onAddedToMergeQueueEvent?.createdAt?.toString())
            }


            return null
        }


}


class Github {
    val apolloClient: ApolloClient
    val repo:String

    constructor(repo:String = "onyx") {
        this.repo = repo

        apolloClient = ApolloClient.Builder()
            .serverUrl("https://api.github.com/graphql")
            .addHttpHeader(
                "Authorization",
                "Bearer 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"
            ) // .networkTransport(QueueTestNetworkTransport())
            .build()
    }

    suspend fun getPR(number: Int): PRData {
        val prData = PRData(number, repo)
        prData.init(apolloClient)
        return prData
    }

    suspend fun getOpenPullRequests(limit: Int = 300): ArrayList<PullRequest> {
        val list = ArrayList<PullRequest>()
        var cursor: String? = null
        while (true) {

            println("cursor ${cursor}")
            var v: com.apollographql.apollo3.api.Optional<String?> = com.apollographql.apollo3.api.Optional.Absent
            if (cursor != null)
                v = com.apollographql.apollo3.api.Optional.Present(cursor)

            var itemcount = limit
            if( limit > 100)
                itemcount = 100

            var resp2 = apolloClient.query(GetOpenPullRequestsQuery(repo, itemcount, cursor = v)).execute()

            resp2.data?.repository?.pullRequests?.nodes?.forEach {
                if( it?.pullRequest != null )
                    list.add(it?.pullRequest)
            }

            cursor = resp2.data?.repository?.pullRequests?.pageInfo?.startCursor
            if (cursor == null || list.size >= limit)
                return list
        }
    }
    suspend fun getMergedPRs(limit: Int = 300): ArrayList<PullRequest> {

        val list = ArrayList<PullRequest>()
        var cursor: String? = null
        while (true) {

            println("cursor ${cursor}")
            var v: com.apollographql.apollo3.api.Optional<String?> = com.apollographql.apollo3.api.Optional.Absent
            if (cursor != null)
                v = com.apollographql.apollo3.api.Optional.Present(cursor)

            var itemcount = limit
            if( limit > 100)
                itemcount = 100

            var resp2 = apolloClient.query(GetAllPullRequestsQuery(repo, itemcount, cursor = v)).execute()

            resp2.data?.repository?.pullRequests?.nodes?.forEach {
               if( it?.pullRequest != null )
                   list.add(it?.pullRequest)
            }

            cursor = resp2.data?.repository?.pullRequests?.pageInfo?.startCursor
            if (cursor == null || list.size >= limit)
                return list
        }
    }
}
/*
class Processor {
val apolloClient: ApolloClient
val tracer: Tracer

constructor() {
    apolloClient = ApolloClient.Builder()
        .serverUrl("https://api.github.com/graphql")
        .addHttpHeader(
            "Authorization",
            "Bearer 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"
        ) // .networkTransport(QueueTestNetworkTransport())
        .build()

    var otel = sdk()
    tracer = otel.getTracer("io.opentelemetry.example")

}

   suspend fun process() {
       generatePRData()
   }


   suspend fun generatePRData() {


       var resp2 = apolloClient.query(GetAllPullRequestsQuery("onyx", 100)).execute();

       resp2.data?.repository?.pullRequests?.nodes?.forEach {
           val pr = it?.pullRequest

           if (pr != null) {
               val prData = PRData(pr.number)
               prData.init(apolloClient)
               processPR(apolloClient, prData)
           }
           Thread.sleep(10000)
           println("")
       }
   }

 suspend fun processPR(apolloClient: ApolloClient, prData: PRData) {

       println("${prData.number} ${prData.createdAt} ${prData.mergedAt}")
       val commit = prData.commits?.get(0)
       val commitDate = commit?.node?.pullRequestCommit?.commit?.committedDate
       val createdDate = prData.createdAt;

       println("  ${commitDate}")


       val cmdt = Instant.parse(commitDate?.toString())
       val prdate = Instant.parse(createdDate?.toString())


       val mergedAt = prData.mergedAt

       val res: Duration = Duration.between(cmdt, prdate)
       println("${res}")

       var workSpan = tracer.spanBuilder("PR #${prData.number}")
           .setNoParent()
           .setStartTimestamp(cmdt)
           .startSpan();

       workSpan.setAttribute("Name", prData.title);
       workSpan.setAttribute("Author", prData.author);
       val prno: Long = prData.number.toLong()

       Baggage.builder()
           .put("pr_author", prData.author)
           .put("additions", "%%int%%${prData.additions}")
           .put("deletions", "%%int%%${prData.deletions}")
           .put("changedFiles", "%%int%%${prData.changedFiles}")
           .build().storeInContext(Context.current()).makeCurrent().use {

               workSpan.setAttribute("PR", prno);

               workSpan.makeCurrent().use {

                   var workDelay = tracer.spanBuilder("1st Commit To PR")
                       .setStartTimestamp(cmdt)
                       .setAttribute("type", "PR_DELAY")
                       .startSpan();
                   workDelay.end(prdate);

                   var prSpan = tracer.spanBuilder("Pull Request")
                       .setStartTimestamp(prdate)
                       .setAttribute("type", "PR")
                       .startSpan();

                   prSpan.makeCurrent().use {

                       var pr_ready_review: Instant? = null
                       var pr_1st_review: Instant? = null


                       prData.timeline.forEach {
                           var timeStamp: Instant? = null

                           if (it?.onPullRequestReview != null) {
                               timeStamp = Instant.parse(it?.onPullRequestReview?.createdAt?.toString())
                               if (pr_1st_review == null) {
                                   pr_1st_review = timeStamp
                               }

                               if (it?.onIssueComment != null) {
                                   timeStamp = Instant.parse(it?.onIssueComment?.createdAt?.toString())
                               }

                               if (it?.onLabeledEvent != null) {
                                   timeStamp = Instant.parse(it?.onLabeledEvent?.createdAt?.toString())
                               }

                               if (it?.onReadyForReviewEvent != null) {
                                   timeStamp = Instant.parse(it?.onReadyForReviewEvent?.createdAt?.toString())
                                   pr_ready_review = timeStamp
                               }

                               if (it?.onAddedToMergeQueueEvent != null) {
                                   timeStamp = Instant.parse(it?.onAddedToMergeQueueEvent?.createdAt?.toString())
                               }

                               if (timeStamp != null) {
                                   prSpan.addEvent(it?.__typename, timeStamp)
                               }
                           }


                       }
                       if (pr_ready_review != null) {
                           var reviewSpan = tracer.spanBuilder("Pull Request to Request Review")
                               .setStartTimestamp(prdate)
                               .setAttribute("type", "PR_TO_REVIEW_REQUEST")
                               .startSpan();
                           reviewSpan.end(pr_ready_review)

                           println(" RS: ${prdate} -> ${pr_ready_review}")

                           if (pr_1st_review != null) {

                               if (pr_1st_review!!.isAfter(pr_ready_review)) {
                                   var ttfr = tracer.spanBuilder("Request Review to review")
                                       .setStartTimestamp(pr_ready_review)
                                       .setAttribute("type", "REVIEW_REQUEST_TO_REVIEW")
                                       .startSpan();
                                   ttfr.end(pr_1st_review)

                                   println(" RR2R: ${pr_ready_review} -> ${pr_1st_review}")
                               }
                           }

                       }
                       if (pr_1st_review != null) {
                           // Time to first review
                           var ttfrev = tracer.spanBuilder("Time to first review")
                               .setStartTimestamp(prdate)
                               .setAttribute("type", "FIRST_REVIEW")
                               .startSpan();
                           ttfrev.end(pr_1st_review)
                           println(" FIRST_REVIEW: ${prdate} -> ${pr_1st_review}")
                       }

                       prSpan.end(mergedAt);
                   }
               }

               workSpan.end(mergedAt)
           }
       Thread.sleep(2000)
   }


}



fun sendMetrics(licenseKey: String) {


val factory: MetricBatchSenderFactory =
    MetricBatchSenderFactory.fromHttpImplementation(Supplier<HttpPoster> { OkHttpPoster() } as Supplier<HttpPoster?>?)
val sender: MetricBatchSender =
    MetricBatchSender.create(factory.configureWith(licenseKey).useLicenseKey(true).build())

val metricBuffer = MetricBuffer(getCommonAttributes())

for (i in 0..9) {
    val currentTemperature: Gauge = getLTTC()
    println("Recording temperature: $currentTemperature")
    metricBuffer.addMetric(currentTemperature)
    TimeUnit.SECONDS.sleep(5) // 5 seconds between measurements
}

sender.sendBatch(metricBuffer.createBatch())
}

private fun getCommonAttributes(): Attributes? {
return Attributes().put("pr.title", "<title>>")
}

private fun getLTTC(): Gauge {
return Gauge(
    "lttc",
    100.0,
    System.currentTimeMillis(),
    getLTTCAttributes()
)
}

private fun getLTTCAttributes(): Attributes? {
val attributes = Attributes()
attributes.put("change.startDate", 1)
attributes.put("change.endDate", 1)

return attributes
}

suspend fun old(apolloClient: ApolloClient) {
val q = GetTeamMembersQuery("price-domain", "DigitalInnovation")

var resp = apolloClient.query(q).execute();
println(resp.data)

var resp2 = apolloClient.query(GetAllPullRequestsQuery("onyx", 100)).execute();

println(resp2.data)

resp2.data?.repository?.pullRequests?.nodes?.forEach {
    val pr = it?.pullRequest

    println("${pr?.number} ${pr?.createdAt} ${pr?.mergedAt}")
    val commit = pr?.commits?.edges?.get(0)
    val commitDate = commit?.node?.pullRequestCommit?.commit?.committedDate
    val createdDate = pr?.createdAt;

    println("  ${commitDate}")


    val cmdt = Instant.parse(commitDate?.toString())
    val prdate = Instant.parse(createdDate?.toString())

    val res: Duration = Duration.between(cmdt, prdate)
    println("${res}")


}

println("")
Thread.sleep(2000)
}*/