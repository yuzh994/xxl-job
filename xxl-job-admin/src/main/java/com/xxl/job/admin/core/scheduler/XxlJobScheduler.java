package com.xxl.job.admin.core.scheduler;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.thread.*;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.client.ExecutorBizClient;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author xuxueli 2018-10-28 00:18:17
 */

public class XxlJobScheduler  {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobScheduler.class);


    public void init() throws Exception {
        // init i18n
        initI18n();


        /**
         * 初始化触发器线程池子
         * 这里分别初始化了2个线程池，一个快一个慢，优先选择快，当一分钟以内任务超过10次执行时间超过500ms，则加入慢线程池执行。
         */
        JobTriggerPoolHelper.toStart();

        /**
         * 30秒执行一次，维护注册表信息
         * 初始化注册或者删除线程池，主要负责客户端注册或者销毁到xxl_job_registry表
         * 剔除超时注册机器
         * 更新xxl_job_group执行器地址列表
         */
        JobRegistryHelper.getInstance().start();

        // admin fail-monitor run
        //运行失败监听器，主要失败发送邮件，重试触发器
        JobFailMonitorHelper.getInstance().start();

        // admin lose-monitor run ( depend on JobTriggerPoolHelper )
        //将丢失主机信息调度日志更改状态
        JobCompleteHelper.getInstance().start();

        // admin log report start
        //统计一些失败成功报表
        JobLogReportHelper.getInstance().start();

        // start-schedule  ( depend on JobTriggerPoolHelper )
        //执行调度器
        JobScheduleHelper.getInstance().start();

        logger.info(">>>>>>>>> init xxl-job admin success.");
    }

    
    public void destroy() throws Exception {

        // stop-schedule
        JobScheduleHelper.getInstance().toStop();

        // admin log report stop
        JobLogReportHelper.getInstance().toStop();

        // admin lose-monitor stop
        JobCompleteHelper.getInstance().toStop();

        // admin fail-monitor stop
        JobFailMonitorHelper.getInstance().toStop();

        // admin registry stop
        JobRegistryHelper.getInstance().toStop();

        // admin trigger pool stop
        JobTriggerPoolHelper.toStop();

    }

    // ---------------------- I18n ----------------------

    private void initI18n(){
        for (ExecutorBlockStrategyEnum item:ExecutorBlockStrategyEnum.values()) {
            item.setTitle(I18nUtil.getString("jobconf_block_".concat(item.name())));
        }
    }

    // ---------------------- executor-client ----------------------
    private static ConcurrentMap<String, ExecutorBiz> executorBizRepository = new ConcurrentHashMap<String, ExecutorBiz>();
    public static ExecutorBiz getExecutorBiz(String address) throws Exception {
        // valid
        if (address==null || address.trim().length()==0) {
            return null;
        }

        // load-cache
        address = address.trim();
        ExecutorBiz executorBiz = executorBizRepository.get(address);
        if (executorBiz != null) {
            return executorBiz;
        }

        // set-cache
        executorBiz = new ExecutorBizClient(address, XxlJobAdminConfig.getAdminConfig().getAccessToken());

        executorBizRepository.put(address, executorBiz);
        return executorBiz;
    }

}
