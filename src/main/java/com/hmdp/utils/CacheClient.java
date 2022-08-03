package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * @author XTY~
 * @version 1.0
 * @Date: 2022-08-02 16:40
 * @Description: 封装的有关redis的工具
 */
@Component
public class CacheClient
{
    @Resource
    private final StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    public CacheClient(StringRedisTemplate stringRedisTemplate)
    {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 设置逻辑过期时间解决穿透
     * @param key redis的key
     * @param value 传过来的数据
     * @param time 逻辑过期时间
     * @param unit 时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData();
        // 设置value值和逻辑过期时间
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 防止缓存穿透
     * @param keyPrefix 索引前缀
     * @param id 待查询的数据的id
     * @param type 带查询数据的类型
     * @param <R> 用于数据类型的泛型
     * @param <ID> 用于保存数据id的泛型
     * @param dbFallback 要求调用者返回一个有关操作数据库函数的值
     * @return 查询数据
     */
        public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type , Function<ID, R> dbFallback, Long time, TimeUnit unit)
    {
        String key = keyPrefix + id;

        // 从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断缓存中是否存在以json类型存放的对象数据
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            //如果存在 直接返回查询的结果对象
            return JSONUtil.toBean(shopJson,type);
        }

        // 判断数据库中是否存储的是为了防止缓存穿透而设置的空值
        if(shopJson != null){
            // 如果是则返回错误信息
            return null;
        }

        // 如果缓存中不存在 则等待工具调用者传递
        R r = dbFallback.apply(id);
        if(r == null)
        {
            // 防止缓存穿透 写入空值到redis中
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 最后查找到数据 我们将数据回写到redis中
        this.set(key,r,time,unit);
        // 最后返回数据
        return r;
    }

    // 为了在成功获取到互斥锁后，开辟独立线程而创建一个线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 用于解决缓存击穿
     * @param keyPrefix 索引前缀
     * @param id 待查询数据的id
     * @param type 待查询数据的类型
     * @param dbFalLBack 调用者完成指定的的函数业务从而得到的返回值
     * @param time 指定的逻辑过期时间
     * @param unit 过期时间单位
     * @param <R> 接收带查询数据的泛型
     * @param <ID> 接收待查询数据id的泛型
     * @return 返回查询所得数据
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFalLBack, Long time, TimeUnit unit)
    {
        String key = keyPrefix + id;
        // 1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.判断缓存中是否存在以string类型存放的店铺数据
        if(StrUtil.isBlank(shopJson)) {
            //如果不存在 直接返回null
            return null;
        }
        // 3.如果在缓存中命中
        // 4.需要先将json反序列化成为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断该数据是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1如果还未过期 直接返回查询到的数据
            return r;
        }
        // 5.2如果已经过期 则需要进行缓存重建
        // 6.开始缓存重建
        // 6.1首先尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);
        // 6.1.1如果成功获取互斥锁，则新开一个独立线程线程，实现缓存重建，并且返回缓存
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try
                {
                    // 查询数据库
                    R r1 = dbFalLBack.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e)
                {
                    throw new RuntimeException(e);
                } finally
                {
                    //重建缓存后 释放锁
                    unLock(lockKey);
                }
            });
        }
        // 最后返回数据
        return r;
    }

    /**
     * 获取自定义锁
     * @param key 锁名
     * @return 是否成功获取锁
     */
    private Boolean tryLock(String key){
        Boolean flab = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flab);
    }


    /**
     * 释放自定义锁
     * @param key 锁名
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}