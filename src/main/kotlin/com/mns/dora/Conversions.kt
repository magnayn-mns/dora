package com.mns.dora

import java.time.Duration
import java.time.Instant

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

    try {
        val lttc_old = Timeline("Commit_to_merge", pr.firstCommitDate, pr.mergedAt)
        lttc_old.attributes["type"] = "1ST_COMMIT_TO_MERGE"
        full.add(lttc_old)
    } catch(e:Exception) {
        println("Date error on pr ${pr.number}");
    }
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