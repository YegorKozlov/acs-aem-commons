package com.adobe.acs.commons.contentsync.io;

import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

public class TestShardGenerator {
    @Test
    public void testSharding(){
        assertShard(3, "3");
        assertShard(333, "33/3");
        assertShard(3333, "33/33");
        assertShard(33333, "33/33/3");
    }

    void assertShard(int n, String expected){
        ShardGenerator generator = new ShardGenerator("/var/test");
        for(int i = 0; i < n; i++){
            generator.nextShard();
        }
        assertEquals(expected, generator.getShard());
    }
}
