package utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * server酱推送
 * @author st4rlight
 * @since 2021/1/29
 */
@Slf4j
public class PushUtils {

	// Server酱推送地址
	public static final String SERVER_URL = "https://sctapi.ftqq.com/SCT53372TvU0eqAPiAonPHevj2EuElTWe.send";


	public static void pushMsg(String title, String desc){
		log.info("server酱消息推送开始");
		HttpResponse response = HttpRequest.post(SERVER_URL)
				.form("text", title)
				.form("desp", desc)
				.execute();

		if(response.getStatus() == 200)
			log.info("server酱消息推送成功");
		else
			log.error("server酱消息推送失败");
	}
}
