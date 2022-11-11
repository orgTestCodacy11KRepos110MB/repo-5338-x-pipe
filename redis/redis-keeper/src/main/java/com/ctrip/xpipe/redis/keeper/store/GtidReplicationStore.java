package com.ctrip.xpipe.redis.keeper.store;

import com.ctrip.xpipe.exception.XpipeRuntimeException;
import com.ctrip.xpipe.gtid.GtidSet;
import com.ctrip.xpipe.redis.core.protocal.protocal.EofType;
import com.ctrip.xpipe.redis.core.redis.operation.RedisOpParser;
import com.ctrip.xpipe.redis.core.store.*;
import com.ctrip.xpipe.redis.keeper.Gtid2OffsetIndexGenerator;
import com.ctrip.xpipe.redis.keeper.config.KeeperConfig;
import com.ctrip.xpipe.redis.keeper.monitor.KeeperMonitor;
import com.ctrip.xpipe.redis.keeper.store.cmd.GtidSetCommandReaderWriterFactory;
import com.ctrip.xpipe.utils.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * @author lishanglin
 * date 2022/5/24
 */
public class GtidReplicationStore extends DefaultReplicationStore {

    private static final Logger logger = LoggerFactory.getLogger(GtidReplicationStore.class);

    private static final int DEFAULT_BYTES_BETWEEN_INDEX = 50 * 1024 * 1024; // 50MB

    private Gtid2OffsetIndexGenerator indexGenerator;

    public GtidReplicationStore(File baseDir, KeeperConfig config, String keeperRunid,
                                KeeperMonitor keeperMonitor, RedisOpParser redisOpParser) throws IOException {
        super(baseDir, config, keeperRunid,
                new GtidSetCommandReaderWriterFactory(redisOpParser, config.getCommandIndexBytesInterval()),
                keeperMonitor);
    }

    @Override
    protected CommandStore createCommandStore(File baseDir, ReplicationStoreMeta replMeta, int cmdFileSize,
                                              KeeperConfig config, CommandReaderWriterFactory cmdReaderWriterFactory,
                                              KeeperMonitor keeperMonitor, RdbStore rdbStore) throws IOException {

        String replRdbGtidSet = replMeta.getRdbGtidSet();
        logger.info("[createCommandStore], replRdbGtidSet={}", replRdbGtidSet);
        GtidCommandStore cmdStore = new GtidCommandStore(new File(baseDir, replMeta.getCmdFilePrefix()), cmdFileSize,
                new GtidSet(replRdbGtidSet), config::getReplicationStoreCommandFileKeepTimeSeconds,
                config.getReplicationStoreMinTimeMilliToGcAfterCreate(), config::getReplicationStoreCommandFileNumToKeep,
                config.getCommandReaderFlyingThreshold(), cmdReaderWriterFactory, keeperMonitor);

        if (null != replRdbGtidSet) {
            try {
                cmdStore.initialize();
            } catch (Exception e) {
                logger.info("[createCommandStore] init fail", e);
                cmdStore.close();
                throw new XpipeRuntimeException("cmdStore init fail", e);
            }
        } else if (null != rdbStore) {
            logger.info("[createCommandStore] init after rdb gtid set ready");
            rdbStore.addListener(new RdbStoreListener() {
                @Override
                public void onRdbGtidSet(String gtidSet) {
                    try {
                        getLogger().info("[onRdbGtidSet][update cmdstore] {} {}", rdbStore.getRdbFileName(), gtidSet);
                        cmdStore.setBaseGtidSet(gtidSet);
                        cmdStore.initialize();
                    } catch (Exception e) {
                        getLogger().error("[onRdbGtidSet][update cmdstore]", e);
                        try {
                            cmdStore.close();
                        } catch (Throwable th) {
                            getLogger().error("[onRdbGtidSet] fail and close fail", th);
                        }
                    }
                }

                @Override
                public void onEndRdb() {
                }
            });
        } else {
            throw new IllegalStateException("no rdb.gtidset for GtidCommandStore to init");
        }

        return cmdStore;
    }

