package com.adobe.acs.commons.contentsync;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ContentSyncService {
    String SERVICE_NAME = "content-sync-writer";
    String JOB_RESULTS_BASE_PATH = "/var/acs-commons/contentsync/jobs";
    Map<String, Object> AUTH_INFO = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, SERVICE_NAME);


    List<CatalogItem> getRemoteItems(ExecutionContext context) throws Exception;

    List<CatalogItem> getItemsToSync(ExecutionContext context) throws Exception;

    void syncItem(CatalogItem item, ExecutionContext context) throws Exception;

    RemoteInstance createRemoteInstance(Job job) throws Exception;

    Collection<String> getNodesToSort(Collection<CatalogItem> items, ExecutionContext context);

    void sortNodes(Collection<String> paths, ExecutionContext context) throws Exception;

    void deleteUnknownResources(ExecutionContext context) throws Exception;

    UpdateStrategy getStrategy(String pid);

    void startWorkflows(Collection<CatalogItem> items, ExecutionContext context) throws Exception;

    ResourceResolverFactory getResourceResolverFactory() throws LoginException;
}
