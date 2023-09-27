package com.mns.dora

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

        val prs = pullRequests.map{
            try {
                convertPRtoTimeline(it)
            } catch(_:Exception) {
                null
            } }

        prs.forEach{
           if( it != null )
               tlParent.add(it)
        }

        return tlParent
    }

}