    @Override
    protected RdbStore createRdbStore(File rdb, long rdbOffset, EofType eofType) throws IOException {
        ReplicationStoreMeta meta = getMetaStore().dupReplicationStoreMeta();
        if (meta.getRdbFile().equals(rdb.getName())) {
            return new GtidRdbStore(rdb, rdbOffset, eofType, meta.getRdbGtidSet());
        } else {
            return new GtidRdbStore(rdb, rdbOffset, eofType, null);
        }
    }

    @Override
    protected RdbStoreListener createRdbStoreListener(RdbStore rdbStore) {
        return new GtidReplicationStoreRdbFileListener(rdbStore);
    }

    @Override
    public DumpedRdbStore prepareNewRdb() throws IOException {
        makeSureOpen();
        return new DumpedGtidRdbStore(new File(getBaseDir(), newRdbFileName()));
    }

    @Override
    public FULLSYNC_FAIL_CAUSE fullSyncIfPossible(FullSyncListener fullSyncListener) throws IOException {
        makeSureOpen();

        if (!fullSyncListener.supportProgress(GtidSetReplicationProgress.class)) {
            return super.fullSyncIfPossible(fullSyncListener);
        }

        FullSyncContext ctx = lockAndCheckIfFullSyncPossible();
        if (ctx.isFullSyncPossible() && StringUtil.isEmpty(((GtidRdbStore)ctx.getRdbStore()).getGtidSet())) {
            return FULLSYNC_FAIL_CAUSE.RDB_GTIDSET_NOT_READY;
        }

        if (ctx.isFullSyncPossible()) {
            return tryDoFullSync(ctx, fullSyncListener);
        } else {
            return ctx.getCause();
        }
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    public class GtidReplicationStoreRdbFileListener extends ReplicationStoreRdbFileListener implements RdbStoreListener {

        public GtidReplicationStoreRdbFileListener(RdbStore rdbStore) {
            super(rdbStore);
        }

        @Override
        public void onRdbGtidSet(String gtidSet) {
            try {
                getLogger().info("[onRdbGtidSet][update metastore] {} {}", rdbStore.getRdbFileName(), gtidSet);
                getMetaStore().attachRdbGtidSet(rdbStore.getRdbFileName(), gtidSet);
            } catch (IOException e) {
                getLogger().error("[onRdbGtidSet][update metastore]", e);
            }
        }

    }

    @Override
    public FULLSYNC_FAIL_CAUSE createIndexIfPossible() throws IOException {

        FullSyncContext ctx = lockAndCheckIfFullSyncPossible();
        if (ctx.isFullSyncPossible() && StringUtil.isEmpty(ctx.getRdbStore().getGtidSet())) {
            return FULLSYNC_FAIL_CAUSE.RDB_GTIDSET_NOT_READY;
        }

        if (ctx.isFullSyncPossible()) {
            return tryCreateIndex(ctx);
        } else {
            return ctx.getCause();
        }
    }


    protected FULLSYNC_FAIL_CAUSE tryCreateIndex(FullSyncContext ctx) throws IOException {
        //TODO 1: how about gc() invoked at the same time ?
        //TODO 2: find latest index

        String rdbGtidSetString = ctx.getRdbStore().getGtidSet();

        indexGenerator = new Gtid2OffsetIndexGenerator(cmdStore, DEFAULT_BYTES_BETWEEN_INDEX, new GtidSet(rdbGtidSetString));

        //TODO 3: deal with thread leak
        addCommandsListener(new GtidSetReplicationProgress(new GtidSet(rdbGtidSetString)), indexGenerator);
        return null;
    }

    @Override
    public GtidSet getBeginGtidSet() throws IOException {
        return cmdStore.getBeginGtidSet();
    }

    @Override
    public GtidSet getEndGtidSet() {
        return indexGenerator.getEndGtidSet();
    }
}
