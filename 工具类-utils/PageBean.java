//分页统一返回此类型
package com.itheima.prize.commons.utils;
//这段代码定义了一个名为 PageBean 的泛型类，用于封装分页查询的结果。
//它主要用于与前端进行交互，提供分页查询的相关信息
/**
 * 分页bean: 简单的类注释，说明该类是一个分页的 Java Bean
 */

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;  //MyBatis-Plus 的分页插件 Page
import io.swagger.annotations.ApiModel;  //Swagger 的注解 ApiModel
import io.swagger.annotations.ApiModelProperty;  //Swagger 的注解 ApiModelProperty

import java.util.List;  // Java 的 List 接口

@ApiModel("分页信息")  //使用 Swagger 的 @ApiModel 注解，用于描述该类是一个分页信息的模型
public class PageBean<T> {  //定义了一个泛型类 PageBean，其中 T 表示分页数据的具体类型。
    @ApiModelProperty(value = "当前页，1开始")  //使用 Swagger 的 @ApiModelProperty 注解，用于描述类的属性。
    private long currentPage = 1;  //当前页码，默认值为 1
    @ApiModelProperty(value = "每页条数，默认10")
    private long pageSize = 10;  //每页的条数，默认值为 10
    @ApiModelProperty(value = "总条数")
    private long totalNum;  //总条数
    @ApiModelProperty(value = "是否有下一页")
    private Integer isMore;  //是否有下一页，0 表示没有，1 表示有
    @ApiModelProperty(value = "总页数")
    private long totalPage;  //总页数
    @ApiModelProperty(value = "开始索引")
    private long startIndex;  //当前页的起始索引
    @ApiModelProperty(value = "本页数据")
    private List<T> items;  //当前页的数据列表

    public PageBean() {
        super();
    }  //无参构造方法，调用了父类的构造方法。

    public PageBean(long currentPage, long pageSize, long totalNum, List<T> data) {  //带参构造方法，用于手动设置分页信息。
        super();
        this.currentPage = currentPage;  //当前页码
        this.pageSize = pageSize;  //每页条数
        this.totalNum = totalNum;  //总条数
        this.totalPage = Math.toIntExact((this.totalNum + this.pageSize - 1) / this.pageSize);  //计算总页数
        this.startIndex = (this.currentPage-1)*this.pageSize;  //当前页的起始索引
        this.isMore = this.currentPage >= this.totalPage?0:1;  //判断是否有下一页
        this.items=data;
    }

    public PageBean(Page<T> page) {  //带参构造方法，用于从 MyBatis-Plus 的 Page 对象中提取分页信息。
        this.currentPage = page.getCurrent();  //获取当前页码
        this.pageSize = page.getSize();  //获取每页条数
        this.totalNum = page.getTotal();  //获取总条数
        this.totalPage = page.getPages();  //获取总页数
        this.startIndex = (this.currentPage-1)*this.pageSize;  //当前页的起始索引
        this.isMore = page.hasNext()?1:0;  ///判断是否有下一页
        this.items=page.getRecords();  //获取当前页的数据列表
    }

    public long getCurrentPage() {
        return currentPage;
    }

    public long getPageSize() {
        return pageSize;
    }

    public long getTotalNum() {
        return totalNum;
    }

    public Integer getIsMore() {
        return isMore;
    }

    public long getTotalPage() {
        return totalPage;
    }

    public long getStartIndex() {
        return startIndex;
    }

    public List<T> getItems() {
        return items;
    }
}