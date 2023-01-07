package com.ctrip.xpipe.redis.keeper.applier.xsync;

import com.ctrip.xpipe.api.command.Command;
import com.ctrip.xpipe.api.endpoint.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Slight
 * <p>
 * Jun 05, 2022 14:44
 */
public interface StubbornNetworkCommunication extends NetworkCommunication {

    Logger logger = LoggerFactory.getLogger(StubbornNetworkCommunication.class);

    ScheduledExecutorService scheduled();

    boolean isInvoked();

    void markInvoked();

    long reconnectDelayMillis();

    boolean closed();

    /* API */

    @Override
    default void connect(Endpoint endpoint, Object... states) {

        if (!changeTarget(endpoint, states)) return;

        // close and reconnect later by scheduleReconnect()
        disconnect();

        if (!isInvoked()) {
            markInvoked();
            doConnect();
        }
    }

    default void doConnect() {
        if (endpoint() == null) {
            scheduleReconnect();
            return;
        }
        try {
            Command<Object> command = connectCommand();
            command.future().addListener((f) -> {

                logger.info("[future.done()] isSuccess: {}", f.isSuccess());

                if (!f.isSuccess()) {
                    logger.error("[future.done()] fail", f.cause());
                }

                scheduleReconnect();
            });

            logger.info("[doConnect() try execute] {}", endpoint());

            command.execute();
        } catch (Throwable t) {

            logger.error("[doConnect() fail] {}", endpoint());
            logger.error("[doConnect() fail]", t);

            scheduleReconnect();
        }
    }

    default void scheduleReconnect() {

        scheduled().schedule(() -> {
            if (!closed()) {
                doConnect();
            }
        }, reconnectDelayMillis(), TimeUnit.MILLISECONDS);
    }
}
