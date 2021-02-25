# zju-nCov-java
浙大自动打卡java版，添加Server酱推送，添加早7-10随机时间

修改自https://github.com/Tishacy/ZJU-nCov-Hitcarder


【直接使用方法】

1. 使用前一天必须手动打卡一次
2. fork本项目
3. 修改`SingleTask.java`类的第44、45行的学号和密码
4. 修改`PushUtils`类的第16行的Server酱推送地址，不修改的话，你的登陆结果会推送到我的微信上
   <br />
   Server酱推送地址可以在这里获取：http://sc.ftqq.com/3.versionServer
5. 运行主类`AutoClockIn.java`


【部署vps方法】

1. 按【使用方法】的描述进行修改
2. 运行`mvn package`将项目打包成jar包
3. 将打包好的jar上传到vps
4. 登陆到vps，通过命令`nohup java -jar xxx.jar &`后台运行，其中`xxx.jar`即是你打包的jar包名称
   
   
仅供学习，请勿滥用，对可能产生的一切后果概不负责。
