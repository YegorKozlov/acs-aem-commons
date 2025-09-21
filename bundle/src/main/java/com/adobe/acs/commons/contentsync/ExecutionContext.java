package com.adobe.acs.commons.contentsync;

import com.adobe.acs.commons.contentsync.io.JobLogWriter;
import org.apache.sling.event.jobs.Job;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;

import static com.adobe.acs.commons.contentsync.ContentSyncService.JOB_RESULTS_BASE_PATH;

public class ExecutionContext extends HashMap<String, Object> implements AutoCloseable {
    public static String REMOTE_ITEMS = "remoteItems";
    public static String UPDATE_STRATEGY = "updateStrategy";

    final Job job;
    final RemoteInstance remoteInstance;
    final JobLogWriter logWriter;

    public ExecutionContext(Job job, ContentSyncService syncService) throws Exception {
        this.job = job;
        this.remoteInstance =  syncService.createRemoteInstance(job);

        String logPath = ExecutionContext.getLogPath(job);
        this.logWriter = new JobLogWriter(syncService.getResourceResolverFactory(), logPath);

        log("remote host: {0}", remoteInstance.getHostConfiguration().getHost());
        log("sync root: {0}", job.getProperty("root"));
    }

    public static String getLogPath(Job job){
        return String.format(JOB_RESULTS_BASE_PATH + "/%s", job.getId());
    }

    public void log(String msg, Object... args)  {
        try {
            if (dryRun()) msg = "[dry-run] " + msg;
            String logEntry = MessageFormat.format(msg, args);
            logWriter.write(logEntry);
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    public Job getJob() {
        return job;
    }

    public RemoteInstance getRemoteInstance() {
        return remoteInstance;
    }

    public boolean dryRun() {
        return job.getProperty("dryRun") != null;
    }

    public void close() {
        try {
            remoteInstance.close();
            logWriter.close();
        } catch (IOException  e){
            throw new RuntimeException(e);
        }
    }

}
