package com.itheima.prize.msg;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.service.*;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.RedisUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 活动信息预热，每隔1分钟执行一次
 * 查找未来1分钟内（含），要开始的活动
 */
@Component  //将 GameTask 类标记为 Spring 的组件，使其能够被自动扫描并注册到 Spring 容器中。
public class GameTask {
    private final static Logger log = LoggerFactory.getLogger(GameTask.class);
    // 依赖注入 (@Autowired)：注入了一些服务类，如 CardGameService、CardGameProductService、RedisUtil 等。
    // 这些服务类负责与数据库交互或操作 Redis。
    @Autowired
    private CardGameService gameService;
    @Autowired
    private CardGameProductService gameProductService;
    @Autowired
    private CardGameRulesService gameRulesService;
    @Autowired
    private GameLoadService gameLoadService;
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private CardProductService productService;  // 新增的服务类
    @Autowired
    private CardGameProductService cardGameProductService;  // 新增的服务类

    //表示每分钟的开始执行一次（如 00:00:00, 00:01:00 等）。这是基于 Cron 表达式来定义的调度时间。
    @Scheduled(cron = "0 * * * * ?")
    public void execute() { //定时任务的核心逻辑。


        //============================================================================高效代码编写============================================================================
        //目的:加载未来一分钟开始的活动

        // 获取当前时间的Calendar实例
        Calendar calendar = Calendar.getInstance();
        // 清除毫秒部分
        calendar.set(Calendar.MILLISECOND, 0);
        // 获取不带毫秒的Date对象
        Date now = calendar.getTime();
        //分布式锁，防止重复启动任务
        if (!redisUtil.setNx("game_task_" + now.getTime(), 1, 60L)) {
            log.info("task started by another server!");
            return;
        }
        //查询将来1分钟内要开始的活动
        QueryWrapper<CardGame> gameQueryWrapper = new QueryWrapper<>();
        //开始时间大于当前时间
        gameQueryWrapper.gt("starttime", now);
        //小于等于（当前时间+1分钟）
        gameQueryWrapper.le("starttime", DateUtils.addMinutes(now, 1));
        List<CardGame> list = gameService.list(gameQueryWrapper);
        if (list.size() == 0) {
            //没有查到要开始的活动

            //取消lua脚本输出提高压测性能
            //log.info("game list scan : size = 0 ; 没有查到要开始的活动");
            return;
        }

        //取消lua脚本输出提高压测性能
        //log.info("game list scan : size = {}", list.size());

        //有相关活动数据，则将活动数据预热，进redis
        list.forEach(game -> {
            //活动开始时间
            long start = game.getStarttime().getTime();
            //活动结束时间
            long end = game.getEndtime().getTime();
            //计算活动结束时间到现在还有多少秒，作为redis key过期时间
            long expire = (end - now.getTime()) / 1000;
            //long expire = -1; //永不过期
            //活动持续时间（ms）
            long duration = end - start;

            Map queryMap = new HashMap();
            queryMap.put("gameid", game.getId());


            //活动基本信息
            game.setStatus(1);
            redisUtil.set(RedisKeys.INFO + game.getId(), game, -1);

            //取消lua脚本输出提高压测性能
            //log.info("load game info:{},{},{},{}", game.getId(), game.getTitle(), game.getStarttime(), game.getEndtime());

            //活动奖品信息
            List<CardProductDto> products = gameLoadService.getByGameId(game.getId());
            Map<Integer, CardProduct> productMap = new HashMap<>(products.size());
            products.forEach(p -> productMap.put(p.getId(), p));

            //取消lua脚本输出提高压测性能
            //log.info("load product type:{}", productMap.size());

            //奖品数量等配置信息
            List<CardGameProduct> gameProducts = gameProductService.listByMap(queryMap);

            //取消lua脚本输出提高压测性能
            //log.info("load bind product:{}", gameProducts.size());

            //令牌桶
            List<Long> tokenList = new ArrayList();
            gameProducts.forEach(cgp -> {
                //生成amount个start到end之间的随机时间戳做令牌
                for (int i = 0; i < cgp.getAmount(); i++) {
                    long rnd = start + new Random().nextInt((int) duration);
                    //为什么乘1000，再额外加一个随机数呢？ - 防止时间段奖品多时重复
                    //记得取令牌判断时间时，除以1000，还原真正的时间戳
                    long token = rnd * 1000 + new Random().nextInt(999);
                    //将令牌放入令牌桶
                    tokenList.add(token);
                    //以令牌做key，对应的商品为value，创建redis缓存

                    //取消lua脚本输出提高压测性能
                    //log.info("token -> game : {} -> {}", token / 1000, productMap.get(cgp.getProductid()).getName());

                    //token到实际奖品之间建立映射关系
                    redisUtil.set(RedisKeys.TOKEN + game.getId() + "_" + token, productMap.get(cgp.getProductid()), expire);
                }
            });
            //排序后放入redis队列
            Collections.sort(tokenList);
            //取消lua脚本输出提高压测性能
            //log.info("load tokens:{}", tokenList);

            //从右侧压入队列，从左到右，时间戳逐个增大
            redisUtil.rightPushAll(RedisKeys.TOKENS + game.getId(), tokenList);
            redisUtil.expire(RedisKeys.TOKENS + game.getId(), expire);

            //奖品策略配置信息
            List<CardGameRules> rules = gameRulesService.listByMap(queryMap);
            //遍历策略，存入redis hset
            rules.forEach(r -> {
                redisUtil.hset(RedisKeys.MAXGOAL + game.getId(), r.getUserlevel() + "", r.getGoalTimes());
                redisUtil.hset(RedisKeys.MAXENTER + game.getId(), r.getUserlevel() + "", r.getEnterTimes());
                redisUtil.hset(RedisKeys.RANDOMRATE + game.getId(), r.getUserlevel() + "", r.getRandomRate());
                log.info("load rules:level={},enter={},goal={},rate={}",
                        r.getUserlevel(), r.getEnterTimes(), r.getGoalTimes(), r.getRandomRate());
            });
            redisUtil.expire(RedisKeys.MAXGOAL + game.getId(), expire);
            redisUtil.expire(RedisKeys.MAXENTER + game.getId(), expire);
            redisUtil.expire(RedisKeys.RANDOMRATE + game.getId(), expire);


            //活动状态变更为已预热，禁止管理后台再随便变动
            game.setStatus(1);
            gameService.updateById(game);

        });
        //============================================================================高效代码编写============================================================================



        /*
        //------------------------------------------------------------------------------初次代码编写------------------------------------------------------------------------------

        log.info("开始执行活动信息预热: " + new Date());
        // 查询未来1分钟内将要开始的活动
        Date now = new Date();  //定义当前时间now
        Date oneMinuteLater = DateUtils.addMinutes(now, 1);  //定义1分钟后的时间
        //通过 CardGameService 查询数据库，获取将在未来 1 分钟内开始的活动（start_time 在 now 和 oneMinuteLater 之间）。
        List<CardGame> upcomingGames = gameService.list(new QueryWrapper<CardGame>()
                .ge("starttime", now)
                .le("starttime", oneMinuteLater));

        for (CardGame game : upcomingGames) {
            log.info("预加载活动: " + game.getId());
            // 1. 缓存预加载的活动基本信息
            // 将活动的基本信息存储到 Redis 中，RedisKeys.INFO + game.getId() 是 Redis 的键名，game 是活动对象，-1 表示永久缓存
            redisUtil.set(RedisKeys.INFO + game.getId(), game, -1);

            // 2. 生成令牌桶（奖品时间戳）
            // 通过 generateTokenList() 生成奖品对应的时间戳，并将其存储到 Redis 的 TOKENS 键中。
            List<String> tokenList = generateTokenList(game);
            // 将生成的所有令牌列表推送到 Redis 中。
            redisUtil.rightPushAll(RedisKeys.TOKENS + game.getId(), tokenList);

            // 3. 缓存活动策略（抽奖次数、中奖次数）
            // 利用 CardGameRulesService 查询不同用户等级的抽奖规则，并将其存储到 Redis 中。
            List<CardGameRules> rules = gameRulesService.list(new QueryWrapper<CardGameRules>()
                    .eq("gameid", game.getId()));
            for (CardGameRules rule : rules) {
                // MAXGOAL 键存储用户等级对应的中奖次数，MAXENTER 键存储用户等级对应的参与次数。
                redisUtil.hset(RedisKeys.MAXGOAL + game.getId(), String.valueOf(rule.getUserlevel()), rule.getGoalTimes());
                redisUtil.hset(RedisKeys.MAXENTER + game.getId(), String.valueOf(rule.getUserlevel()), rule.getEnterTimes());
            }
        }
    }

    //令牌桶生成逻辑
    private List<String> generateTokenList(CardGame game) {
        List<String> tokenList = new ArrayList<>();

        // 活动持续时间（毫秒）
        // 通过 game.getEndtime().getTime() - game.getStarttime().getTime() 计算活动的总时长。
        long duration = game.getEndtime().getTime() - game.getStarttime().getTime();
        Random random = new Random();

        // 调用getProductDtosForGame方法 - 获取所有奖品的 DTO 列表及总数量
        List<CardProductDto> productDtos = getProductDtosForGame(game);
        // 计算所有 CardProductDto 对象的 amount 总和 - 将 totalAmount 存储到变量中
        int totalAmount = productDtos.stream()
                .mapToInt(CardProductDto::getAmount)  // 提取每个 CardProductDto 对象的 amount
                .sum();  // 对所有 amount 值求和

        for (CardProductDto productDto : productDtos) {
            // 为每个奖品生成相应数量的令牌
            for (int i = 0; i < productDto.getAmount(); i++) {
                long rnd = game.getStarttime().getTime() + random.nextInt((int) duration);
                // 防止令牌重复
                long token = rnd * 1000 + random.nextInt(999);
                String tokenStr = String.valueOf(token);
                tokenList.add(tokenStr);

                // 将每个令牌与奖品关联，存入 Redis , -1表示一直存储
                // 注意事项:在redis中使用get取出的时候一定要注意key的名称一样,这里的token是转化为了字符串格式的!!!
                redisUtil.set(RedisKeys.TOKEN + game.getId() + "_" + tokenStr, productDto, -1);
            }
        }
        return tokenList;
    }


    // 获取某个活动的所有奖品 DTO 列表
    private List<CardProductDto> getProductDtosForGame(CardGame game) {

        //--------------------------------------------------------------获取一个活动的所有奖品数量(totalAmount)--------------------------------------------------------------

        // 1. 查询 CardGameProduct 表中 gameid 等于传入 gameid 的用户记录
        QueryWrapper<CardGameProduct> queryWrapper = new QueryWrapper<>();  //创建一个QueryWrapper对象，用于构建查询条件。
        queryWrapper.eq("gameid", game.getId());  //通过 eq 方法设置了查询条件，即查找 gameid 字段值等于变量 gameid 的用户记录。
        List<CardGameProduct> cardGameProducts = cardGameProductService.list(queryWrapper);  //调用 cardGameProductService 的 list 方法，根据 queryWrapper 中的条件查询数据库，返回符合条件的 CardGameProduct 对象列表。

        // 2. 获取所有找到的用户记录对应的 productid
        List<Integer> productIds = cardGameProducts.stream()  //将 cardGameProducts 列表转换为一个流。
                .map(CardGameProduct::getProductid)  //对流中的每个 CardGameProduct 对象调用 getProductid 方法，获取 productid 值。
                .collect(Collectors.toList());  //将获取的 productid 值收集到一个列表中。

        // 3. 查询 CardProduct 表中 productid 在这些 productIds 中的记录
        QueryWrapper<CardProduct> productQueryWrapper = new QueryWrapper<>();  //用于构建查询条件。
        productQueryWrapper.in("id", productIds);  //设置查询条件，即查找 id 字段值在 productIds 列表中的记录。
        List<CardProduct> cardProducts = productService.list(productQueryWrapper);  //根据构建的查询条件，查询数据库，返回符合条件的 CardProduct 对象列表。

        // 4. 将 CardProduct 对象转换为 CardProductDto 对象，并设置 account 值
        return cardProducts.stream()
                .map(product -> {  //对流中的每个 CardProduct 对象进行映射操作，将其转换为 CardProductDto 对象。
                    CardProductDto dto = new CardProductDto();  //创建一个新的 CardProductDto 对象。
                    dto.setId(product.getId());  //将 CardProduct 对象的 id 赋值给 CardProductDto 对象。
                    dto.setName(product.getName());  //将 CardProduct 对象的 name 赋值给 CardProductDto 对象。
                    dto.setPic(product.getPic());  //将 CardProduct 对象的 pic 赋值给 CardProductDto 对象。
                    dto.setInfo(product.getInfo());  //将 CardProduct 对象的 info 赋值给 CardProductDto 对象。
                    dto.setPrice(product.getPrice());  //将 CardProduct 对象的 price 赋值给 CardProductDto 对象。

                    // 查找对应的 CardGameProduct 记录，获取 account 值
                    // 在 cardGameProducts 列表中查找与当前 CardProduct 对象 id 匹配的 CardGameProduct 记录。
                    CardGameProduct cardGameProduct = cardGameProducts.stream()
                            .filter(cgProduct -> cgProduct.getProductid().equals(product.getId()))
                            .findFirst()
                            .orElse(null);
                    // 如果找到对应的 CardGameProduct 记录，则将其 amount 值赋给 CardProductDto 对象的 amount 字段。
                    if (cardGameProduct != null) {
                        dto.setAmount(cardGameProduct.getAmount());
                    } else {
                        dto.setAmount(0);  // 如果没有找到对应的 CardGameProduct 记录，设置 amount 为 0
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        //--------------------------------------------------------------获取一个活动的所有奖品数量(totalAmount)--------------------------------------------------------------

    }

        //------------------------------------------------------------------------------初次代码编写------------------------------------------------------------------------------
         */
    }
}





/*

----GameTask中的缓存预热逻辑实现----
GameTask类中的定时任务需要每分钟预加载未来1分钟内将开始的活动信息到Redis中，
以确保活动开启时系统能够高效处理高并发请求。具体步骤如下：

实现步骤：
1.查询未来1分钟内开始的活动：
使用CardGameService查询符合条件的活动。

2.加载活动奖品信息：
通过CardGameProductService获取活动的奖品列表，并生成相应的令牌桶（奖品对应的时间戳）。

3.加载活动策略：
利用CardGameRulesService获取每个用户等级的抽奖策略，并存储到Redis中。

4.预热Redis缓存：
将活动的基本信息、奖品信息、策略信息等提前放入Redis

 */