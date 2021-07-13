package task;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import model.*;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import utils.PushUtils;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SingleTask implements Job {

	// 一些url
	public static final String LOGIN_URL = "https://zjuam.zju.edu.cn/cas/login?service=https%3A%2F%2Fhealthreport.zju.edu.cn%2Fa_zju%2Fapi%2Fsso%2Findex%3Fredirect%3Dhttps%253A%252F%252Fhealthreport.zju.edu.cn%252Fncov%252Fwap%252Fdefault%252Findex%26from%3Dwap";
	public static final String BASE_URL = "https://healthreport.zju.edu.cn/ncov/wap/default/index";
	public static final String SAVE_URL = "https://healthreport.zju.edu.cn/ncov/wap/default/save";
	public static final String EXECUTION_REG = "name=\"execution\" value=\"(.*?)\"";
	public static final String OLD_INFO_REG = "oldInfo: (\\{[^\\n]+\\})";
	public static final String DEF_REG = "def = (\\{[^\\n]+\\})";
	public static final String GET_PUB_KEY_URL = "https://zjuam.zju.edu.cn/cas/v2/getPubKey";
	public static final Integer MOVE_CODE = 302;
	// cookies
	public static List<HttpCookie> cookies = new ArrayList<>();
	public static LocalDateTime nextTime = null;
	
	public static final String USERNAME = "3180100138","3180100151"; // 学号
	public static final String PASSWORD = "rick890207","88913david"; // 密码


	@Override
	public void execute(JobExecutionContext context) {
		try{
			singleTask();
		}catch (Exception ex){
			log.error("", ex);
		}
	}


	public static void singleTask(){
		if(Objects.nonNull(nextTime)){
			// 判断当前时间是否已经超过设定时间，且不需要重试
			if(nextTime.isAfter(LocalDateTime.now()))
				return;
		}
		// 设置下一次打卡时间
		setNextTime();

		log.info("任务开始");

		// 浙大统一身份登陆
		boolean loginSuccess = login(USERNAME, PASSWORD);
		if(!loginSuccess){
			log.info("任务结束");
			return;
		}

		// 获取个人信息
		NovInfo info = getAndSetInfo();
		if(Objects.isNull(info)){
			log.info("任务结束");
			return;
		}

		// 提交打卡
		try {
			postInfo(info);
		} catch (IllegalAccessException e) {
			log.error("对象反射出错", e);
		}

		log.info("任务结束");
	}

	/**
	 * 统一身份认证
	 */
	public static boolean login(String username, String password) {
		log.info("尝试登录中, username: {}, password: {}", username, password);

		// 获取execution和cookies
		HttpResponse response = HttpRequest.get(LOGIN_URL)
				.execute();
		cookies = response.getCookies();
		LoginParam loginParam = new LoginParam();
		// 正则获取execution
		Matcher matcher = Pattern.compile(EXECUTION_REG).matcher(response.body());
		if (matcher.find()) {
			loginParam.setExecution(matcher.group(1));
		}

		// 获取pubKey和cookies
		response = HttpRequest.get(GET_PUB_KEY_URL)
				.cookie(cookies)
				.execute();
		cookies = response.getCookies();

		// 获取加密后的密码
		PubKey pubKey = JSONObject.parseObject(response.body(), PubKey.class);
		loginParam.setPassword(rsaEncrypt(password, pubKey.getExponent(), pubKey.getModulus()));
		loginParam.setUsername(username);
		loginParam.set_eventId("submit");

		// 发起请求，注意是以表单的格式提交
		response = HttpRequest.post(LOGIN_URL)
				.cookie(cookies)
				.form("username", loginParam.getUsername())
				.form("password", loginParam.getPassword())
				.form("execution", loginParam.getExecution())
				.form("_eventId", loginParam.get_eventId())
				.execute();
		cookies = response.getCookies();

		while (response.getStatus() == MOVE_CODE){
			response = HttpRequest.get(response.header("location"))
					.cookie(cookies)
					.execute();
			cookies = response.getCookies();
		}

		if(response.body().contains("统一身份认证")) {
			log.error("登录失败！");
			return false;

		}else {
			log.info("登录成功！");
			return true;
		}
	}

	/**
	 * 获取并设置打卡信息
	 */
	public static NovInfo getAndSetInfo(){
		log.info("开始获取个人信息");

		// 统一认证请求
		HttpResponse response = HttpRequest.get(BASE_URL)
				.cookie(cookies)
				.execute();
		cookies = response.getCookies();

		// 获取定义信息和旧打卡信息
		Matcher oldInfoMatcher = Pattern.compile(OLD_INFO_REG).matcher(response.body());
		if(!oldInfoMatcher.find()){
			log.error("获取个人信息失败，请至少手动打一次卡再运行此脚本");
			return null;
		}
		Matcher defMatcher = Pattern.compile(DEF_REG).matcher(response.body());
		if(!defMatcher.find()){
			log.error("获取定义信息失败，程序退出");
			return null;
		}
		String oldInfoStr = oldInfoMatcher.group(0).substring("oldInfo: ".length());
		String defStr = defMatcher.group(0).substring("def = ".length());
		NovInfo novInfo = JSON.parseObject(oldInfoStr, NovInfo.class);
		int newId = JSONObject.parseObject(defStr, NovInfo.class).getId();

		// 获取名字和学号
		Matcher nameMatcher = Pattern.compile("realname: \"([^\\\"]+)\",").matcher(response.body());
		nameMatcher.find();
		Matcher numMatcher = Pattern.compile("number: '([^\\']+)',").matcher(response.body());
		numMatcher.find();
		String name = nameMatcher.group(1);
		String numStr = numMatcher.group(1);

		// 设置通用信息
		novInfo.setId(newId);
		novInfo.setName(name);
		novInfo.setNumber(Integer.parseInt(numStr));
		novInfo.setDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
		novInfo.setCreated(LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8")));

		// 设置打卡信息
		novInfo.setSqhzjkkys(1); // 杭州健康码颜色，1:绿色 2:红色 3:黄色
		novInfo.setSfqrxxss(1);  // 是否确认信息属实
		novInfo.setSfsqhzjkk(1); // 是否申领杭州健康码
		novInfo.setJcqzrq("");
		novInfo.setGwszdd("");
		novInfo.setSzgjcs("");


		log.info("获取个人信息完成");
		return novInfo;
	}

	/**
	 * 提交打卡信息
	 */
	public static void postInfo(NovInfo novInfo) throws IllegalAccessException {
		log.info("开始提交打卡信息");

		// 对传进来的novInfo进行反射，获取所有属性信息，并设置到表单中
		HttpRequest request = HttpRequest.post(SAVE_URL).cookie(cookies);
		Field[] declaredFields = NovInfo.class.getDeclaredFields();
		for (Field field : declaredFields) {
			field.setAccessible(true);
			request.form(field.getName(), field.get(novInfo));
		}
		// 处理特殊的表单字段
		request.form("jrdqtlqk[]", 0);
		request.form("jrdqjcqk[]", 0);

		// 提交打卡数据
		HttpResponse response = request.execute();
		PostResp postResp = JSON.parseObject(response.body(), PostResp.class);
		if(postResp.getE() == 0) {
			log.info("打卡成功");
			PushUtils.pushMsg(String.format("打卡成功，下次打卡时间：%s", nextTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))), "");

		}else {
			log.error("打卡失败, {}", postResp.getM());
			PushUtils.pushMsg(String.format("打卡失败，原因：%s", postResp.getM()), "");
		}
	}


	/**
	 * rsa加密
	 */
	public static String rsaEncrypt(String password, String eStr, String mStr){
		byte[] bytes = password.getBytes(StandardCharsets.US_ASCII);
		BigInteger bigPass = new BigInteger(bytes);

		BigInteger bigE = new BigInteger(eStr, 16);
		BigInteger bigM = new BigInteger(mStr, 16);

		BigInteger resultBig = bigPass.pow(bigE.intValue()).mod(bigM);
		String resultStr = resultBig.toString(16);

		if(resultStr.length() > 128)
			return resultStr.substring(resultStr.length() - 128);
		else if(resultStr.length() < 128)
			return "0".repeat(128 - resultStr.length()) + resultStr;
		else
			return resultStr;
	}


	/**
	 * 设置下一次打卡时间
	 */
	public static void setNextTime(){
		LocalDateTime tomorrow = LocalDateTime.now().plusDays(1);
		nextTime = LocalDateTime.of(
				tomorrow.getYear(), tomorrow.getMonth(), tomorrow.getDayOfMonth(),
				NumberUtil.generateRandomNumber(7, 10, 1)[0],
				NumberUtil.generateRandomNumber(0, 59, 1)[0],
				NumberUtil.generateRandomNumber(0, 59, 1)[0]
		);
	}
}
