package com.itheima.prize.api.action;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;  //MyBatis Plus的查询条件构造器
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;  //MyBatis Plus的分页插件
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.mapper.CardGameMapper;  //CardGame实体类的接口
import com.itheima.prize.commons.db.mapper.GameLoadMapper;  //GameLoad接口
import com.itheima.prize.commons.db.mapper.ViewCardUserHitMapper;  //ViewCardUserHit实体类的接口
import com.itheima.prize.commons.db.service.*;
import com.itheima.prize.commons.utils.ApiResult;  //工具类
import com.itheima.prize.commons.utils.PageBean;  //工具类
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

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController  //表示这是一个RESTful风格的控制器，返回的数据通常是JSON格式。
@RequestMapping(value = "/api/game")  //定义了该控制器的根路径为/api/game。
@Api(tags = {"活动模块"})  //使用Swagger注解，标记这是一个活动模块的API。
public class GameController {
    //通过@Autowired注解，将GameLoadService、CardGameService和ViewCardUserHitService注入到控制器中，以便在方法中使用这些服务类。
    @Autowired
    private GameLoadService loadService;
    @Autowired
    private CardGameService gameService;
    @Autowired
    private ViewCardUserHitService hitService;
    @Autowired
    private CardProductService productService;  // 新增的服务类
    @Autowired
    private CardGameProductService cardGameProductService;  // 新增的服务类



    //活动列表接口
    //定义了一个GET请求的接口，路径为/list/{status}/{curpage}/{limit}，其中{status}、{curpage}和{limit}是路径参数。
    @GetMapping("/list/{status}/{curpage}/{limit}")
    @ApiOperation(value = "活动列表")  //使用Swagger注解，标记该接口的功能为“活动列表”。
    @ApiImplicitParams({
            @ApiImplicitParam(name="status",value = "活动状态（-1=全部，0=未开始，1=进行中，2=已结束）",example = "-1",required = true),
            @ApiImplicitParam(name = "curpage",value = "第几页",defaultValue = "1",dataType = "int", example = "1",required = true),
            @ApiImplicitParam(name = "limit",value = "每页条数",defaultValue = "10",dataType = "int",example = "3",required = true)
    })  //定义了接口的参数，包括status、curpage和limit，并描述了每个参数的含义和示例值。
    public ApiResult list(@PathVariable int status,@PathVariable int curpage,@PathVariable int limit) {



        //============================================================================高效代码编写============================================================================

        Date now = new Date();  // 获取当前时间
        QueryWrapper<CardGame> gameQueryWrapper = new QueryWrapper<>();  // 创建 QueryWrapper，用于构建查询条件
        switch (status) {  // 根据传入的状态值选择不同的查询条件
            case -1:
                // 查全部：不添加任何过滤条件，查询所有游戏
                break;
            case 0:
                // 未开始：查询开始时间大于当前时间的活动（即未来的活动）
                gameQueryWrapper.gt("starttime", now);
                break;
            case 1:
                // 进行中：查询开始时间小于等于当前时间且结束时间大于当前时间的活动（即正在进行的活动）
                gameQueryWrapper.le("starttime", now).gt("endtime", now);
                break;
            case 2:
                // 已结束：查询结束时间小于等于当前时间的活动（即已经结束的活动）
                gameQueryWrapper.le("endtime", now);
                break;
        }
        gameQueryWrapper.orderByDesc("starttime");  // 按照开始时间降序排序
        Page<CardGame> page = gameService.page(new Page<>(curpage, limit), gameQueryWrapper);  // 使用分页服务进行查询，传入当前页和每页记录数
        return new ApiResult(1, "成功", new PageBean<CardGame>(page));  // 返回带分页结果的 ApiResult 对象，状态码 1 表示成功

        //============================================================================高效代码编写============================================================================



        /*
        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------

        QueryWrapper<CardGame> queryWrapper = new QueryWrapper<>();
        if (status != -1) {
            queryWrapper.eq("status", status); //通过 eq 方法设置了查询条件，即查找 status 字段值等于变量 status 的用户记录。
        }
        Page<CardGame> page = new Page<>(curpage, limit);
        Page<CardGame> cardGamePage = gameService.page(page, queryWrapper);
        return new ApiResult(1,"成功",new PageBean<>(cardGamePage));

        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------
         */

    }  //定义了一个方法list，接收三个路径参数status、curpage和limit，返回一个ApiResult对象。




