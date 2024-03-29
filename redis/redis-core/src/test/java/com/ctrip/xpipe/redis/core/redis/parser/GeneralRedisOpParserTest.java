package com.ctrip.xpipe.redis.core.redis.parser;

import com.ctrip.xpipe.redis.core.redis.operation.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author lishanglin
 * date 2022/2/18
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class GeneralRedisOpParserTest extends AbstractRedisOpParserTest {

    @Test
    public void testSetParse() {
        RedisOp redisOp = parser.parse(Arrays.asList("SET", "k1", "v1").toArray());
        Assert.assertEquals(RedisOpType.SET, redisOp.getOpType());
        Assert.assertNull(redisOp.getOpGtid());
        Assert.assertArrayEquals(strList2bytesArray(Arrays.asList("SET", "k1", "v1")), redisOp.buildRawOpArgs());

        RedisSingleKeyOp redisSingleKeyOp = (RedisSingleKeyOp) redisOp;
        Assert.assertArrayEquals("k1".getBytes(), redisSingleKeyOp.getKey().get());
        Assert.assertArrayEquals("v1".getBytes(), redisSingleKeyOp.getValue());
    }

    @Test
    public void testGtidParse() {
        RedisOp redisOp = parser.parse(Arrays.asList("GTID", "a1:10", "0", "set", "k1", "v1").toArray());
        Assert.assertEquals(RedisOpType.SET, redisOp.getOpType());
        Assert.assertEquals("a1:10", redisOp.getOpGtid());
        Assert.assertArrayEquals(strList2bytesArray(Arrays.asList("GTID", "a1:10", "0", "set", "k1", "v1")), redisOp.buildRawOpArgs());

        RedisSingleKeyOp redisSingleKeyOp = (RedisSingleKeyOp) redisOp;
        Assert.assertArrayEquals("k1".getBytes(), redisSingleKeyOp.getKey().get());
        Assert.assertArrayEquals("v1".getBytes(), redisSingleKeyOp.getValue());
    }

    @Test
    public void testMSetParse() {
        RedisOp redisOp = parser.parse(Arrays.asList("MSET", "k1", "v1", "k2", "v2").toArray());
        Assert.assertEquals(RedisOpType.MSET, redisOp.getOpType());
        Assert.assertNull(redisOp.getOpGtid());
        Assert.assertArrayEquals(strList2bytesArray(Arrays.asList("MSET", "k1", "v1", "k2", "v2")), redisOp.buildRawOpArgs());

        RedisMultiKeyOp redisMultiKeyOp = (RedisMultiKeyOp) redisOp;
        Assert.assertEquals(2, redisMultiKeyOp.getKeys().size());
        Assert.assertEquals(new RedisKey("k1"), redisMultiKeyOp.getKeyValue(0).getKey());
        Assert.assertArrayEquals("v1".getBytes(), redisMultiKeyOp.getKeyValue(0).getValue());
        Assert.assertEquals(new RedisKey("k2"), redisMultiKeyOp.getKeyValue(1).getKey());
        Assert.assertArrayEquals("v2".getBytes(), redisMultiKeyOp.getKeyValue(1).getValue());
    }

    @Test
    public void testGtidMSetParse() {
        RedisOp redisOp = parser.parse(Arrays.asList("GTID", "a1:10", "0", "MSET", "k1", "v1", "k2", "v2").toArray());
        Assert.assertEquals(RedisOpType.MSET, redisOp.getOpType());
        Assert.assertEquals("a1:10", redisOp.getOpGtid());
        Assert.assertArrayEquals(strList2bytesArray(Arrays.asList("GTID", "a1:10", "0", "MSET", "k1", "v1", "k2", "v2")), redisOp.buildRawOpArgs());

        RedisMultiKeyOp redisMultiKeyOp = (RedisMultiKeyOp) redisOp;
        Assert.assertEquals(2, redisMultiKeyOp.getKeys().size());
        Assert.assertEquals(new RedisKey("k1"), redisMultiKeyOp.getKeyValue(0).getKey());
        Assert.assertArrayEquals("v1".getBytes(), redisMultiKeyOp.getKeyValue(0).getValue());
        Assert.assertEquals(new RedisKey("k2"), redisMultiKeyOp.getKeyValue(1).getKey());
        Assert.assertArrayEquals("v2".getBytes(), redisMultiKeyOp.getKeyValue(1).getValue());
    }

    @Test
    public void testSelectParse() {
        RedisOp redisOp = parser.parse(Arrays.asList("SELECT", "0").toArray());
        Assert.assertEquals(RedisOpType.SELECT, redisOp.getOpType());
    }

    @Test
    public void testPingParse() {
        RedisOp redisOp = parser.parse(Arrays.asList("PING", "TEST").toArray());
        Assert.assertEquals(RedisOpType.PING, redisOp.getOpType());

        RedisSingleKeyOp redisSingleKeyOp = (RedisSingleKeyOp) redisOp;
        Assert.assertNull(redisSingleKeyOp.getKey());
        Assert.assertNull(redisSingleKeyOp.getValue());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNoneExistsCmdParse() {
        RedisOp redisOp = parser.parse(Arrays.asList("EMPTY", "0").toArray());
        Assert.assertEquals(RedisOpType.UNKNOWN, redisOp.getOpType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParamShorterParse() {
        parser.parse(Arrays.asList("SET").toArray());
    }

    @Test
    public void testParamLongerParse() {
        RedisOp redisOp = parser.parse(Arrays.asList("SET", "a", "1", "b").toArray());
        Assert.assertEquals(RedisOpType.SET, redisOp.getOpType());
    }

    @Test
    public void testParseAllCmds() {
        List<String> cmdList = Arrays.asList(
                "append",
                "decr",
                "decrby",
                "del",
                "expire",
                "expireat",
                "geoadd",
                "georadius",
                "getset",
                "hdel",
                "hincrby",
                "hincrbyfloat",
                "hmset",
                "hset",
                "hsetnx",
                "incr",
                "incrby",
                "linsert",
                "lpop",
                "lpush",
                "lpushx",
                "lrem",
                "lset",
                "ltrim",
                "move",
                "mset",
                "msetnx",
                "persist",
                "pexpire",
                "pexpireat",
                "psetex",
                "rpop",
                "rpush",
                "rpushx",
                "sadd",
                "set",
                "setbit",
                "setex",
                "setnx",
                "setrange",
                "spop",
                "srem",
                "unlink",
                "zadd",
                "zincrby",
                "zrem",
                "zremrangebylex",
                "zremrangebyrank",
                "zremrangebyscore",
                "incrbyfloat",
                "publish",
                "ping",
                "select",
                "exec",
                "script",
                "multi"
        );

        for (String cmd : cmdList) {
            RedisOpType redisOpType = RedisOpType.lookup(cmd);
            List<String> parserList = new ArrayList<>();
            System.out.println(cmd);
            parserList.add(cmd);
            for (int i = 0; i < Math.abs(redisOpType.getArity()) - 1; i++) {
                parserList.add("0");
            }
            parser.parse(parserList.toArray());
        }
    }
}
