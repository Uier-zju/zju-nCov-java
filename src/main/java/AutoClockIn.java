import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import task.SingleTask;

/**
 * @author st4rlight
 * @since 2021/1/25
 */
@Slf4j
public class AutoClockIn {

	public static void main(String[] args) throws SchedulerException {
		// 1、创建调度器Scheduler
		SchedulerFactory schedulerFactory = new StdSchedulerFactory();
		Scheduler scheduler = schedulerFactory.getScheduler();
		// 2、创建JobDetail实例，并与PrintWordsJob类绑定(Job执行内容)
		JobDetail jobDetail = JobBuilder.newJob(SingleTask.class).build();
		// 3、构建Trigger实例,每隔1s执行一次
		Trigger trigger = TriggerBuilder.newTrigger()
							.startNow()//立即生效
							.withSchedule(
								SimpleScheduleBuilder.simpleSchedule()
									.withIntervalInSeconds(100)//每隔1s执行一次
									.repeatForever()
							)
							.build();//一直执行

		// 4、执行
		scheduler.scheduleJob(jobDetail, trigger);
		scheduler.start();
	}
}
