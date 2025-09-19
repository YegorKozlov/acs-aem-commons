package com.adobe.acs.commons.contentsync;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;

import javax.jcr.RepositoryException;
import javax.json.JsonObject;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ContentSyncService {
    List<CatalogItem> getRemoteItems(ExecutionContext context) throws Exception;

    List<CatalogItem> getItemsToSync(ExecutionContext context) throws Exception;

    void syncItem(CatalogItem item, ExecutionContext context) throws Exception;

    RemoteInstance createRemoteInstance(Job job) throws Exception;

    Collection<String> getNodesToSort(Collection<CatalogItem> items, ExecutionContext context);

    void sortNodes(Collection<String> paths, ExecutionContext context) throws Exception;

    void delete(ExecutionContext context) throws Exception;

    UpdateStrategy getStrategy(String pid);

    void startWorkflows(Collection<CatalogItem> items, ExecutionContext context) throws Exception;
}
