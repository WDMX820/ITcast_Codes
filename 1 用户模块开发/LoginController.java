package com.itheima.prize.api.action;
//这段Java代码定义了一个用于处理登录和退出功能的控制器类 LoginController，位于包 com.itheima.prize.api.action 下

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;  // MyBatis Plus 的条件查询
import com.itheima.prize.commons.config.RedisKeys;  // Redis 键值配置
import com.itheima.prize.commons.db.entity.CardUser;  // 数据库实体类 CardUser
import com.itheima.prize.commons.db.mapper.CardUserMapper;  // 数据库实体类 CardUser 对应的 Mapper 接口
import com.itheima.prize.commons.db.service.CardUserService;  // 服务层接口 CardUserService
import com.itheima.prize.commons.utils.ApiResult;  // API 结果封装类 ApiResult
import com.itheima.prize.commons.utils.PasswordUtil;  // 密码工具类 PasswordUtil
import com.itheima.prize.commons.utils.RedisUtil;  // Redis 工具类 RedisUtil
// Swagger 注解用于 API 文档生成
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.models.Swagger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
// Spring Web 相关的注解
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
//import javax.smartcardio.Card;
import javax.swing.*;
import javax.xml.crypto.Data;
import java.util.List;

//控制器类定义
@RestController  //表明这是一个 RESTful风格的控制器,返回的数据通常是 JSON 格式
@RequestMapping(value = "/api")  //指定该控制器处理的所有请求的前缀路径为 /api
@Api(tags = {"登录模块"})  //使用 Swagger 注解，标记该控制器属于“登录模块”
public class LoginController {
    @Autowired
    private CardUserService userService;  //自动装配 CardUserService 服务层对象

    @Autowired
    private RedisUtil redisUtil;  //自动装配 RedisUtil 工具类对象

    // 登录接口
    @PostMapping("/login")  //处理 POST 请求，路径为 /api/login。
    @ApiOperation(value = "登录")  //Swagger 注解，描述该接口的功能是“登录”
    @ApiImplicitParams({
            @ApiImplicitParam(name="account",value = "用户名",required = true),
            @ApiImplicitParam(name="password",value = "密码",required = true)
    })  //Swagger 注解，描述接口的参数，包括用户名和密码，都是必填项
    public ApiResult login(HttpServletRequest request, @RequestParam String account,@RequestParam String password) {



        //============================================================================高效代码编写============================================================================

        //通过 redisUtil.get() 从 Redis 中获取用户的登录错误次数。Redis 使用键 RedisKeys.USERLOGINTIMES + account 存储这个值。
        //如果 errortimes 不为 null 且次数大于等于 5，说明用户输入密码错误超过 5 次，返回提示信息：“密码错误5次，请5分钟后再登录”。
        Integer errortimes = (Integer) redisUtil.get(RedisKeys.USERLOGINTIMES+account);
        if (errortimes != null && errortimes >= 5){
            return new ApiResult(0, "密码错误5次，请5分钟后再登录",null);
        }

        //使用 QueryWrapper 构造查询条件，根据用户名（uname）和经过加密的密码（passwd）进行匹配。
        //调用 userService.list(wrapper) 查询符合条件的用户列表。
        QueryWrapper<CardUser> wrapper = new QueryWrapper<>();
        wrapper.eq("uname",account).eq("passwd",PasswordUtil.encodePassword(password));
        List<CardUser> users = userService.list(wrapper);

        //如果查询到符合条件的用户列表（users 不为空且 size > 0），表示登录成功。
        //从查询到的用户列表中获取第一个用户，并将敏感信息（密码 passwd 和身份证 idcard）设为 null，避免这些信息泄露。
        //将用户信息保存到 session 中，返回一个包含用户信息的 ApiResult，表示登录成功。
        if (users != null && users.size() > 0) {
            CardUser user = users.get(0);
            //信息脱敏，不要将敏感信息带入session以免其他接口不小心泄露到前台
            user.setPasswd(null);
            user.setIdcard(null);
            HttpSession session = request.getSession();
            session.setAttribute("user",user);
            return new ApiResult(1, "登录成功",user);
        } else {
            //如果用户登录失败（users 为空或长度为 0），则调用 redisUtil.incr() 增加该用户的登录错误次数。
            //同时使用 redisUtil.expire() 设置该计数的过期时间为 5 分钟（60 秒 * 5）。
            //返回一个 ApiResult，提示“账户名或密码错误”。
            //错误计数，5次锁定5分钟
            redisUtil.incr(RedisKeys.USERLOGINTIMES+account,1);
            redisUtil.expire(RedisKeys.USERLOGINTIMES+account,60 * 5);
            return new ApiResult(0, "账户名或密码错误",null);
        }

        //============================================================================高效代码编写============================================================================



        /*
        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------

        //1.错误次数检查：使用 Redis 存储用户的错误登录次数，如果超过5次，返回错误提示并冻结5分钟。
        // 获取当前用户的错误次数
        //System.out.println(account);   //测试account是否传入
        //System.out.println(password);   //测试password是否传入
        String errorKey = RedisKeys.USERLOGINTIMES + account;
        Integer errorCount = (Integer) redisUtil.get(errorKey);
        if (errorCount != null && errorCount >= 5) {
            // 如果错误次数超过5次，冻结5分钟
            return new ApiResult(0, "密码错误5次，请5分钟后再登录",null);
        }
        //System.out.println(account);   //测试account是否传入
        //System.out.println(password);   //测试password是否传入

        //2.用户查询：通过 MyBatis Plus 的 QueryWrapper 查询用户信息。
        // 查询用户信息
        QueryWrapper<CardUser> queryWrapper = new QueryWrapper<>();  //创建了一个 QueryWrapper 对象 queryWrapper
        queryWrapper.eq("uname", account);   //通过 eq 方法设置了查询条件，即查找 uname 字段值等于变量 account 的用户记录。
        CardUser user = userService.getOne(queryWrapper); //userService 的 getOne 方法根据这个查询条件获取一个 CardUser 类型的用户对象，并将其存储在变量 user 中

        //测试输出
        //System.out.println(password);
        //System.out.println(user.getPasswd());

        //3.密码验证：使用 PasswordUtil.verify 方法验证用户输入的密码与数据库中的密码是否匹配。
        // 验证密码
        if (user != null && PasswordUtil.verify(user.getPasswd(), password)) {
            // 登录成功，清除错误次数
            redisUtil.del(errorKey);

            // 创建一个新的用户信息对象,不包含passwd和idcard这两个敏感字段,用于登陆成功后返回数据
            CardUser userForApi = new CardUser();
            userForApi.setId(user.getId()); // 需要输出的Id字段
            userForApi.setUname(user.getUname()); // 需要输出的Uname字段
            userForApi.setPic(user.getPic());
            userForApi.setRealname(user.getRealname());
            userForApi.setPhone(user.getPhone());
            userForApi.setLevel(user.getLevel());
            userForApi.setCreatetime(user.getCreatetime());
            userForApi.setUpdatetime(user.getUpdatetime());

            //4.Session 设置：登录成功后，将用户信息存储在 Session 中，并清除错误次数。
            // 设置session
            HttpSession session = request.getSession();
            session.setAttribute("user", user);

            return new ApiResult(1, "登录成功",userForApi);
        } else {
            // 登录失败，增加错误次数
            if (errorCount == null) {
                errorCount = 0;
            }
            redisUtil.set(errorKey, errorCount + 1, 300); // 错误次数保存5分钟

            return new ApiResult(0, "账户名或密码错误", null);
        }

        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------
        */

    }  //定义登录方法，接收 HTTP 请求对象和用户名、密码参数。




