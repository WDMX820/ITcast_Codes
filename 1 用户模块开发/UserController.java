package com.itheima.prize.api.action;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;  //MyBatis Plus的查询条件构造器
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;  //MyBatis Plus的分页插件
import com.itheima.prize.commons.db.entity.CardUser;  //CardUser实体类
import com.itheima.prize.commons.db.entity.CardUserDto;  //CardUserDto实体类
import com.itheima.prize.commons.db.entity.ViewCardUserHit;  //ViewCardUserHit实体类
import com.itheima.prize.commons.db.mapper.ViewCardUserHitMapper;  //Mapper接口
import com.itheima.prize.commons.db.service.GameLoadService;  //服务类
import com.itheima.prize.commons.db.service.ViewCardUserHitService;  //服务类
import com.itheima.prize.commons.utils.ApiResult;  //工具类
import com.itheima.prize.commons.utils.PageBean;  //工具类
import com.itheima.prize.commons.utils.RedisUtil;  //工具类
//Swagger注解以及Spring MVC的注解
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.List;

//这段代码定义了一个名为UserController的Spring MVC控制器类，位于com.itheima.prize.api.action包中。
//这个控制器类主要用于处理与用户相关的API请求，主要包含两个方法：info和hit。
@RestController  //Spring MVC的注解，表示这个类是一个控制器，并且所有的方法都直接返回数据而不是视图
@RequestMapping(value = "/api/user")  //用于映射HTTP请求到控制器的类或方法。
@Api(tags = {"用户模块"})  //Swagger的注解，用于给API分组，这里表示这个控制器属于“用户模块”
public class UserController {  //定义了UserController类

    @Autowired
    private RedisUtil redisUtil;  //自动注入Redis工具类，用于与Redis进行交互
    @Autowired
    private ViewCardUserHitService hitService;  //自动注入ViewCardUserHitService服务类，用于处理与ViewCardUserHit实体相关的业务逻辑。
    @Autowired
    private GameLoadService loadService;  //自动注入GameLoadService服务类，用于处理与游戏加载相关的业务逻辑。

    @GetMapping("/info")  //映射到/api/user/info的HTTP GET请求
    @ApiOperation(value = "用户信息")  //描述了该方法的功能，即获取用户信息。
    public ApiResult info(HttpServletRequest request) {



        //============================================================================高效代码编写============================================================================

        HttpSession session = request.getSession();   //从 HttpServletRequest 获取 HttpSession 对象，用来访问当前会话中的属性
        CardUser user = (CardUser) session.getAttribute("user");   //从 session 中获取名为 "user" 的属性，并将其转换为 CardUser 对象
        if (user == null) {   //如果 user 为 null，说明会话中没有登录的用户信息（用户未登录或会话超时）
            return new ApiResult(0, "登录超时", null);   //返回 ApiResult 对象，包含错误代码 0，提示信息 "登录超时"，以及 null 数据
        } else {
            CardUserDto dto = new CardUserDto(user);   //如果 user 不为 null，将 user 转换为 CardUserDto 对象，CardUserDto 是用于传输数据的对象
            dto.setGames(loadService.getGamesNumByUserId(user.getId()));   //调用 loadService，通过用户 ID 获取该用户的游戏数量，并设置到 dto 对象中
            dto.setProducts(loadService.getPrizesNumByUserId(user.getId()));   //调用 loadService，通过用户 ID 获取该用户的奖品数量，并设置到 dto 对象中
            return new ApiResult(1, "成功", dto);   //返回 ApiResult 对象，包含成功代码 1，提示信息 "成功"，以及包含用户信息的 dto 对象
        }

        //============================================================================高效代码编写============================================================================



        /*
        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------

        HttpSession session = request.getSession();  //从HttpSession中获取user属性
        CardUser user = (CardUser) session.getAttribute("user");  //并将其转换为CardUserDto对象
        if (user == null) {  //如果user为null，则返回一个错误信息，表示用户未登录
            return new ApiResult(0, "登录超时",null);
        }  //如果user不为null，则返回包含用户信息的ApiResult对象

        // 将 CardUser 对象 user 转换为 CardUserDto 对象 userDto
        CardUserDto userDto = new CardUserDto(user);

        // 查询不重复的 gameid 数量
        QueryWrapper<ViewCardUserHit> gameIdQueryWrapper = new QueryWrapper<>();  //使用 QueryWrapper 构建查询条件
        //通过 select("DISTINCT gameid") 选择不重复的 gameid 字段，并使用 eq("userid", user.getId()) 设置查询条件。
        gameIdQueryWrapper.select("DISTINCT gameid").eq("userid", user.getId());
        //调用 hitService.listObjs(gameIdQueryWrapper) 获取不重复的 gameid 列表，并通过 size() 方法获取数量
        List<Object> gameIdList = hitService.listObjs(gameIdQueryWrapper);
        int gameIdCount = gameIdList.size();

        // 查询 productid 数量
        QueryWrapper<ViewCardUserHit> productIdQueryWrapper = new QueryWrapper<>();  //使用 QueryWrapper 构建查询条件
        //选择 productid 字段的总和，并设置 userid 条件(允许productid重复,只记录product的数量)
        productIdQueryWrapper.select("COUNT(productid)").eq("userid", user.getId());
        //调用 hitService.getObj(productIdQueryWrapper, o -> (Long) o) 获取 productid 数量的总和，并将其转换为整数。
        Long productIdCount = hitService.getObj(productIdQueryWrapper, o -> (Long) o);
        int productIdCountInt = productIdCount != null ? productIdCount.intValue() : 0;

        // 将查询结果赋值到 userDto 对象中
        userDto.setGames(gameIdCount);
        userDto.setProducts(productIdCountInt);

        // 创建一个新的用户信息对象userForApi1,不包含passwd和idcard这两个敏感字段, 加入games和products字段, 用于查看个人信息
        CardUserDto userForApi1 = new CardUserDto();
        userForApi1.setId(userDto.getId()); // 需要输出的Id字段
        userForApi1.setUname(userDto.getUname()); // 需要输出的Uname字段
        userForApi1.setPic(userDto.getPic());
        userForApi1.setRealname(userDto.getRealname());
        userForApi1.setPhone(userDto.getPhone());
        userForApi1.setLevel(userDto.getLevel());
        userForApi1.setCreatetime(userDto.getCreatetime());
        userForApi1.setUpdatetime(userDto.getUpdatetime());
        userForApi1.setGames(userDto.getGames());
        userForApi1.setProducts(userDto.getProducts());

        return new ApiResult(1, "成功",userForApi1);

        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------
         */


    } // info 方法




