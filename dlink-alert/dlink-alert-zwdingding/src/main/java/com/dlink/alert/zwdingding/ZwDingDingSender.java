package com.dlink.alert.zwdingding;

import com.alibaba.xxpt.gateway.shared.api.request.OapiGovDingIsvSendJsonRequest;
import com.alibaba.xxpt.gateway.shared.api.response.OapiGovDingIsvSendJsonResponse;
import com.alibaba.xxpt.gateway.shared.client.http.ExecutableClient;
import com.alibaba.xxpt.gateway.shared.client.http.IntelligentPostClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;


/**
 * Author: kannabis
 * Date: 2022/1/6 10:23
 */


@Slf4j
@Component("zwDingDingAlarm")
public class ZwDingDingSender {

    private String appkey="glz-hO8Z9q068KIi9rg3Hkl4NbQzIb";

    private String appsecret="AqL35oaVoV3SyW815ky0t0K23oU004umAD5le1LI";

    private String domainName="zd-openplatform.bigdatacq.com";

    @Autowired
    @Lazy
    public ExecutableClient executableClient;

    @Bean
    public ExecutableClient getExecutableClient() {
        //executableClient要单例，并且使用前要初始化，只需要初始化一次
        ExecutableClient executableClient = ExecutableClient.getInstance();
        executableClient.setAccessKey(appkey);
        executableClient.setSecretKey(appsecret);
        executableClient.setDomainName(domainName);
        executableClient.setProtocal("https");
        executableClient.init();
        return executableClient;
    }

    public boolean send(String dingdingids, String content) {
        try {
            //executableClient保证单例
            IntelligentPostClient intelligentPostClient = executableClient.newIntelligentPostClient("/gov/ding/isv/send.json");
            OapiGovDingIsvSendJsonRequest oapiGovDingIsvSendJsonRequest = new OapiGovDingIsvSendJsonRequest();
            //创建人
            oapiGovDingIsvSendJsonRequest.setCreator("{\n" +
                    "    \"accountId\":\""+dingdingids+"\",\n" +
                    "}");
            //DING通知方式，短信(sms)、APP内(app)
            oapiGovDingIsvSendJsonRequest.setNotifyType("app");
            //接收人列表
            oapiGovDingIsvSendJsonRequest.setReceivers(Collections.singletonList("[{\n" +
                    "    \"accountId\":\""+dingdingids+"\",\n" +
                    "}]"));
            //租户ID
            oapiGovDingIsvSendJsonRequest.setTenantId(1L);
            //DING消息体加密方式，明文(plaintext) or 密文(ciphertext)
            oapiGovDingIsvSendJsonRequest.setTextType("plaintext");
            //DING内容消息体，格式参考下示例，只支持文本
            oapiGovDingIsvSendJsonRequest.setBody("{\"text\":\""+content+"\"}");
            //DING消息体类型，文本
            oapiGovDingIsvSendJsonRequest.setBodyType("text");
            //获取结果
            OapiGovDingIsvSendJsonResponse apiResult = intelligentPostClient.post(oapiGovDingIsvSendJsonRequest);

            if (apiResult.getSuccess().equals(true)) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}


