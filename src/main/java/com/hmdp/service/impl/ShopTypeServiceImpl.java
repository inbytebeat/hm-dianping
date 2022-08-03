package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList()
    {
        // 先查询redis中的有关商铺类型的所有数据 看在redis缓存中有没有 如果有，则以list形式返回
        List<String> shopTypeListData = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOPlist_KEY, 0, -1);
        if (!shopTypeListData.isEmpty())
        {
            // 如果redis存有数据 则依次将string类型的商铺种类数据转换成为ShopType类型存入list返回给前端
            List<ShopType> shopTypeList = new ArrayList<>();
                for (String data : shopTypeListData) {
                    shopTypeList.add(JSONUtil.toBean(data,ShopType.class));
                }
            // 将查询结果返回
            return Result.ok(shopTypeList);
        }
        // 如果redis中没有查到 则去数据库中继续查
        List<ShopType> list = query().list();
        if(list.isEmpty())
        {
            return Result.fail("无商铺类型数据");
        }
        // 如果查到了 则现将商铺类型数据转换成为string 然后存入redis的list中
        for (ShopType data : list)
        {
            String shopTy = JSONUtil.toJsonStr(data);
            shopTypeListData.add(shopTy);
        }
        return Result.ok(list);
    }
}
