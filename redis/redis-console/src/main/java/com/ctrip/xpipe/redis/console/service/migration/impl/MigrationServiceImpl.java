package com.ctrip.xpipe.redis.console.service.migration.impl;

import com.ctrip.xpipe.cluster.ClusterType;
import com.ctrip.xpipe.endpoint.HostPort;
import com.ctrip.xpipe.redis.checker.alert.ALERT_TYPE;
import com.ctrip.xpipe.redis.checker.alert.AlertManager;
import com.ctrip.xpipe.redis.console.controller.api.RetMessage;
import com.ctrip.xpipe.redis.console.controller.api.migrate.meta.MigrationProgress;
import com.ctrip.xpipe.redis.console.dao.MigrationClusterDao;
import com.ctrip.xpipe.redis.console.dao.MigrationEventDao;
import com.ctrip.xpipe.redis.console.exception.BadRequestException;
import com.ctrip.xpipe.redis.console.healthcheck.nonredis.migration.MigrationSystemAvailableChecker;
import com.ctrip.xpipe.redis.console.migration.manager.MigrationEventManager;
import com.ctrip.xpipe.redis.console.migration.model.MigrationCluster;
import com.ctrip.xpipe.redis.console.migration.model.MigrationEvent;
import com.ctrip.xpipe.redis.console.migration.status.MigrationStatus;
import com.ctrip.xpipe.redis.console.model.*;
import com.ctrip.xpipe.redis.console.query.DalQuery;
import com.ctrip.xpipe.redis.console.service.*;
import com.ctrip.xpipe.redis.console.service.migration.MigrationService;
import com.ctrip.xpipe.redis.console.service.migration.exception.*;
import com.ctrip.xpipe.redis.core.meta.MetaCache;
import com.ctrip.xpipe.utils.DateTimeUtils;
import com.ctrip.xpipe.utils.StringUtil;
import com.ctrip.xpipe.utils.VisibleForTesting;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unidal.dal.jdbc.DalException;
import org.unidal.lookup.ContainerLoader;

