package com.itheima.prize.commons.db.entity;
//这段代码定义了一个名为 CardUserDto 的类，该类继承自 CardUser 类。
//CardUserDto 类主要用于在 CardUser 的基础上添加一些额外的字段和方法。
import org.springframework.beans.BeanUtils;

public class CardUserDto extends CardUser {  //定义了一个名为 CardUserDto 的类并将继承 CardUser 类的所有属性和方法。
    //带参数的构造方法，用于将 CardUser 对象的属性复制到当前的 CardUserDto 对象中。
    //BeanUtils.copyProperties(user, this) 方法会将 user 对象的所有属性复制到当前对象中。
    public CardUserDto(CardUser user){
        BeanUtils.copyProperties(user,this);
    }
    //参构造方法，调用了父类 CardUser 的无参构造方法。
    public CardUserDto(){
        super();
    }

    //参与的活动数，中奖数
    //这两个字段 games 和 products 是 CardUserDto 类新增的字段，分别表示用户参与的活动数和中奖数
    private Integer games,products;

    //Getter 和 Setter 方法
    public Integer getGames() {
        return games;
    }

    public void setGames(Integer games) {
        this.games = games;
    }

    public Integer getProducts() {
        return products;
    }

    public void setProducts(Integer products) {
        this.products = products;
    }
}
