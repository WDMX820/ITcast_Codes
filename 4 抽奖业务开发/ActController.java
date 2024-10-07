package com.itheima.prize.api.action;

import com.alibaba.fastjson.JSON;
import com.itheima.prize.api.config.LuaScript;
import com.itheima.prize.commons.config.RabbitKeys;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.mapper.CardGameMapper;
import com.itheima.prize.commons.db.service.CardGameService;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/act")
@Api(tags = {"抽奖模块"})
public class ActController {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private LuaScript luaScript;

    @GetMapping("/limits/{gameid}")
    @ApiOperation(value = "剩余次数")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult<Object> limits(@PathVariable int gameid, HttpServletRequest request){
        //获取活动基本信息
        CardGame game = (CardGame) redisUtil.get(RedisKeys.INFO+gameid);
        if (game == null){
            return new ApiResult<>(-1,"活动未加载",null);
        }
        //获取当前用户
        HttpSession session = request.getSession();
        CardUser user = (CardUser) session.getAttribute("user");
        if (user == null){
            return new ApiResult(-1,"未登陆",null);
        }
        //用户可抽奖次数
        Integer enter = (Integer) redisUtil.get(RedisKeys.USERENTER+gameid+"_"+user.getId());
        if (enter == null){
            enter = 0;
        }
        //根据会员等级，获取本活动允许的最大抽奖次数
        Integer maxenter = (Integer) redisUtil.hget(RedisKeys.MAXENTER+gameid,user.getLevel()+"");
        //如果没设置，默认为0，即：不限制次数
        maxenter = maxenter==null ? 0 : maxenter;

        //用户已中奖次数
        Integer count = (Integer) redisUtil.get(RedisKeys.USERHIT+gameid+"_"+user.getId());
        if (count == null){
            count = 0;
        }
        //根据会员等级，获取本活动允许的最大中奖数
        Integer maxcount = (Integer) redisUtil.hget(RedisKeys.MAXGOAL+gameid,user.getLevel()+"");
        //如果没设置，默认为0，即：不限制次数
        maxcount = maxcount==null ? 0 : maxcount;

        //幸运转盘类，先给用户随机剔除，再获取令牌，有就中，没有就说明抢光了
        //一般这种情况会设置足够的商品，卡在随机上
        Integer randomRate = (Integer) redisUtil.hget(RedisKeys.RANDOMRATE+gameid,user.getLevel()+"");
        if (randomRate == null){
            randomRate = 100;
        }

        Map map = new HashMap();
        map.put("maxenter",maxenter);
        map.put("enter",enter);
        map.put("maxcount",maxcount);
        map.put("count",count);
        map.put("randomRate",randomRate);

        return new ApiResult<>(1,"成功",map);
    }


