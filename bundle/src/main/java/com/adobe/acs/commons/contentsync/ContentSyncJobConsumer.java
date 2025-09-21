package com.adobe.acs.commons.contentsync;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandles;
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
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static final String JOB_TOPIC = "acs-commons/contentsync/job";

    @Reference
    ContentSyncService syncService;

    @Override
    public JobExecutionResult process(Job job, JobExecutionContext jobContext) {
        long timeStarted = System.currentTimeMillis();
        try (ExecutionContext context = new ExecutionContext(job, syncService)){
            List<CatalogItem> items = syncService.getItemsToSync(context);
            jobContext.initProgress(items.size(), -1);

            int count = 1;
            long syncStarted = System.currentTimeMillis();
            for (CatalogItem item : items) {
                context.log( "[{0}] {1}",count, item.getPath());
                syncService.syncItem(item, context);

                updateProgress(count++, items.size(), syncStarted, jobContext, context);
            }
            context.log("sync-ed {0} resource(s) in {1} ms", items.size(), System.currentTimeMillis() - timeStarted);

            syncService.deleteUnknownResources(context);

            Collection<String> foldersToSort = syncService.getNodesToSort(items, context);
            syncService.sortNodes(foldersToSort, context);

            syncService.startWorkflows(items, context);
            context.log("all done in {0} ms", System.currentTimeMillis() - timeStarted);
            return jobContext.result().succeeded();
        } catch (Exception e) {
            log.error("content-sync job failed: {}", job.getId(), e);

            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            jobContext.log("{0}", sw.toString());
            return jobContext.result().cancelled();
        }
    }

    void updateProgress(int count, int totalSize, long t0, JobExecutionContext jobContext, ExecutionContext context){
        long remainingCycles = totalSize - count;
        long pace = (System.currentTimeMillis() - t0) / count;
        long estimatedTime = remainingCycles * pace;

        String pct = String.format("%.0f", count*100./totalSize);
        String eta = DurationFormatUtils.formatDurationWords(estimatedTime, true, true);

        jobContext.updateProgress(estimatedTime);
        jobContext.incrementProgressCount(1);
        context.log("{0}%, ETA: {1}", pct, eta);
    }
}
