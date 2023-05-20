package com.banfftech.common.util;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.ofbiz.base.util.UtilValidate;

import java.util.Map;

/**
 * @author scy
 * @date 2023/5/18
 */
public class CamelUtil {

    public static JSONObject sendFormPost(String url, Map<String, Object> param) throws Exception {
        HttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

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
        return getCamelResult(jsonObject);
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

    public static JSONObject getCamelResult(JSONObject result){
        if (result.isEmpty() || !result.containsKey("dataInputs")) {
            return null;
        }
        JSONObject jsonObject = JSONObject.fromObject(result);
        String camelResult = jsonObject.getString("dataInputs");
        if (camelResult.startsWith("[")) {
            return JSONArray.fromObject(camelResult).getJSONObject(0);
        } else {
            return JSONObject.fromObject(camelResult);
        }


    }

}