    @GetMapping("/go/{gameid}")
    @ApiOperation(value = "抽奖")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult<Object> act(@PathVariable int gameid, HttpServletRequest request){



        //============================================================================高效代码编写============================================================================
        Date currentTime = new Date();
        //获取活动基本信息
        CardGame activeGame =(CardGame) redisUtil.get(RedisKeys.INFO + gameid);

        //判断活动是否已经开始
        //若活动信息未加载至 Redis 或者活动已加载但预热结束,且开始时间晚于当前时间,视为无效
        if(activeGame == null || activeGame.getStarttime().after(currentTime)) {
            return new ApiResult(-1, "活动未开始", null);
        }

        //检查活动是否结束
        if (currentTime.after(activeGame.getEndtime())) {
            return new ApiResult(-1, "活动已结束", null);
        }

        //获取当前用户
        HttpSession session =request.getSession();
        CardUser currentUser=(CardUser)session.getAttribute("user");
        if(currentUser == null){
            return new ApiResult(-1,"未登录",null);
        } else {
            // 发送消息队列，记录参与的活动 - Redis分布式锁
            if (redisUtil.setNx(RedisKeys.USERGAME+currentUser.getId()+"_"+gameid,1,
                    (activeGame.getEndtime().getTime() - currentTime.getTime())/1000)) {
                //持久化用户活动参与记录，并通过消息队列异步处理
                CardUserGame userGame = new CardUserGame();
                userGame.setUserid(currentUser.getId());
                userGame.setGameid(gameid);
                userGame.setCreatetime(new Date());
                rabbitTemplate.convertAndSend(RabbitKeys.EXCHANGE_DIRECT, RabbitKeys.QUEUE_PLAY, JSON.toJSONString(userGame));
            }
        }
        //获取会员等级允许的最大抽奖次数
        Integer maxAttempts = (Integer) redisUtil.hget(RedisKeys.MAXENTER+gameid,currentUser.getLevel()+"");
        //如果没设置,默认为0 - 不限制次数
        if (maxAttempts == null) {
            maxAttempts = 0;
        }
        //检查用户抽奖次数
        if(maxAttempts > 0) {
            //用户可抽奖次数
            long enter = redisUtil.incr(RedisKeys.USERENTER + gameid + "_" + currentUser.getId(), 1);
            if (enter > maxAttempts) {
                //如果达到最大次数，不介许抽奖
                return new ApiResult(-1, "您的抽奖次数已用完", null);
            }
        }
        //获取会员等级允许的最大中奖次数
        Integer maxWins = (Integer) redisUtil.hget(RedisKeys.MAXGOAL+gameid, currentUser.getLevel()+"");
        //如果没设置,默认为0 - 不限制次数
        if (maxWins == null) {
            maxWins = 0;
        }
        //检验完成,接下来获取中奖令牌(token)
        Long token;
        //token.intValue() 确保 token 为整型，便于进行精确匹配。
        token = luaScript.tokenCheck(gameid,currentUser.getId(),maxWins);
        switch (token.intValue()) {
            case 0:
                return new ApiResult(0, "未中奖", null);
            case -1:
                return new ApiResult(-1, "您已达到最大中奖次数", null);
            case -2:
                return new ApiResult(-1, "奖品已抽光", null);
            default:
                break;
        }

        //获取合法的 token 后,意味着中奖
        //从 Redis 中获取相应的奖品信息
        CardProduct awardProduct = (CardProduct) redisUtil.get(RedisKeys.TOKEN + gameid +"_"+token);

        //创建中奖信息对象 CardUserHit 以封装中奖信息
        CardUserHit hit = new CardUserHit();
        hit.setGameid(gameid);  //活动id
        hit.setHittime(currentTime);    //中奖时间
        hit.setProductid(awardProduct.getId());  //奖品id
        hit.setUserid(currentUser.getId());        //中奖用户的id


        //3.将中奖信息发送到消息队列
        //使用rabbitTemplate.convertAndSend()方法将中奖信息发送到消息队列
        //----RabbitKeys.EXCHANGE_DIRECT：这是消息交换机的名称，用于确定消息的路由规则
        //----RabbitKeys.QUEUE_HIT：这是队列的名称，表示消息会发送到此队列，供后续的消息模块消费处理
        //----JSON.toJSONString(hit)：将CardUserHit对象序列化为JSON格式，方便在消息队列中传输
        rabbitTemplate.convertAndSend(RabbitKeys.EXCHANGE_DIRECT,RabbitKeys.QUEUE_HIT,JSON.toJSONString(hit));

        //返回给前台中奖信息
        return new ApiResult(1,"恭喜中奖",awardProduct);

        //这段代码首先从 Redis 获取中奖的奖品信息，然后构建一个 CardUserHit 对象来存储中奖信息(包括活动ID、用户ID等)
        //最后通过 RabbitMQ 消息队列将该信息异步发送出去，让消息模块去处理后续的耗时操作。
        //============================================================================高效代码编写============================================================================



        /*
        //------------------------------------------------------------------------------初次代码编写------------------------------------------------------------------------------
        // 1. 获取当前用户
        HttpSession session = request.getSession();
        CardUser user = (CardUser) session.getAttribute("user");
        //测试输出 - 有
        //System.out.println(user);

        if (user == null) {
            return new ApiResult<>(-1, "未登录", null); // 未登录
        }

        // 2. 获取活动信息
        CardGame game = (CardGame) redisUtil.get(RedisKeys.INFO + gameid);
        //测试输出 - 有
        //System.out.println(game);

        if (game == null) {
            return new ApiResult<>(-1, "活动未开始", null);
        }

        // 3. 判断活动是否已经结束
        Date now = new Date();
        if (now.after(game.getEndtime())) {
            return new ApiResult<>(-1, "活动已结束", null); // 活动已结束
        }

        // 4. 获取最大中奖次数
        Integer maxGoal = (Integer) redisUtil.hget(RedisKeys.MAXGOAL + gameid, user.getLevel().toString());
        //测试输出 - 提醒: 默认的登录账户"bxg"是金牌会员 - 有
        //System.out.println(maxGoal);

        //测试输出
        //System.out.println(gameid);  //gameId: 活动id - 有
        //System.out.println(user.getId());  //userId：当前登录用户的id - 有
        //System.out.println(maxGoal);  //maxCount：当前活动允许的最大中奖次数 - 有

        // 5. 调用Lua脚本处理令牌
        Long token = luaScript.tokenCheck(gameid, user.getId(), maxGoal);
        //测试输出 - 无
        System.out.println(token);

        // 6. 判断Lua脚本返回结果
        if (token == -1) {
            return new ApiResult<>(-1, "您已达到最大中奖数", null); // 达到最大中奖数
        } else if (token == -2) {
            return new ApiResult<>(-1, "奖品已抽光", null); // 奖品已抽光
        } else if (token == 0) {
            return new ApiResult<>(0, "未中奖", null); // 未中奖
        } else {
            // 7. 中奖逻辑
            CardProduct cardProduct = (CardProduct) redisUtil.get(RedisKeys.TOKEN + gameid + "_" + token);
            if (cardProduct != null) {

                // 发送中奖消息到RabbitMQ队列
                rabbitTemplate.convertAndSend(RabbitKeys.QUEUE_HIT, JSON.toJSONString(cardProduct));

                // 更新中奖次数
                redisUtil.incr(RedisKeys.USERHIT + gameid + "_" + user.getId(), 1);
                return new ApiResult<>(1, "恭喜中奖", cardProduct); // 中奖
            }
            return new ApiResult<>(-1, "奖品信息异常", null); // 中奖，但奖品信息异常
        }
        //个人代码问题:无法利用mq完成下列操作 - api发中奖信息-msg在背后写入mysql
        //此操作可以利用Mysql写入操作,但是面对高并发性,压测时性能会很差
        //------------------------------------------------------------------------------初次代码编写------------------------------------------------------------------------------
         */

    }



