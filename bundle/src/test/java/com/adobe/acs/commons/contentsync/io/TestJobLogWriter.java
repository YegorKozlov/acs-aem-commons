package com.adobe.acs.commons.contentsync.io;

import io.wcm.testing.mock.aem.junit.AemContext;
import org.apache.sling.api.resource.AbstractResourceVisitor;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.wrappers.ResourceResolverWrapper;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.adobe.acs.commons.contentsync.io.JobLogWriter.DATA_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TestJobLogWriter {
    @Rule
    public AemContext context = new AemContext(ResourceResolverType.RESOURCERESOLVER_MOCK);

    private ResourceResolverFactory resourceResolverFactory;

    @Before
    public void setUp() throws Exception {
        resourceResolverFactory = mock(ResourceResolverFactory.class);
        when(resourceResolverFactory.getServiceResourceResolver(anyMap())).thenReturn(context.resourceResolver());
    }

    @Test
    public void testOne() throws IOException {
        String jobPath = "/var/acs-commons/contentsync/jobs/test";
        int bucketSize = 2;
        int shardWidth = 1;
        JobLogWriter writer = new JobLogWriter(resourceResolverFactory, jobPath, bucketSize, -1, shardWidth);
        int N = 21;
        List<String> writtenMessages = new ArrayList<>();
        for(int i = 0; i < N; i++){
            String msg = "hello-" + i;
            writtenMessages.add(msg);
            writer.write(msg);
        }
        writer.close();

        Resource logNode = context.resourceResolver().getResource(jobPath);
        List<Resource> shards = new ArrayList<>();
        new AbstractResourceVisitor(){
            public void visit(Resource res){
                if(res.getValueMap().containsKey(DATA_PROPERTY)) {
                    shards.add(res);
                }
            }
        }.accept(logNode);
        // 21 messages are distributed in 11 buckets, 10x2 messages and 1 bucked with 1 message
        assertEquals(11, shards.size());

        JobLogIterator it = new JobLogIterator(logNode, shardWidth);
        List<String> readMessages = new ArrayList<>();
        while(it.hasNext()){
            readMessages.addAll(Arrays.asList(it.next()));
        }

        // assert read and write operations match
        assertEquals(writtenMessages, readMessages);
    }

    @Test
    public void testStructure() throws IOException {
        String jobPath = "/var/acs-commons/contentsync/jobs/test";
        int bucketSize = 2;
        int shardWidth = 1;
        JobLogWriter writer = new JobLogWriter(resourceResolverFactory, jobPath, bucketSize, -1, shardWidth);
        int N = 221;
        List<String> writtenMessages = new ArrayList<>();
        for(int i = 0; i < N; i++){
            String msg = "hello-" + i;
            writtenMessages.add(msg);
            writer.write(msg);
        }
        writer.close();

        Resource logNode = context.resourceResolver().getResource(jobPath);
        new AbstractResourceVisitor(){
            public void visit(Resource res){
                if(res.getValueMap().containsKey(DATA_PROPERTY)) {
                    System.out.println(res.getPath());
                    System.out.println(res.getResourceType());
                }
            }
        }.accept(logNode);
    }
}