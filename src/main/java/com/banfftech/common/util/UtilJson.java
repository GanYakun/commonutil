package com.banfftech.common.util;

import org.apache.ofbiz.base.conversion.ConversionException;
import org.apache.ofbiz.base.conversion.JSONConverters;
import org.apache.ofbiz.base.lang.JSON;

import java.util.Map;

/**
 * @author scy
 * @date 2023/10/26
 */
public class UtilJson {

    public static JSON toJson(Map<String, Object> map) throws ConversionException {
        JSONConverters.MapToJSON mapToJSON = new JSONConverters.MapToJSON();
        return mapToJSON.convert(map);
    }

    public static Map<String, Object> toMap(JSON json) throws ConversionException {
        JSONConverters.JSONToMap jsonToMap = new JSONConverters.JSONToMap();
        return jsonToMap.convert(json);
    }

    public static Map<String, Object> toMap(String jsonStr) throws ConversionException {
        return toMap(JSON.from(jsonStr));
    }

}
