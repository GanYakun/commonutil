package com.banfftech.common.util;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilXml;

import java.util.Map;

/**
 * @author scy
 * @date 2023/5/18
 */
public class CamelUtil {

    public static JSONArray sendFormPost(String url, Map<String, Object> param) throws Exception {
        HttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        //timeout 10s
        httpPost.setConfig(RequestConfig.custom().setConnectTimeout(10000).build());

        // 设置请求体
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        if (UtilValidate.isNotEmpty(param)) {
            for (Map.Entry<String, Object> entry : param.entrySet()) {
                builder.addTextBody(entry.getKey(), (String) entry.getValue(), ContentType.TEXT_PLAIN);
            }
        }
        HttpEntity requestEntity = builder.build();
        httpPost.setEntity(requestEntity);

        // 发送请求并获取响应
        HttpResponse response = httpClient.execute(httpPost);
        HttpEntity responseEntity = response.getEntity();
        // 处理响应
        String responseString = EntityUtils.toString(responseEntity);
        // 返回响应
        JSONObject jsonObject = JSONObject.fromObject(responseString);
        if(jsonObject.containsKey("dataInputs")) {
            String dataInputs = jsonObject.getString("dataInputs");
            if (!isValidJson(dataInputs)) {
                //error
                String textContent = UtilXml.readXmlDocument(dataInputs).getElementsByTagName("msg").item(0).getTextContent();
                throw new Exception(textContent);
            }
            return JSONArray.fromObject(dataInputs);
        } else {
            throw new Exception("request error : " + jsonObject);
        }

    }

    public static boolean isValidJson(String jsonString) {
        try {
            if (jsonString.startsWith("[")) {
                JSONArray.fromObject(jsonString);
            } else {
                JSONObject.fromObject(jsonString);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

//    public static JSONObject sendHttpPost(String url, JSONObject params) throws IOException {
//        HttpClient httpClient = HttpClients.createDefault();
//        HttpPost httpPost = new HttpPost(url);
//
//        // 设置请求头
//        httpPost.setHeader("Content-Type", "application/json");
//
//        // 设置请求体
//        StringEntity entity = new StringEntity(params.toString(), ContentType.APPLICATION_JSON);
//        httpPost.setEntity(entity);
//
//        // 发送请求并获取响应
//        HttpResponse response = httpClient.execute(httpPost);
//        HttpEntity responseEntity = response.getEntity();
//
//        // 处理响应
//        String responseString = EntityUtils.toString(responseEntity);
//        JSONObject jsonResponse = JSONObject.fromObject(responseString);
//
//        // 返回响应
//        return jsonResponse;
//    }

}
