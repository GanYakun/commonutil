package com.banfftech.common.util;

import org.apache.http.entity.ContentType;
import org.apache.ofbiz.base.conversion.JSONConverters;
import org.apache.ofbiz.base.lang.JSON;
import org.apache.ofbiz.base.util.HttpClient;
import org.apache.ofbiz.base.util.UtilXml;
import org.xml.sax.SAXException;

import java.util.Map;

/**
 * @author scy
 * @date 2023/5/18
 */
public class CamelUtil {

    public static JSON sendFormPost(String url, Map<String, Object> param) throws Exception {
        JSONConverters.JSONToMap jsonToMap = new JSONConverters.JSONToMap();
        JSONConverters.MapToJSON mapToJSON = new JSONConverters.MapToJSON();
        JSON convert = mapToJSON.convert(param);
        HttpClient ofbizHttpClient = new HttpClient(url);
        ofbizHttpClient.setContentType("application/json;charset=UTF-8");
        ofbizHttpClient.post(convert.toString());
        String responseString = ofbizHttpClient.post();
        JSON resultJson = JSON.from(responseString);
        Map<String, Object> resultMap = jsonToMap.convert(resultJson);
        if (resultMap.containsKey("dataInputs")) {
            String dataInputs = (String) resultMap.get("dataInputs");
            if (!isValidJson(dataInputs)) {
                //error
                try {
                    String textContent = UtilXml.readXmlDocument(dataInputs).getElementsByTagName("msg").item(0).getTextContent();
                    throw new Exception(textContent);
                } catch (SAXException e) {
                    throw new Exception(dataInputs);
                }
            }
            return JSON.from(dataInputs);
        } else {
            throw new Exception("request error : " + resultMap);
        }

    }

    public static boolean isValidJson(String jsonString) {
        try {
            JSON from = JSON.from(jsonString);
            if (jsonString.startsWith("[")) {
                JSONConverters.JSONToList jsonToList = new JSONConverters.JSONToList();
                jsonToList.convert(from);
            } else {
                JSONConverters.JSONToMap jsonToMap = new JSONConverters.JSONToMap();
                jsonToMap.convert(from);
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
