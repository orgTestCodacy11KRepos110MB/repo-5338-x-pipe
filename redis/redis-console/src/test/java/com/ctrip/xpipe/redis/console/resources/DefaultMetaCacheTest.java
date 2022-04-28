package com.ctrip.xpipe.redis.console.resources;

import com.ctrip.xpipe.cluster.ClusterType;
import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.redis.console.config.ConsoleConfig;
import com.ctrip.xpipe.redis.console.service.DcService;
import com.ctrip.xpipe.redis.console.service.impl.RedisCheckRuleServiceImpl;
import com.ctrip.xpipe.redis.console.service.meta.DcMetaService;
import com.ctrip.xpipe.redis.core.AbstractRedisTest;
import com.ctrip.xpipe.redis.core.entity.*;
import com.ctrip.xpipe.redis.core.meta.XpipeMetaManager;
import com.ctrip.xpipe.redis.core.util.SentinelUtil;
import com.ctrip.xpipe.tuple.Pair;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultMetaCacheTest extends AbstractRedisTest {

    @Mock
    private DcMetaService dcMetaService;

    @Mock
    private DcService dcService;

    @Mock
    private ConsoleConfig consoleConfig;

    @Mock
    private XpipeMetaManager xpipeMetaManager;

    @Mock
    private RedisCheckRuleServiceImpl redisCheckRuleService;

    @InjectMocks
    private DefaultMetaCache metaCache = new DefaultMetaCache();

    @Before
    public void beforeDefaultMetaCacheTest() {
        MockitoAnnotations.initMocks(this);
        metaCache.setMeta(Pair.of(getXpipeMeta(), xpipeMetaManager));
        metaCache.setMonitor2ClusterShard(Maps.newHashMap());
    }


    @Test
    public void getRouteIfPossible() {
        HostPort hostPort = new HostPort("127.0.0.1", 6379);
        XpipeMetaManager xpipeMetaManager = mock(XpipeMetaManager.class);
        when(xpipeMetaManager.findMetaDesc(hostPort)).thenReturn(null);
        metaCache.setMeta(new Pair<>(mock(XpipeMeta.class), xpipeMetaManager));
        metaCache.getRoutes();
    }

    @Test
    public void testIsCrossRegion() {
        Map<String, DcMeta> dcs = getXpipeMeta().getDcs();
        Assert.assertFalse(dcs.get("jq").getZone().equalsIgnoreCase(dcs.get("fra-aws").getZone()));
    }

    @Test
    public void testGetAllRedisCheckRules() {
        Map<Long, RedisCheckRuleMeta> redisCheckRules = getXpipeMeta().getRedisCheckRules();
        redisCheckRules.values().forEach(redisCheckRuleMeta -> {
            logger.info(redisCheckRuleMeta.getId() + ":" + redisCheckRuleMeta.getCheckType()
                    + ":" + redisCheckRuleMeta.getParam());
        });

        Assert.assertEquals(3, redisCheckRules.values().size());
    }


    @Test
    public void testGetAllActiveRedisOfDc() {
        List<HostPort> redises = metaCache.getAllActiveRedisOfDc("jq", "jq");
        Assert.assertEquals(4, redises.size());
        Assert.assertTrue(redises.contains(new HostPort("10.0.0.1", 6379)));
        Assert.assertTrue(redises.contains(new HostPort("127.0.0.1", 6379)));

        redises = metaCache.getAllActiveRedisOfDc("jq", "oy");
        Assert.assertEquals(2, redises.size());
        Assert.assertTrue(redises.contains(new HostPort("127.0.0.1", 8100)));
        Assert.assertTrue(redises.contains(new HostPort("127.0.0.1", 8101)));

        redises = metaCache.getAllActiveRedisOfDc("oy", "oy");
        Assert.assertEquals(4, redises.size());
        Assert.assertTrue(redises.contains(new HostPort("127.0.0.2", 8100)));
        Assert.assertTrue(redises.contains(new HostPort("127.0.0.2", 8101)));
        Assert.assertTrue(redises.contains(new HostPort("10.0.0.2", 6379)));
        Assert.assertTrue(redises.contains(new HostPort("10.0.0.2", 6479)));
    }

    @Test
    public void testGetAllKeepers() {
        Set<HostPort> allKeepers = metaCache.getAllKeepers();
        Assert.assertEquals(6, allKeepers.size());
        Assert.assertEquals(Sets.newHashSet(new HostPort("127.0.0.1", 6000),
                new HostPort("127.0.0.1", 6001),
                new HostPort("127.0.0.1", 6100),
                new HostPort("127.0.0.1", 6101),
                new HostPort("127.0.0.2", 6100),
                new HostPort("127.0.0.2", 6101)), allKeepers);
    }

    @Test
    public void testFindBiClusterShardBySentinelMonitor() {
        String monitorNameOY = SentinelUtil.getSentinelMonitorName("cluster3", "shard1", "oy");
        String monitorNameJQ = SentinelUtil.getSentinelMonitorName("cluster3", "shard1", "jq");
        Assert.assertEquals(Pair.of("cluster3", "shard1"), metaCache.findClusterShardBySentinelMonitor(monitorNameOY));
        Assert.assertEquals(Pair.of("cluster3", "shard1"), metaCache.findClusterShardBySentinelMonitor(monitorNameJQ));
    }

    @Test
    public void testFindOneWayClusterShardBySentinelMonitor() {
        String monitorNameOY = SentinelUtil.getSentinelMonitorName("cluster1", "shard1", "oy");
        String monitorNameJQ = SentinelUtil.getSentinelMonitorName("cluster1", "shard1", "jq");
        Assert.assertNull(metaCache.findClusterShardBySentinelMonitor(monitorNameOY));
        Assert.assertEquals(Pair.of("cluster1", "shard1"), metaCache.findClusterShardBySentinelMonitor(monitorNameJQ));
    }

    @Test
    public void getMaxMasterCountDcTest() throws Exception {
        when(consoleConfig.getConsoleAddress()).thenReturn("");
        when(consoleConfig.getClustersPartIndex()).thenReturn(1);
        XpipeMeta xpipeMeta = new XpipeMeta();

//          single dc trocks
        ClusterMeta oyCluster = new ClusterMeta().setType(ClusterType.CROSS_DC.name()).setId("cluster").addShard(
                new ShardMeta().setId("shard1").addRedis(new RedisMeta().setMaster("")).addRedis(new RedisMeta().setMaster("127.0.0.1")));

        DcMeta oyDcMeta = new DcMeta("oy");
        oyDcMeta.addCluster(oyCluster);
        xpipeMeta.addDc(oyDcMeta);
        metaCache.setActiveDcForCrossDcClusters(xpipeMeta);

        Assert.assertEquals("oy", oyCluster.getActiveDc());

//        multi dc trocks，single shard，oy master
        ClusterMeta jqCluster = new ClusterMeta().setType(ClusterType.CROSS_DC.name()).setId("cluster").addShard(
                new ShardMeta().setId("shard1").addRedis(new RedisMeta().setMaster("127.0.0.1")).addRedis(new RedisMeta().setMaster("127.0.0.1")));

        DcMeta jqDcMeta = new DcMeta("jq");
        jqDcMeta.addCluster(jqCluster);
        xpipeMeta.addDc(jqDcMeta);
        metaCache.setActiveDcForCrossDcClusters(xpipeMeta);

        Assert.assertEquals("oy", oyCluster.getActiveDc());
        Assert.assertEquals("oy", jqCluster.getActiveDc());

//        multi dc trocks，multi shards，oy master
        oyCluster.addShard(new ShardMeta().setId("shard2").addRedis(new RedisMeta().setMaster("")).addRedis(new RedisMeta().setMaster("127.0.0.1")));
        jqCluster.addShard(new ShardMeta().setId("shard2").addRedis(new RedisMeta().setMaster("127.0.0.1")).addRedis(new RedisMeta().setMaster("127.0.0.1")));
        metaCache.setActiveDcForCrossDcClusters(xpipeMeta);

        Assert.assertEquals("oy", oyCluster.getActiveDc());
        Assert.assertEquals("oy", jqCluster.getActiveDc());

//        multi dc trocks，multi shards，one oy master，one jq master
        oyCluster.removeShard("shard2");
        jqCluster.removeShard("shard2");
        oyCluster.addShard(new ShardMeta().setId("shard2").addRedis(new RedisMeta().setMaster("127.0.0.1")).addRedis(new RedisMeta().setMaster("127.0.0.1")));
        jqCluster.addShard(new ShardMeta().setId("shard2").addRedis(new RedisMeta().setMaster("")).addRedis(new RedisMeta().setMaster("127.0.0.1")));
        metaCache.setActiveDcForCrossDcClusters(xpipeMeta);

        Assert.assertEquals("jq", oyCluster.getActiveDc());
        Assert.assertEquals("jq", jqCluster.getActiveDc());

//        multidc trocks，multi shards，one oy master，two jq masters
        oyCluster.addShard(new ShardMeta().setId("shard3").addRedis(new RedisMeta().setMaster("127.0.0.1")).addRedis(new RedisMeta().setMaster("127.0.0.1")));
        jqCluster.addShard(new ShardMeta().setId("shard3").addRedis(new RedisMeta().setMaster("")).addRedis(new RedisMeta().setMaster("127.0.0.1")));
        metaCache.setActiveDcForCrossDcClusters(xpipeMeta);
        Assert.assertEquals("jq", oyCluster.getActiveDc());
        Assert.assertEquals("jq", jqCluster.getActiveDc());
    }

    protected String getXpipeMetaConfigFile() {
        return "dc-meta-test.xml";
    }
}