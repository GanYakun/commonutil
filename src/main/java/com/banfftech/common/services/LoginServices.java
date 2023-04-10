package com.banfftech.common.services;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

public class LoginServices {

    public final static String module = LoginServices.class.getName();

    /**
     * checkIsLogin
     * @param dctx
     * @param context
     * @return
     */
    public static Map<String, Object> checkIsLogin(DispatchContext dctx, Map<String, Object> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("login",true);
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Debug.log(">>>>>>>>>> " + userLogin);
        if(userLogin==null){ result.put("login",false);}
        return result;
    }

    public static String logout(HttpServletRequest request, HttpServletResponse response) {
        //empty out the session
        request.getSession().invalidate();
        //empty out the session
        if (EntityUtilProperties.propertyValueEquals("security", "security.login.tomcat.sso", "true")){
            try {
                request.logout();
            } catch (ServletException e) {
                Debug.logError(e, module);
            }
        }
        return "success";
    }

}
