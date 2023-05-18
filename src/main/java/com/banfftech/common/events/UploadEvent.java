package com.banfftech.common.events;

import com.dpbird.odata.Util;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author scy
 * @date 2023/5/18
 */
public class UploadEvent {

    public static String uploadFile(HttpServletRequest request, HttpServletResponse response) {
        try {
            LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
            //获取文件表单全部内容
            Map<String, Object> multiPartMap = UtilHttp.getMultiPartParameterMap(request);
            //关联实体
            List<String> relations =  Arrays.asList(request.getParameter("relation").split("/"));
            Map<String, Object> keyMap = Util.odataIdToMap(delegator, relations.get(0), request.getParameter("key"));
            //中间表的ContentType 可以为空
            String relContentTypeIdName = request.getParameter("relContentTypeIdName");
            String relContentTypeIdValue = request.getParameter("relContentTypeIdValue");
            dispatcher.runSync("banfftech.uploadFile", UtilMisc.toMap("multiFrom", multiPartMap, "key", keyMap,
                    "relContentTypeIdName", relContentTypeIdName, "relContentTypeIdValue", relContentTypeIdValue, "relation", relations, "userLogin", userLogin));
        } catch (GenericServiceException e) {
            e.printStackTrace();
            response.setStatus(500);
            return "error";
        }
        response.setStatus(200);
        return "success";
    }

}
