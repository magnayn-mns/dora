package com.mns.dora

import java.util.ArrayList

class WorkItem(val id:String) {
    var jiraTicket:JiraTicket? = null
    var pullRequests = ArrayList<PRData>()

    fun toTimeline():TimelineItem {
        var tlParent = TimelineParent("#${id}")

        var ticket = ticketIdFromString(id)
        if( ticket != null )
        {
            tlParent.baggage.put("TEAM", teamIdFromTicketId(ticket));
        }

        if( jiraTicket != null ) {
            val tlJira = convertTicketToTimeline(jiraTicket!!)
            tlParent.add(tlJira)
        }

        val prs = pullRequests.map{ convertPRtoTimeline(it) }

        prs.forEach{
            tlParent.add(it)
        }

        return tlParent
    }

}
