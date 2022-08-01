package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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

    @Override
    public Result queryById(Long id)
    {
        // 从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        //判断缓存中是否存在以string类型存放的店铺数据
        if(StrUtil.isNotBlank(shopJson))
        {
            //如果存在 将json格式的店铺数据转换成对象返回
            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
        }
        // 如果不存在 则再去数据库中查找
        Shop shop = getById(id);
        if(shop == null)
        {
            return Result.fail("商铺数据不存在");
        }
        // 最后查找到数据 我们将数据回写到redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop));
        // 最后返回数据给前端
        return Result.ok(shop);
    }
}
