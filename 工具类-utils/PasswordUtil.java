package com.itheima.prize.commons.utils;
//这段 Java 代码定义了一个名为 PasswordUtil 的工具类，主要用于密码的加密处理。
import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * 密码工具类
 */
public class PasswordUtil { //定义了一个公共类 PasswordUtil

	/**
	 * 验证用户输入的密码与数据库中的 MD5 密码是否匹配
	 *
	 * @param rawPassword     用户输入的原始密码
	 * @param md5Password     数据库中存储的 MD5 密码
	 * @return 如果密码匹配返回 true，否则返回 false
	 */
	public static boolean verify(String rawPassword, String md5Password) {
		// 将用户输入的密码进行 MD5 加密
		//测试输出
		//System.out.println(rawPassword);
		//System.out.println(md5Password);
		//System.out.println(md5(md5Password));
		// 比较加密后的密码与数据库中的密码是否一致
		String md5md5Password = md5(md5Password);
		//此处不能使用 == , 使用.equals字符串方法:检查通过MD5加密后的密码（md5Password）是否与原始密码（rawPassword）相等
		return md5md5Password.equals(rawPassword);
	}

//1.MD5 加密方法：md5(String inputStr) 方法用于将输入的字符串进行 MD5 加密。
	//定义了一个私有方法 md5，用于对输入的字符串进行 MD5 加密。
	private static String md5(String inputStr) {
		BigInteger bigInteger = null; //初始化一个 BigInteger 对象
		try {
			MessageDigest md = MessageDigest.getInstance("MD5"); //获取 MD5 消息摘要实例。
			byte[] inputData = inputStr.getBytes();  //将输入字符串转换为字节数组。
			md.update(inputData);  //更新消息摘要。
			bigInteger = new BigInteger(md.digest());  //将消息摘要的结果转换为 BigInteger 对象。
		} catch (Exception e) {
			e.printStackTrace();
		}
		return bigInteger.toString(16);  //将 BigInteger 对象转换为十六进制字符串并返回。
	}


	/**
	 * 密码加密
	 */
	//密码加密方法：encodePassword(String password) 静态方法用于对密码进行加密。
	public static String encodePassword(String password) {
		return new PasswordUtil().md5(password); //创建 PasswordUtil 实例并调用 md5 方法对密码进行加密。
	}
	//主方法：main(String[] args) 方法用于测试密码加密功能。
	public static void main(String[] args) {
		System.out.println(encodePassword("123456"));  //调用 encodePassword 方法对字符串 "123456" 进行加密，并输出结果
	}

}

