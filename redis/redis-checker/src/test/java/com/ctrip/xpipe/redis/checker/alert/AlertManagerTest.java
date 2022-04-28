package com.ctrip.xpipe.redis.checker.alert;

import com.ctrip.xpipe.redis.checker.PersistenceCache;
import com.ctrip.xpipe.utils.DateTimeUtils;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Date;

import static org.mockito.Mockito.when;

/**
 * @author chen.zhu
 * <p>
 * Dec 04, 2017
 */
public class AlertManagerTest {

    @Mock
    private AlertConfig alertConfig;

    @Mock
    private AlertDbConfig alertDbConfig;

    @Mock
    private PersistenceCache persistenceCache;

    @InjectMocks
    AlertManager alertManager = new AlertManager();

    @Before
    public void beforeAlertManagerTest() {
        MockitoAnnotations.initMocks(this);
        when(alertConfig.getNoAlarmMinutesForClusterUpdate()).thenReturn(15);
        when(alertConfig.getAlertWhileList()).thenReturn(Collections.emptySet());
        when(alertDbConfig.clusterAlertWhiteList()).thenReturn(Sets.newHashSet("cluster1"));
        when(alertConfig.getClusterExcludedRegex()).thenReturn("");
    }

    @Test
    public void testGenerateAlertMessage() {
        String cluster = null, shard = "", message = "test message";
        ALERT_TYPE type = ALERT_TYPE.ALERT_SYSTEM_OFF;
        String result = alertManager.generateAlertMessage(cluster, "jq", shard, type, message);
        String expected = "jq," + type.simpleDesc() + "," + message;
        Assert.assertEquals(expected, result);
    }

    @Test
    public void testNoAlertJustAfterCreate() {
        alertManager.refreshClusterExcluded();
        when(persistenceCache.getClusterCreateTime("just-created")).thenReturn(new Date(System.currentTimeMillis() - 60000));
        when(persistenceCache.getClusterCreateTime("created-long-ago")).thenReturn(DateTimeUtils.getHoursBeforeDate(new Date(), 1));

        Assert.assertFalse(alertManager.shouldAlert("just-created"));
        Assert.assertTrue(alertManager.shouldAlert("created-long-ago"));
    }

    @Test
    public void testShouldAlertNullCluster() {
        Assert.assertTrue(alertManager.shouldAlert(null));
    }

    @Test
    public void testAlertWhiteList() {
        when(alertConfig.getNoAlarmMinutesForClusterUpdate()).thenReturn(15);
        alertManager.refreshClusterExcluded();

        Assert.assertFalse(alertManager.shouldAlert("cluster1"));
        Assert.assertTrue(alertManager.shouldAlert("cluster2"));
    }

    @Test
    public void testClusterExcludedByRegex() {
        Assert.assertTrue(alertManager.shouldAlert("Test_cluster"));

        when(alertConfig.getClusterExcludedRegex()).thenReturn("test_.*");
        alertManager.refreshClusterExcluded();
        Assert.assertFalse(alertManager.shouldAlert("Test_cluster"));
        Assert.assertTrue(alertManager.shouldAlert("xpipe_test_cluster"));
    }

    @Test
    public void testClearExcludedRegex() {
        when(alertConfig.getClusterExcludedRegex()).thenReturn("test_.*");
        alertManager.refreshClusterExcluded();
        Assert.assertFalse(alertManager.shouldAlert("Test_cluster"));

        when(alertConfig.getClusterExcludedRegex()).thenReturn("");
        alertManager.refreshClusterExcluded();
        Assert.assertTrue(alertManager.shouldAlert("Test_cluster"));
    }

}