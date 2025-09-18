package com.adobe.acs.commons.contentsync;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

import static com.adobe.acs.commons.contentsync.ContentSyncJobConsumer.JOB_TOPIC;

@Component(
        service = JobExecutor.class,
        property = {
                JobExecutor.PROPERTY_TOPICS + "=" + JOB_TOPIC,
        }
)
public class ContentSyncJobConsumer implements JobExecutor {
    public static final String JOB_TOPIC = "acs-commons/contentsync/job";

    @Reference
    ContentSyncService syncService;

    @Override
    public JobExecutionResult process(Job job, JobExecutionContext jobContext) {
        long timeStarted = System.currentTimeMillis();
        try(RemoteInstance remoteInstance = syncService.createRemoteInstance(job)) {
            ExecutionContext context = new ExecutionContext(job, jobContext, remoteInstance);

            List<CatalogItem> items = syncService.getItemsToSync(context);
            jobContext.initProgress(items.size(), -1);

            int count = 1;
            long syncStarted = System.currentTimeMillis();
            for (CatalogItem item : items) {
                context.log( "[{0}] {1}",count, item.getPath());
                syncService.syncItem(item, context);

                updateProgress(count++, items.size(), syncStarted, jobContext);
            }
            context.log("sync-ed {0} resource(s) in {1} ms", items.size(), System.currentTimeMillis() - timeStarted);

            if(job.getProperty("delete") != null){
                syncService.delete(context);
            }

            Collection<String> foldersToSort = syncService.getNodesToSort(items, context);
            syncService.sortNodes(foldersToSort, context);

            syncService.startWorkflows(items, context);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            jobContext.log("{0}", sw.toString());
            return jobContext.result().cancelled();
        }
        jobContext.log("all done in {0} ms", System.currentTimeMillis() - timeStarted);
        return jobContext.result().succeeded();
    }

    void updateProgress(int count, int totalSize, long t0, JobExecutionContext context){
        long remainingCycles = totalSize - count;
        long pace = (System.currentTimeMillis() - t0) / count;
        long estimatedTime = remainingCycles * pace;

        String pct = String.format("%.0f", count*100./totalSize);
        String eta = DurationFormatUtils.formatDurationWords(estimatedTime, true, true);

        context.updateProgress(estimatedTime);
        context.incrementProgressCount(1);
        context.log("{0}%, ETA: {1}", pct, eta);
    }
}