    @GetMapping("/info/{gameid}")
    @ApiOperation(value = "缓存信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult info(@PathVariable int gameid){



        //============================================================================高效代码编写============================================================================
        //代码目的：Redis 中获取与 gameid 相关的信息，组织成一个 Map 对象，并返回一个包含这些信息的 ApiResult 对象。

        //1.初始化Map对象
        //创建了一个有序的LinkedHashMao，用于存储从Redis中获取的信息。
        Map map = new LinkedHashMap<>();

        //2.获取活动的基本信息
        //通过 redisuti1.get()方法，从 Redis 中获取键为INFO+ gameid 的数据，存储到 map 中；RedisKeys.INFO + gameid 是 Redis 中存储游戏信息的键名。
        map.put(RedisKeys.INFO+gameid,redisUtil.get(RedisKeys.INFO+gameid));

        //3.获取Token列表
        //通过 redisUtil.lrange()方法，从 Redis 的列表(List)中获取与gameid 相关的所有 Token，范围是从0到 -1，表示获取整个列表。
        List<Object> tokens = redisUtil.lrange(RedisKeys.TOKENS+gameid,0,-1);

        //4.构建Token的映射
        //这里遍历 tokens 列表中的每个 Token，将每个 Token 的时间戳(经过格式化)作为键，Token 相关的信息作为值，存入tokenMap
        //----将 Token 转为 1ong ，除以 1000，表示转换为秒级时间戳。
        //----使用 simpleDateFormat 将时间戳格式化为yyyy-MM-dd HH:mm:ss.sss 格式。
        //----通过 redisuti1.get()获取与每个 Token 相关的信息，键名为 TOKEN + gameid +""
        Map tokenMap = new LinkedHashMap();
        tokens.forEach(o -> tokenMap.put(
                new SimpleDateFormat("yyyy-MM-dd HH:m:ss.SSS").format(new Date(Long.valueOf(o.toString())/1000)),
                redisUtil.get(RedisKeys.TOKEN + gameid +"_"+o))
        );

        //5.将token映射存入主Map - Key为TOKENS + gameid
        map.put(RedisKeys.TOKENS+gameid,tokenMap);

        //6.获取最大目标和最大进入人数
        //分别从Redis中获取与 MAXGOAL + gameid 和 MAXENTER + gameid 相关的哈希表数据，并存入map。
        map.put(RedisKeys.MAXGOAL+gameid,redisUtil.hmget(RedisKeys.MAXGOAL+gameid));
        map.put(RedisKeys.MAXENTER+gameid,redisUtil.hmget(RedisKeys.MAXENTER+gameid));

        //7.接口返回信息
        return new ApiResult(200,"缓存信息",map);
        //============================================================================高效代码编写============================================================================



        /*
        //------------------------------------------------------------------------------初次代码编写------------------------------------------------------------------------------

        // 1. 获取活动基本信息
        CardGame game = (CardGame) redisUtil.get(RedisKeys.INFO + gameid);

        //测试输出
        //System.out.println(game);

        if (game == null) {
            return new ApiResult<>(-1, "活动未开始", null);
        }

        // 2. 获取奖品映射信息（令牌列表）
        List<Object> tokenList = redisUtil.lrange(RedisKeys.TOKENS + gameid, 0, -1);

        // 3. 将令牌转换为时间戳日期格式作为Map的key，活动信息作为value
        // 小测试 - Map<String,CardProductDto> tokenMap = new HashMap<>();
        Map<String,Object> tokenMap = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //定义日期格式

        for (Object token : tokenList) {
            try {
                long tokenLong = Long.parseLong(token.toString());
                // 测试输出
                //System.out.println(tokenLong);

                // 解析时间戳,假设令牌前10位是时间戳
                Date tokenDate = new Date(tokenLong / 1000);
                // 测试输出
                //System.out.println(tokenDate);

                String formattedDate = sdf.format(tokenDate);
                // 测试输出
                //System.out.println(formattedDate);

                // 小测试 - tokenStr
                String tokenStr = String.valueOf(token);
                // 测试输出
                //System.out.println(tokenStr);
                //System.out.println(gameid);

                // 获取该令牌对应的奖品信息
                // 小测试 - CardProductDto cardProduct = (CardProductDto) redisUtil.get(RedisKeys.TOKEN + gameid + "_" + token); - 无法从redis中取出奖品信息
                // 注意事项:在redis中使用get取出的时候一定要注意key的名称一样,这里的tokenLong是因为token在set存储时候是转化为了字符串格式的!!!
                Object test = redisUtil.get(RedisKeys.TOKEN + gameid + "_" + tokenLong);

                //测试输出
                //System.out.println(cardProduct);
                //System.out.println(test);

                // 将日期字符串作为key,令牌对应的奖品信息作为value(假设基础信息为奖品信息,这里你可以替换为token本身)
                // 小测试 - tokenMap.put(formattedDate, cardProduct);
                tokenMap.put(formattedDate, test);

            } catch (NumberFormatException e) {
                // 处理异常,忽略无效令牌
                e.printStackTrace();
            }
        }

        // 4. 获取活动策略信息（最大中奖次数和最大参与次数）
        Map<Object, Object> maxGoals = redisUtil.hmget(RedisKeys.MAXGOAL + gameid);
        Map<Object, Object> maxEnters = redisUtil.hmget(RedisKeys.MAXENTER + gameid);

        // 5. 返回信息：包含活动基本信息、奖品映射信息、策略信息
        Map<String, Object> result = new HashMap<>();
        result.put("game", game);  // 活动基本信息
        result.put("tokens", tokenMap);  // 令牌映射信息, key 为日期字符串, value 为令牌对应的基础信息
        result.put("maxGoals", maxGoals);  // 每个等级的最大中奖次数
        result.put("maxEnters", maxEnters);  // 每个等级的最大参与次数

        return new ApiResult<>(1, "成功", result);

        //------------------------------------------------------------------------------初次代码编写------------------------------------------------------------------------------
         */

    }
}
//修改代码，使ActController类的info方法，要求tokens返回时转化为map类型同maxGoals的返回类型类似，但是其中tokens为group，时间戳转化的日期格式表示为key，value值为string类型的令牌对应活动的基础信息

