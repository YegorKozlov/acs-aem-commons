package com.adobe.acs.commons.contentsync;

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;

import java.util.HashMap;

public class ExecutionContext extends HashMap<String, Object> {
    public static String REMOTE_ITEMS = "remoteItems";
    public static String UPDATE_STRATEGY = "updateStrategy";


    final JobExecutionContext jobContext;
    final Job job;
    final RemoteInstance remoteInstance;

    public ExecutionContext(Job job, JobExecutionContext jobContext, RemoteInstance remoteInstance){
        this.jobContext = jobContext;
        this.job = job;
        this.remoteInstance = remoteInstance;

        log("root: {0}", job.getProperty("root"));
        log("dryRun: {0}", dryRun());
    }

    public void log(String msg, Object... args){
        jobContext.log(msg, args);
    }

    public Job getJob(){
        return job;
    }

    public RemoteInstance getRemoteInstance(){
        return remoteInstance;
    }

    public boolean dryRun(){
        return job.getProperty("dryRun") != null;
    }
}
