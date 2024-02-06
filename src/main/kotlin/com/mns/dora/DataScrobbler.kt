package com.mns.dora

import java.util.*

class DataScrobbler {


    val github:Github
    val jira:JiraFactory?

    // Keyed on ID
    val workItems = HashMap<String, WorkItem>()

    constructor(repoName: String, jiraSessionId:String?, githubToken:String) {
        if( jiraSessionId != null )
            jira = JiraFactory(jiraSessionId);
        else
            jira = null;

        github = Github(repoName, githubToken)
    }

    val timelines: List<TimelineItem>
        get() { return workItems.values.map { it.toTimeline() } }

    suspend fun processPullRequest(number:Int) {
        val pr = github.getPR(number)
        var ticketId = ticketIdFromString(pr.title)

        // No id, just create one
        if (ticketId == null)
            ticketId = "#${UUID.randomUUID().toString()}"

        val workItem = getWorkItemForTicketId(ticketId)
        workItem.pullRequests.add(pr)

    }

    fun pullRequests(): ArrayList<PRData> {
        val prs = ArrayList<PRData>()

        workItems.values.forEach{
            prs.addAll( it.pullRequests )
        }

        return prs;
    }

    fun getWorkItemForTicketId(ticketId: String): WorkItem {

        // Exists?
        if( workItems.containsKey(ticketId))
            return workItems[ticketId]!!

        val workItem = WorkItem(ticketId)
        workItems[ticketId] = workItem

        if( !ticketId.startsWith("#") && jira != null )
            workItem.jiraTicket = jira.getTicket(ticketId)
        return workItem
    }

    suspend fun initialiseWithOpenPulls(limit:Int)  {
        val openPRs = github.getOpenPullRequests(limit)
        openPRs.forEach {
            println("PR ${it.number}")
            processPullRequest(it.number)
        }
    }

    suspend fun initialiseWithMergedPRs(limit:Int) {
        val mergedPRs = github.getMergedPRs(limit)

        mergedPRs.forEach {
            println("PR ${it.number}")
            processPullRequest(it.number)
        }
    }

    suspend fun initialiseWithUnmergedPRs() {
        val unmergedPRs = github.getOpenPullRequests()

        unmergedPRs.forEach {
            println("PR ${it.number}")
            processPullRequest(it.number)
        }

    }
}