    @GetMapping("/hit/{gameid}/{curpage}/{limit}")  //映射到/api/user/hit/{gameid}/{curpage}/{limit}的HTTP GET请求，其中{gameid}、{curpage}和{limit}是路径变量。
    @ApiOperation(value = "我的奖品")  //描述了该方法的功能，即获取用户的奖品信息。
    @ApiImplicitParams({
            //描述了gameid参数，表示活动ID，数据类型为整数，示例值为1，且为必填项
            @ApiImplicitParam(name="gameid",value = "活动id（-1=全部）",dataType = "int",example = "1",required = true),
            //描述了curpage参数，表示当前页码，默认值为1，数据类型为整数，示例值为1
            @ApiImplicitParam(name = "curpage",value = "第几页",defaultValue = "1",dataType = "int", example = "1"),
            //描述了limit参数，表示每页条数，默认值为10，数据类型为整数，示例值为3
            @ApiImplicitParam(name = "limit",value = "每页条数",defaultValue = "10",dataType = "int",example = "3")
    }) //定义了方法参数的详细信息。
    public ApiResult hit(@PathVariable int gameid, @PathVariable int curpage, @PathVariable int limit, HttpServletRequest request) {



        //============================================================================高效代码编写============================================================================

        HttpSession session = request.getSession();  // 获取 HttpSession 对象，用来访问当前会话中的用户信息
        CardUser user = (CardUser) session.getAttribute("user");  // 从会话中获取 "user" 属性，并转换为 CardUser 对象
        QueryWrapper<ViewCardUserHit> wrapper = new QueryWrapper<>();  // 创建 QueryWrapper，用于构建数据库查询条件
        wrapper.eq("userid", user.getId());  // 设置查询条件：userid 等于当前用户的 ID

        if (gameid != -1) {  // 如果传入的 gameid 不等于 -1，意味着需要根据具体游戏 ID 进行过滤
            wrapper.eq("gameid", gameid);  // 将 gameid 作为查询条件添加到 wrapper 中
        }

        Page<ViewCardUserHit> all = hitService.page(new Page(curpage, limit), wrapper);  // 调用 hitService，通过分页信息 (curpage, limit) 和查询条件 (wrapper) 获取分页结果
        return new ApiResult(1, "成功", new PageBean<ViewCardUserHit>(all));  // 返回包含分页数据的 ApiResult 对象，状态码 1 表示成功

        //============================================================================高效代码编写============================================================================



        /*
        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------

        HttpSession session = request.getSession();  //从HttpSession中获取user属性
        CardUser user = (CardUser) session.getAttribute("user");  //并将其转换为CardUserDto对象
        if (user == null) {  //如果user为null，则返回一个错误信息，表示用户未登录
            return new ApiResult(0, "登录超时",null);
        }

        QueryWrapper<ViewCardUserHit> queryWrapper = new QueryWrapper<>();  //创建一个QueryWrapper对象，用于构建查询条件。
        queryWrapper.eq("userid", user.getId());  //通过 eq 方法设置了查询条件，即查找 userid 字段值等于变量 user.getId() 的用户记录。

        if (gameid != -1) { //如果gameid不等于-1，则添加gameid的查询条件
            queryWrapper.eq("gameid", gameid);  //通过 eq 方法设置了查询条件，即查找 gameid 字段值等于变量 gameid 的用户记录。
        }

        //使用MyBatis Plus的分页插件进行分页查询，并将结果封装到PageBean中
        Page<ViewCardUserHit> page = new Page<>(curpage, limit);
        Page<ViewCardUserHit> resultPage = hitService.page(page, queryWrapper);
        //测试确保 hitService.page 方法正确实现，并且没有抛出异常,
        //可以尝试在 hitService.page 方法中添加日志，查看具体执行情况。
        if (resultPage == null) {
            // 记录日志或抛出异常
            throw new RuntimeException("分页查询结果为空");
        }
        return new ApiResult(1,"成功",new PageBean<>(resultPage));

        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------
         */

    } // hit 方法
}







//3.3.2 实现User代码
//接口自测：【提交结果截图】
//用户信息：直接从session获取user属性即可

//参与的活动列表：【提交结果截图】
//在完成活动抽奖接口之前，无数据，可以在card_user_hit中手工录入测试数据检验接口
//查询结果可以使用视图：view_card_user_hit