import javax.annotation.PostConstruct;
import java.rmi.ServerException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class MigrationServiceImpl extends AbstractConsoleService<MigrationEventTblDao> implements MigrationService {

    @Autowired
    private MigrationEventDao migrationEventDao;

    @Autowired
    private MigrationEventManager migrationEventManager;

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private DcClusterService dcClusterService;

    @Autowired
    private AlertManager alertManager;

    @Autowired
    private DcService dcService;

    @Autowired
    private MigrationClusterDao migrationClusterDao;

    @Autowired
    private MigrationSystemAvailableChecker checker;

    @Autowired
    private ConfigService configService;

    @Autowired
    private MetaCache metaCache;

    private MigrationShardTblDao migrationShardTblDao;

    @PostConstruct
    private void postConstruct() throws ServerException {
        try {
            migrationShardTblDao = ContainerLoader.getDefaultContainer().lookup(MigrationShardTblDao.class);
        } catch (ComponentLookupException e) {
            throw new ServerException("Cannot construct dao.");
        }
    }

    @Override
    public MigrationEventTbl find(final long id) {
        return queryHandler.handleQuery(new DalQuery<MigrationEventTbl>() {
            @Override
            public MigrationEventTbl doQuery() throws DalException {
                return dao.findByPK(id, MigrationEventTblEntity.READSET_FULL);
            }
        });
    }

    @Override
    public long countAll() {
        return queryHandler.handleQuery(new DalQuery<Long>() {
            @Override
            public Long doQuery() throws DalException {
                return dao.countAll(MigrationEventTblEntity.READSET_COUNT).getCount();
            }
        });
    }

    @Override
    public long countAllByCluster(long clusterId) {
        return migrationClusterDao.countAllByCluster(clusterId);
    }

    @Override
    public List<MigrationModel> find(long size, long offset) {
        List<MigrationClusterTbl> migrationClusterList = migrationClusterDao.find(size, offset);
        return aggregateClusterByMigration(migrationClusterList);
    }
    @Override
    public List<MigrationModel> findByCluster(long clusterId, long size, long offset) {
        List<MigrationClusterTbl> migrationClusterList =
                migrationClusterDao.findByCluster(clusterId, size, offset);
        return aggregateClusterByMigration(migrationClusterList);
    }

    private List<MigrationModel> aggregateClusterByMigration(List<MigrationClusterTbl> migrationClusterTblList) {
        Map<Long, List<MigrationClusterTbl> > clusterMap = new LinkedHashMap<>();

        for (MigrationClusterTbl migrationCluster: migrationClusterTblList) {
            MigrationEventTbl event = migrationCluster.getMigrationEvent();

            if (!clusterMap.containsKey(event.getId())) {
                clusterMap.put(event.getId(), new LinkedList<>());
            }

            clusterMap.get(event.getId()).add(migrationCluster);
        }

        Iterator<Map.Entry<Long, List<MigrationClusterTbl> > > iterator = clusterMap.entrySet().iterator();
        List<MigrationModel> modals = new LinkedList<>();

        while (iterator.hasNext()) {
            List<MigrationClusterTbl> clusters = iterator.next().getValue();
            modals.add(MigrationModel.createFromMigrationClusters(clusters));
        }

        return modals;
    }

    @Override
    public List<MigrationEventTbl> findAll() {
        return queryHandler.handleQuery(new DalQuery<List<MigrationEventTbl>>() {
            @Override
            public List<MigrationEventTbl> doQuery() throws DalException {
                return dao.findAll(MigrationEventTblEntity.READSET_FULL);
            }
        });
    }

    @Override
    public MigrationClusterTbl findMigrationCluster(final long eventId, final long clusterId) {

        return migrationClusterDao.findByEventIdAndClusterId(eventId, clusterId);
    }

    @Override
    public void updateMigrationClusterStartTime(long migrationClusterId, Date startTime) {
        migrationClusterDao.updateStartTime(migrationClusterId, startTime);
    }

    @Override
    public void updateStatusAndEndTimeById(long migrationClusterId, MigrationStatus status, Date endTime) {
        migrationClusterDao.updateStatusAndEndTimeById(migrationClusterId, status, endTime);
    }

    @Override
    public void updatePublishInfoById(long migrationClusterId, String publishInfo) {
        migrationClusterDao.updatePublishInfoById(migrationClusterId, publishInfo);
    }

    @Override
    public MigrationClusterTbl findLatestUnfinishedMigrationCluster(final long clusterId) {

        List<MigrationClusterTbl> unfinishedByClusterId = migrationClusterDao.findUnfinishedByClusterId(clusterId);

        if (unfinishedByClusterId.size() == 0) {
            return null;
        }

        if (unfinishedByClusterId.size() > 1) {
            alertManager.alert(String.valueOf(clusterId), null, null, ALERT_TYPE.MIGRATION_MANY_UNFINISHED, String.format("[count]%d", unfinishedByClusterId.size()));
        }
        return unfinishedByClusterId.get(unfinishedByClusterId.size() - 1);
    }

    @Override
    public List<MigrationShardTbl> findMigrationShards(final long migrationClusterId) {
        return queryHandler.handleQuery(new DalQuery<List<MigrationShardTbl>>() {
            @Override
            public List<MigrationShardTbl> doQuery() throws DalException {
                return migrationShardTblDao.findByMigrationClusterId(migrationClusterId, MigrationShardTblEntity.READSET_FULL);
            }
        });
    }

    @Override
    public List<MigrationClusterModel> getMigrationClusterModel(long eventId) {
        return migrationEventDao.getMigrationCluster(eventId);
    }

    @Override
    public Long createMigrationEvent(MigrationRequest request) {
        preCheck(request);
        MigrationEvent event = migrationEventDao.createMigrationEvent(request);
        migrationEventManager.addEvent(event);
        return event.getEvent().getId();
    }

    private void preCheck(MigrationRequest request) {
        List<MigrationRequest.ClusterInfo> clusterinfos = request.getRequestClusters();
        for(MigrationRequest.ClusterInfo clusterInfo : clusterinfos) {
            if(clusterInfo.getToDcId() < 0 || !isDestDcIdValid(clusterInfo.getClusterId(), clusterInfo.getToDcId())) {
                throw new BadRequestException("Target DC Id Illegal for cluster: " + clusterInfo.getClusterId());
            }

            ClusterType clusterType;
            if (StringUtil.isEmpty(clusterInfo.getClusterName())) {
                ClusterTbl clusterTbl = clusterService.find(clusterInfo.getClusterId());
                clusterType = ClusterType.lookup(clusterTbl.getClusterType());
            } else {
                clusterType = metaCache.getClusterType(clusterInfo.getClusterName());
            }

            if (null == clusterType || !clusterType.supportMigration())
                throw new BadRequestException(String.format("cluster %s type %s not support migration", clusterInfo.getClusterName(), clusterType));
        }
    }

    private boolean isDestDcIdValid(long clusterId, long dcId) {
        try {
            DcClusterTbl dcClusterTbl = dcClusterService.find(dcId, clusterId);
            if(dcClusterTbl == null) {
                return false;
            }
        } catch (Exception e) {
            logger.error("[isDestDcIdValid]", e);
            return false;
        }
        return true;
    }

    @Override
    public void updateMigrationShardLogById(final long id, final String log) {

        MigrationShardTbl migrationShardTbl = new MigrationShardTbl();
        migrationShardTbl.setId(id);
        migrationShardTbl.setLog(log);

        queryHandler.handleUpdate(new DalQuery<Integer>() {
            @Override
            public Integer doQuery() throws DalException {
                return migrationShardTblDao.updateLogById(migrationShardTbl, MigrationShardTblEntity.UPDATESET_FULL);
            }
        });
    }

    @Override
    public void continueMigrationCluster(final long eventId, final long clusterId) {

        if (isMigrationClusterExist(eventId, clusterId)) {
            try {
                migrationEventManager.getEvent(eventId).processCluster(clusterId);
            } catch (Exception e) {
                logger.info("[continueMigrationCluster][{}][{}] fail", eventId, clusterId, e);
            }
        } else {
            throw new IllegalArgumentException(String.format("event %d, cluster:%d not found", eventId, clusterId));
        }
    }

    @Override
    public void continueMigrationEvent(long id) throws Exception {

        logger.info("[continueMigrationEvent]{}", id);
        MigrationEvent event = getEvent(id);
        if (event == null) {
            throw new IllegalArgumentException("event not found:" + id);
        }
        event.process();
    }

    @Override
    public MigrationEvent getMigrationEvent(long eventId) {
        return getEvent(eventId);
    }


    @Override
    public MigrationCluster rollbackMigrationCluster(long eventId, long clusterId) throws ClusterNotFoundException {

        MigrationEvent event = getEvent(eventId);
        if (event == null) {
            throw new IllegalArgumentException("event not found:" + eventId);
        }
        return event.rollbackCluster(clusterId);
    }

    @Override
    public MigrationCluster rollbackMigrationCluster(long eventId, String clusterName) throws ClusterNotFoundException {

        MigrationEvent event = getEvent(eventId);
        if (event == null) {
            throw new IllegalArgumentException("event not found:" + eventId);
        }

        return event.rollbackCluster(clusterName);
    }


    @Override
    public void cancelMigrationCluster(long eventId, long clusterId) {
        if (isMigrationClusterExist(eventId, clusterId)) {
            try {
                migrationEventManager.getEvent(eventId).cancelCluster(clusterId);
            } catch (Exception e) {
                logger.info("[cancelMigrationCluster][{}][{}] fail", eventId, clusterId, e);
            }
        }
    }


    private MigrationEvent getEvent(long eventId) {
        return migrationEventManager.getEvent(eventId);
    }

    @Override
    public void forceProcessMigrationCluster(long eventId, long clusterId) {
        if (isMigrationClusterExist(eventId, clusterId)) {
            try {
                migrationEventManager.getEvent(eventId).forceClusterProcess(clusterId);
            } catch (Exception e) {
                logger.info("[forceProcessMigrationCluster][{}][{}] fail", eventId, clusterId, e);
            }
        }
    }

    @Override
    public void forceEndMigrationCluster(long eventId, long clusterId) {
        if (isMigrationClusterExist(eventId, clusterId)) {
            try {
                migrationEventManager.getEvent(eventId).forceClusterEnd(clusterId);
            } catch (Exception e) {
                logger.info("[forceEndMigrationClsuter][{}][{}] fail", eventId, clusterId, e);
            }
        }
    }

    @Override
    public MigrationSystemAvailableChecker.MigrationSystemAvailability getMigrationSystemAvailability() {
        MigrationSystemAvailableChecker.MigrationSystemAvailability availability =  checker.getResult();
        if(availability.isAvaiable() && !availability.isWarning()) {
            if (System.currentTimeMillis() - availability.getTimestamp() >= TimeUnit.MINUTES.toMillis(2)) {
                String message = "not updated over 2 min\n";
                availability.addWarningMessage(message);
                alertManager.alert("", "", new HostPort(), ALERT_TYPE.MIGRATION_SYSTEM_CHECK_OVER_DUE, message);
            }
        }
        return availability;
    }

    private boolean isMigrationClusterExist(long eventId, long clusterId) {
        boolean ret = false;
        if (null != migrationEventManager.getEvent(eventId)) {
            if (null != migrationEventManager.getEvent(eventId).getMigrationCluster(clusterId)) {
                ret = true;
            }
        }
        return ret;
    }

    @Override
    public TryMigrateResult tryMigrate(String clusterName, String fromIdc, String toIdc)
            throws ClusterNotFoundException, MigrationNotSupportException, ClusterActiveDcNotRequest, ClusterMigratingNow, ToIdcNotFoundException, MigrationSystemNotHealthyException {

        if(!checker.getResult().isAvaiable() && !configService.ignoreMigrationSystemAvailability()) {
            throw new MigrationSystemNotHealthyException(checker.getResult().getMessage());
        }
        ClusterTbl clusterTbl = clusterService.find(clusterName);
        if (clusterTbl == null) {
            throw new ClusterNotFoundException(clusterName);
        }
        if (!ClusterType.lookup(clusterTbl.getClusterType()).supportMigration()) {
            throw new MigrationNotSupportException(clusterName);
        }

        MigrationClusterTbl unfinished = findLatestUnfinishedMigrationCluster(clusterTbl.getId());
        if (unfinished != null) {
            long fromDcId = unfinished.getSourceDcId();
            long toDcId = unfinished.getDestinationDcId();
            throw new ClusterMigratingNow(clusterName, dcService.getDcName(fromDcId), dcService.getDcName(toDcId), unfinished.getMigrationEventId());
        }

        long activedcId = clusterTbl.getActivedcId();
        DcTbl activeDc = dcService.find(activedcId);
        if (fromIdc != null && !fromIdc.equalsIgnoreCase(activeDc.getDcName())) {
            throw new ClusterActiveDcNotRequest(clusterName, fromIdc, activeDc.getDcName());
        }

        List<DcTbl> clusterRelatedDc = dcService.findClusterRelatedDc(clusterName);
        logger.debug("[tryMigrate][clusterRelatedDc]", clusterRelatedDc);

        DcTbl toDc = findToDc(fromIdc, toIdc, clusterRelatedDc);
        return new TryMigrateResult(clusterTbl, activeDc, toDc);
    }

    @Override
    public RetMessage getMigrationSystemHealth() {
        MigrationSystemAvailableChecker.MigrationSystemAvailability availability = this.getMigrationSystemAvailability();
        if(availability.isAvaiable()) {
            if(!availability.isWarning()) {
                logger.debug("[getMigrationSystemHealthStatus][good]");
                return RetMessage.createSuccessMessage();
            } else {
                logger.debug("[getMigrationSystemHealthStatus][warned]");
                return RetMessage.createWarningMessage(availability.getMessage());
            }
        }
        if(configService.ignoreMigrationSystemAvailability()) {
            logger.warn("[getMigrationSystemHealthStatus][warn]{}", availability.getMessage());
            return RetMessage.createWarningMessage(availability.getMessage());
        } else {
            logger.error("[getMigrationSystemHealthStatus][warn]{}", availability.getMessage());
            return RetMessage.createFailMessage(availability.getMessage());
        }
    }

    @Override
    public MigrationProgress buildMigrationProgress(int hours) {
        MigrationProgress progress = new MigrationProgress();
        long totalMigrationSeconds = 0;

        List<MigrationClusterTbl> migrationClusterTbls = migrationClusterDao
                .findLatestMigrationClusters(DateTimeUtils.getHoursBeforeDate(new Date(), hours));

        for (MigrationClusterTbl migrationClusterTbl : migrationClusterTbls) {
            progress.addMigrationCluster(migrationClusterTbl);
            MigrationStatus status = MigrationStatus.valueOf(migrationClusterTbl.getStatus());
            if (MigrationStatus.Success.equals(status)) {
                if (null == migrationClusterTbl.getEndTime() || null == migrationClusterTbl.getStartTime()) {
                    logger.info("[buildMigrationProgress][{}] no time but success", migrationClusterTbl.getId());
                }
                long durationMilli = migrationClusterTbl.getEndTime().getTime() - migrationClusterTbl.getStartTime().getTime();
                totalMigrationSeconds += TimeUnit.MILLISECONDS.toSeconds(durationMilli);
            }
        }
        if (progress.getSuccess() > 0) progress.setAvgMigrationSeconds(totalMigrationSeconds / progress.getSuccess());
        progress.setActiveDcs(clusterService.getAllCountByActiveDc());

        return progress;
    }

    protected DcTbl findToDc(String fromIdc, String toIdc, List<DcTbl> clusterRelatedDc) throws ToIdcNotFoundException {

        DcTbl fromIdcInfo = null;
        for(DcTbl dcTbl : clusterRelatedDc) {
            if(dcTbl.getDcName().equals(fromIdc)) {
                fromIdcInfo = dcTbl;
                break;
            }
        }
        if(StringUtil.isEmpty(toIdc)){
            //simple
            for (DcTbl dcTbl : clusterRelatedDc) {
                if (!dcTbl.getDcName().equalsIgnoreCase(fromIdc) && isSameZone(fromIdcInfo, dcTbl)) {
                    return dcTbl;
                }
            }
            throw new ToIdcNotFoundException(String.format("fromIdc:%s, toIdc empty, can not find target dc %s", fromIdc, clusterRelatedDcToString(clusterRelatedDc)));
        }else {

            if(toIdc.equalsIgnoreCase(fromIdc)){
                throw new ToIdcNotFoundException(String.format("fromIdc:%s equals with toIdc %s", fromIdc, toIdc));
            }

            for (DcTbl dcTbl : clusterRelatedDc) {
                if (dcTbl.getDcName().equalsIgnoreCase(toIdc)) {
                    if(!isSameZone(fromIdcInfo, dcTbl)) {
                        throw new ToIdcNotFoundException("To Idc should be in same zone with from Idc");
                    }
                    return dcTbl;
                }
            }
            throw new ToIdcNotFoundException(String.format("toIdc : %s, can not find it in all related dcs:%s", toIdc, clusterRelatedDcToString(clusterRelatedDc)));
        }
    }

    protected boolean isSameZone(DcTbl fromIdc, DcTbl toIdc) {
        try {
            return fromIdc.getZoneId() == toIdc.getZoneId();
        } catch (Exception e) {
            return false;
        }
    }

    private String clusterRelatedDcToString(List<DcTbl> clusterRelatedDc) {
        return StringUtil.join(",", (dcTbl) -> dcTbl.getDcName() , clusterRelatedDc);
    }

    @VisibleForTesting
    protected MigrationServiceImpl setAlertManager(AlertManager alertManager) {
        this.alertManager = alertManager;
        return this;
    }

    @VisibleForTesting
    public MigrationServiceImpl setChecker(MigrationSystemAvailableChecker checker) {
        this.checker = checker;
        return this;
    }

    @VisibleForTesting
    public MigrationServiceImpl setMigrationEventManager(MigrationEventManager migrationEventManager) {
        this.migrationEventManager = migrationEventManager;
        return this;
    }

    @VisibleForTesting
    public MigrationEventManager getMigrationEventManager() {
        return migrationEventManager;
    }

    @VisibleForTesting
    public MigrationServiceImpl setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
        return this;
    }

    @VisibleForTesting
    public MigrationServiceImpl setDcClusterService(DcClusterService dcClusterService) {
        this.dcClusterService = dcClusterService;
        return this;
    }

    @VisibleForTesting
    public MigrationServiceImpl setDcService(DcService dcService) {
        this.dcService = dcService;
        return this;
    }

    @VisibleForTesting
    public MigrationServiceImpl setMigrationClusterDao(MigrationClusterDao migrationClusterDao) {
        this.migrationClusterDao = migrationClusterDao;
        return this;
    }

    @VisibleForTesting
    public MigrationServiceImpl setConfigService(ConfigService configService) {
        this.configService = configService;
        return this;
    }

    @VisibleForTesting
    public MigrationServiceImpl setMigrationShardTblDao(MigrationShardTblDao migrationShardTblDao) {
        this.migrationShardTblDao = migrationShardTblDao;
        return this;
    }
}