    //活动信息接口
    //定义了一个GET请求的接口，路径为/info/{gameid}，其中{gameid}是路径参数。
    @GetMapping("/info/{gameid}")
    @ApiOperation(value = "活动信息")  //使用Swagger注解，标记该接口的功能为“活动信息”。
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })  //定义了接口的参数gameid，并描述了其含义和示例值。
    public ApiResult<CardGame> info(@PathVariable int gameid) {


        //============================================================================高效代码编写============================================================================

        //gameService.getById(gameid)：调用 gameService 的 getById() 方法，根据游戏的唯一标识 gameid 从数据库中查询并返回该游戏的详细信息。
        //返回：构造一个 ApiResult 对象，状态码为 1，表示查询成功，返回的内容是通过 gameid 获取的游戏对象。
        return new ApiResult(1,"成功",gameService.getById(gameid));

        //============================================================================高效代码编写============================================================================



        /*
        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------

        CardGame cardGame = gameService.getById(gameid);
        if (cardGame != null) {
            return new ApiResult(1,"成功",cardGame);
        } else {
            return new ApiResult(0,"活动不存在",null);
        }

        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------
         */


    }  //定义了一个方法info，接收一个路径参数gameid，返回一个ApiResult<CardGame>对象。




    //奖品信息接口
    //定义了一个GET请求的接口，路径为/products/{gameid}，其中{gameid}是路径参数。
    @GetMapping("/products/{gameid}")
    @ApiOperation(value = "奖品信息")  //使用Swagger注解，标记该接口的功能为“奖品信息”。
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })  //定义了接口的参数gameid，并描述了其含义和示例值。
    public ApiResult<List<CardProductDto>> products(@PathVariable int gameid) {

        //============================================================================高效代码编写============================================================================

        //loadService.getByGameId(gameid)：调用 loadService 的 getByGameId() 方法，根据 gameid 查询与游戏相关的加载信息，通常可能是一些游戏配置或状态数据。
        //返回：构造一个 ApiResult 对象，状态码为 1，表示成功，返回的内容是与 gameid 对应的加载信息。
        return new ApiResult(1,"成功",loadService.getByGameId(gameid));

        //============================================================================高效代码编写============================================================================


        /*
        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------

        // 1. 查询 CardGameProduct 表中 gameid 等于传入 gameid 的用户记录
        QueryWrapper<CardGameProduct> queryWrapper = new QueryWrapper<>();  //创建一个QueryWrapper对象，用于构建查询条件。
        queryWrapper.eq("gameid", gameid);  //通过 eq 方法设置了查询条件，即查找 gameid 字段值等于变量 gameid 的用户记录。
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
        List<CardProductDto> productDtos = cardProducts.stream()
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

        // 5. 返回结果
        return new ApiResult<>(1, "成功", productDtos);

        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------
         */

    }  //定义了一个方法products，接收一个路径参数gameid，返回一个ApiResult<List<CardProductDto>>对象。




    //中奖列表接口
    //定义了一个GET请求的接口，路径为/hit/{gameid}/{curpage}/{limit}，其中{gameid}、{curpage}和{limit}是路径参数。
    @GetMapping("/hit/{gameid}/{curpage}/{limit}")
    @ApiOperation(value = "中奖列表")  //使用Swagger注解，标记该接口的功能为“中奖列表”。
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",dataType = "int",example = "1",required = true),
            @ApiImplicitParam(name = "curpage",value = "第几页",defaultValue = "1",dataType = "int", example = "1",required = true),
            @ApiImplicitParam(name = "limit",value = "每页条数",defaultValue = "10",dataType = "int",example = "3",required = true)
    })  //定义了接口的参数gameid、curpage和limit，并描述了每个参数的含义和示例值。
    public ApiResult<PageBean<ViewCardUserHit>> hit(@PathVariable int gameid,@PathVariable int curpage,@PathVariable int limit) {



        //============================================================================高效代码编写============================================================================

        //使用 QueryWrapper 构造查询条件，过滤出所有与 gameid 匹配的用户击中记录。
        QueryWrapper<ViewCardUserHit> wrapper = new QueryWrapper<>();
        wrapper.eq("gameid",gameid);
        //调用 hitService.page() 方法，执行分页查询，传入当前页 curpage 和每页显示的记录数 limit，返回一个分页对象。
        Page<ViewCardUserHit> page = hitService.page(new Page<ViewCardUserHit>(curpage,limit),wrapper);
        //构造 ApiResult 对象，状态码为 1，表示查询成功。查询结果被封装在 PageBean<ViewCardUserHit> 中返回，PageBean 是对分页结果的进一步封装，便于客户端处理。
        return new ApiResult(1, "成功",new PageBean<ViewCardUserHit>(page));

        //============================================================================高效代码编写============================================================================



        /*
        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------

        //查询
        QueryWrapper<ViewCardUserHit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("gameid", gameid);

        //分页显示
        Page<ViewCardUserHit> page = new Page<>(curpage, limit);
        Page<ViewCardUserHit> hitPage = hitService.page(page, queryWrapper);
        return new ApiResult(1,"成功",new PageBean<>(hitPage));

        //----------------------------------------------------------------------------初次代码编写----------------------------------------------------------------------------
         */


    }  //定义了一个方法hit，接收三个路径参数gameid、curpage和limit，返回一个ApiResult<PageBean<ViewCardUserHit>>对象。


}

/*
## 4.1 前置知识
**相关文档：《接口文档.xlsx》**

**便捷的查询：**
有些视图可能会帮到你
view_card_user_hit
view_game_curinfo
view_game_hitnum
view_game_product


## 4.2 任务清单
完成活动相关的业务代码，涉及到以下业务：
1、首页的活动列表接口
2、活动详情页里的活动信息接口
3、活动对应的奖品信息查询接口
4、活动对应的中奖列表接口

## 4.3 完成标准
### 4.3.1 完成基础代码编写
GameController
    list(int,int,int):ApiResult
    info(int):ApiResult<CardGame>
    products(int):ApiResult<List<CardProductDto>>
    hit(int,int,int):ApiResult<PageBean<ViewCardUserHit>>

### 4.3.2 完成代码自测
 【需要提交各个接口自测通过的截图】
活动列表：
活动信息：
活动包含的奖品信息：
活动中奖列表：

 */