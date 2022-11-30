package com.ctrip.xpipe.redis.keeper.applier.xsync;

import com.ctrip.xpipe.api.command.Command;
import com.ctrip.xpipe.api.endpoint.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author Slight
 * <p>
 * Jun 05, 2022 15:15
 */
public interface NetworkCommunication extends NetworkCommunicationState {

    Logger logger = LoggerFactory.getLogger(NetworkCommunication.class);

    Command<Object> connectCommand() throws Exception;

    void doDisconnect() throws Exception;

    default boolean changeTarget(Endpoint endpoint, Object... states) {
        if (isConnected()) {
            if (Objects.equals(endpoint(), endpoint)) {
                return false;
            }
        }

        initState(endpoint, states);
        return true;
    }

    /* API */

    default void connect(Endpoint endpoint, Object... states) {

        if (!changeTarget(endpoint, states)) return;

        disconnect();

        try {
            connectCommand().execute();
        } catch (Throwable t) {
            logger.error("[doConnect() fail] {}", endpoint(), t);
        }
    }

    default void disconnect() {
        try {
            doDisconnect();
        } catch (Throwable t) {
            logger.error("[doDisconnect() fail]  {}", endpoint(), t);
        }
    }
}