/*
------------------------------------------------------开发文档要求-------------------------------------------------------
## 6.1 前置知识

**注意事项：**
本接口是并发度最高的一个点
设计与编码最好做到只与Redis打交道，而不与Mysql交互

**中奖后的处理：**
异步化，交给消息队列

**小提示：**
抽奖流程里的（判断最大中奖次数、取令牌-判断-放回令牌）已经写好了lua脚本：tokenCheck.lua（可以阅读脚本内容，有详细注释）
api模块下有个LuaScript，可以协助完成Redis里Lua脚本的调用，返回值：查看方法上的详细注释
可以用返回的令牌去redis里找vlue拿真实的奖品

RabbitMq传输对象的时候，可以使用FastJson将对象转为字符串后传输，可以避免很多问题
接收到消息后，再转回对应对象。

## 6.2 任务清单

抽奖业务逻辑 - 完成ActController类的act方法编写 - 完成抽奖接口并提交git
中奖后的异步消息处理 - 完成PrizeGameReceiver类和PrizeHitReceiver类的代码 - 完成中奖后异步信息消费端代码
------------------------------------------------------开发文档要求-------------------------------------------------------

------------------------------------------------------设计文档要求-------------------------------------------------------
### 3.2.2 并发与原子性
抽令牌-判断令牌-放回令牌业务下沉到lua脚本，如果直接在Java层实现会有并发问题：

**备注：更理想的状况是抽奖接口的代码完全用lua实现，但调试不方便，权衡下只对抽奖令牌这里做lua处理**
local token = redis.call('lpop',KEYS[1])
local curtime = tonumber(KEYS[2])

if token ~= false then
    if ( tonumber(token)/1000 > tonumber(KEYS[2]) ) then
       redis.call('lpush',KEYS[1],token)
       return 1
    else
       return tonumber(token)
    end
else
    return 0
end

调用方法：
Long token = luaScript.tokenCheck("game_"+gameid,String.valueOf(new Date().getTime()));
if(token == 0){
    return new ApiResult(-1,"奖品已抽光",null);
}else if(token == 1){
    return new ApiResult(0,"未中奖",null);
}else{
    //token有效，中奖！
}


## 3.3中奖处理
### 3.3.1 rabbit配置
RabbitConfig实现rabbit的队列与消费消息配置

### 3.3.2 异步处理
抽奖接口中涉及到两个队列：
prize_queue_play：用户参加的活动通过该队列投放
prize_queue_hit：用户中奖后的信息及中的奖品通过该队列投放

### 3.3.3 msg模块消费
在msg项目里实现processMessage方法，完成消息接收和入库
------------------------------------------------------设计文档要求-------------------------------------------------------
 */