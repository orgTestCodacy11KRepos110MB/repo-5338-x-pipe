package com.ctrip.xpipe.redis.console.controller.api;

import com.ctrip.xpipe.redis.console.controller.AbstractConsoleController;
import com.ctrip.xpipe.redis.console.controller.api.data.meta.ResourceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

import static com.ctrip.xpipe.redis.console.migration.MigrationResources.*;

/**
 * @author lishanglin
 * date 2022/3/31
 */
@RestController
@RequestMapping(AbstractConsoleController.API_PREFIX)
public class ResourceController {

    @Resource(name = MIGRATION_EXECUTOR)
    private ExecutorService migrationExecutor;

    @Resource(name = MIGRATION_PREPARE_EXECUTOR)
    private ExecutorService prepareExecutor;

    @Resource(name = MIGRATION_IO_CALLBACK_EXECUTOR)
    private ExecutorService ioCallBackExecutor;

    private static final String CLAZZ_DELEGATED_EXECUTOR_SERVICE = "DelegatedExecutorService";

    private Logger logger = LoggerFactory.getLogger(ResourceController.class);

    @GetMapping("/resource")
    public ResourceInfo getResource() {
        ResourceInfo resourceInfo = new ResourceInfo();
        ThreadPoolExecutor migrationThreadPoolExecutor = getInnerPoolExecutor(migrationExecutor);
        ThreadPoolExecutor prepareThreadPoolExecutor = getInnerPoolExecutor(prepareExecutor);
        ThreadPoolExecutor ioCallBackThreadPoolExecutor = getInnerPoolExecutor(ioCallBackExecutor);

        resourceInfo.collectDataFromMigrationExecutor(migrationThreadPoolExecutor);
        resourceInfo.collectDataFromPrepareExecutor(prepareThreadPoolExecutor);
        resourceInfo.collectDataFromIoCallbackExecutor(ioCallBackThreadPoolExecutor);

        return resourceInfo;
    }

    private ThreadPoolExecutor getInnerPoolExecutor(ExecutorService executor) {
        Class<?>[] classes = Executors.class.getDeclaredClasses();
        Class<?> classDelegatedExecutorService = Stream.of(classes)
                .filter(clazz -> clazz.getSimpleName().equals(CLAZZ_DELEGATED_EXECUTOR_SERVICE))
                .findFirst().orElse(null);

        if (null == classDelegatedExecutorService) {
            logger.info("[getInnerPoolExecutor][reflect not found] {}", CLAZZ_DELEGATED_EXECUTOR_SERVICE);
            return null;
        }

        try {
            Field innerExecutorField = classDelegatedExecutorService.getDeclaredField("e");
            innerExecutorField.setAccessible(true);
            Object obj = innerExecutorField.get(executor);
            if (obj instanceof ThreadPoolExecutor) {
                return (ThreadPoolExecutor) obj;
            } else if(null != obj) {
                logger.info("[getInnerPoolExecutor][unexpected executor] {}", obj.getClass().getName());
            } else {
                logger.info("[getInnerPoolExecutor][executor null]");
            }
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            logger.info("[getInnerPoolExecutor][reflect no field]", e);
        }

        return null;
    }

}
