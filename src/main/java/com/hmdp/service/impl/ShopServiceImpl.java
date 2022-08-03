package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    @Transactional
    public Result update(Shop shop)
    {
        Long id = shop.getId();
        if(id == null)
        {
            return Result.fail("店铺id不能为空");
        }
        System.out.println(shop);
        // 更新数据库
        updateById(shop);
        System.out.println("更新数据库");
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryById(Long id)
    {
        // 逻辑过期解决缓存击穿
         Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id, Shop.class, this::getById,CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //使用自定义工具类解决缓存穿透
        // Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if(shop == null){
            return Result.fail("店铺不存在");
        }
        // 最后返回数据给前端
        return Result.ok(shop);
    }

    public Shop queryWithThrough(Long id)
    {
        // 从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //判断缓存中是否存在以string类型存放的店铺数据
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            //如果存在 直接返回商店对象
            return shop;
        }

        // 判断数据库中是否存储的是为了防止缓存穿透而设置的空值
        if(shopJson != null){
            // 如果是则返回错误信息
            return null;
        }

        // 如果缓存中不存在 则再去数据库中查找
        Shop shop = getById(id);
        if(shop == null)
        {
            // 防止缓存穿透 写入空值到redis中
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        // 最后查找到数据 我们将数据回写到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        // 最后返回数据给前端
        return shop;
    }

    /**
     * 将店铺数据提前写入redis缓存中
     * @param id 店铺id
     * @param expireSeconds 人为设置的逻辑时间
     */
    public void saveShopToRedis(Long id, Long expireSeconds){
        // 1.查询店铺数据
        Shop shop = getById(id);
        // 2.封装逻辑过期时间
        RedisData redisData= new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.将数据写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /*
    public Shop queryWithMutex(Long id)
    {
        // 1.从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断缓存中是否存在以string类型存放的店铺数据
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            //如果存在 直接返回商店对象
            return shop;
        }
        // 3.判断数据库中是否存储的是null 为了防止缓存穿透而设置的空值
        if(shopJson == null){
            // 如果是则返回错误信息
            return null;
        }
        // 4.如果缓存中不存在 则进行缓存重建
        // 4.1 首先尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try
        {
            Boolean isLock = tryLock(lockKey);
            // 4.2 判断互斥锁是否获取成功
            if(!isLock){
                // 如果没有获取到互斥锁 就先休眠并且重试
                // 4.3 如果获取失败 则该线程进行休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 4.4 如果获取成功 则根据id来查询数据库
            shop = getById(id);
            if(shop == null)
            {
                // 5.当商铺数据不存在时 为了防止缓存穿透 写入空值到redis中
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            // 6.最后在数据库中查找到数据 我们将数据回写到redis中
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        } finally
        {
            // 7.释放互斥锁
            unLock(lockKey);
        }
        // 8.最后返回数据给前端
        return shop;
    }

     */

    /*
    public Shop queryWithLogicalExpire(Long id)
    {
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
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        // 获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断该数据是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1如果还未过期 直接返回该店铺数据
            return shop;
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
                    this.saveShopToRedis(id,20L);
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
        // 最后返回数据给前端
        return shop;
    }
     */

}