    // 退出接口
    @GetMapping("/logout")  //处理 GET 请求，路径为 /api/logout。
    @ApiOperation(value = "退出")  //Swagger 注解，描述该接口的功能是“退出”
    public ApiResult logout(HttpServletRequest request) {



        //============================================================================高效代码编写============================================================================

        HttpSession session = request.getSession();  // 获取当前的 HttpSession 对象
        if (session != null) {  // 检查 session 是否为空（如果当前会话存在）
            session.invalidate();  // 使会话失效，清除与用户相关的所有会话数据
        }
        return new ApiResult(1, "退出成功", null);  // 返回 ApiResult，状态码为 1 表示成功，消息为 "退出成功"，没有其他数据

        //============================================================================高效代码编写============================================================================



        /*
        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------

        HttpSession session = request.getSession(false);  //5.退出功能：清除 Session，实现用户退出。
        if (session != null) {
            session.invalidate();
        }
        return new ApiResult(1, "退出成功",null);

        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------
         */

    }  ///定义退出方法，接收 HTTP 请求对象。
}






/*
代码注意事项
    确保 PasswordUtil 类中有 verify 方法用于密码验证。
    确保 RedisUtil 类中有 get 和 set 方法用于操作 Redis。
    确保 ApiResult 类用于封装接口返回结果。
*/



/*
## 3.1 前置知识
**相关文档：《接口文档.xlsx》**

**用户基本设计：**
用户的密码为Md5密文存储，所以验证需要加密后再匹配数据库

**登录及注意事项：**
登录需要验证错误操作次数，超过5次后，冻结5分钟直接返回错误提示
可以借助redis实现

**密码安全性问题：**
用户信息请求接口返回的数据里，需要脱敏，去掉账户密码等敏感信息

**分布式环境下的session方案：**
项目已经集成spring redis session，登录成功后直接使用session来设置一个user属性，存储当前用户信息
后续的拦截将以session是否存在user作为是否登录的标记
```java
session.setAttribute("user",user);
```

**接口规范：**
所有接口返回json体必须是 com.itheima.prize.commons.utils.ApiResult
详细内容可以查看内部注释
接口详细内容参考**《接口文档.xlsx》**

**分页实现方案：**
Mybatis-Plus可以轻松实现分页
分页的返回数据使用PageBean对象来包装
一个参考案例如下：
```java
QueryWrapper<CardGame> gameQueryWrapper = new QueryWrapper<>();
Page<CardGame> page = gameService.page(new Page<>(curpage,limit),gameQueryWrapper);
return new ApiResult(1,"成功",new PageBean<CardGame>(page));
```

**便捷的查询：**
有些视图可能会帮到你
view_card_user_ hit
view_game_curinfo
view_game_hitnum
view game__product



## 3.2 任务清单
完成用户模块的代码编写
完成登录模块的代码编写

## 3.3 完成标准
提示：commons里的实体类可以作为data
### 3.3.1 实现Login代码
接口自测：【提交结果截图】

###3.3.2 实现User代码
接口自测：【提交结果截图】
用户信息：直接从session获取user属性即可
参与的活动列表：【提交结果截图】
在完成活动抽奖接口之前，无数据，可以在card_user_hit中手工录入测试数据检验接口
查询结果可以使用视图：view_card_user_hit

### 3.3.3 前后联调
在前端页面实现登录、退出、个人中心展示 【提交结果截图】

 */