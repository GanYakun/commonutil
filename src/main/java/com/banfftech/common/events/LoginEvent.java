package com.banfftech.common.events;

import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.common.login.LoginServices;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityConditionList;
import org.apache.ofbiz.entity.condition.EntityExpr;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.webapp.control.LoginWorker;
import org.apache.ofbiz.webapp.webdav.WebDavUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Locale;
import java.util.Map;

/**
 * @author scy
 * @date 2023/7/4
 */
public class LoginEvent {
    private static final String MODULE = LoginEvent.class.getName();
    private static final String resource = "SecurityextUiLabels";

    /**
     * 使用externalId登录
     */
    public static String externalLogin(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        //externalId
        Map<String, String> loginForm = getLoginForm(request);
        if (UtilValidate.isEmpty(loginForm)) {
            return "error";
        }
        String externalId = loginForm.get("username");
        String password = loginForm.get("password");
        try {
            GenericValue party = EntityQuery.use(delegator).from("Party").where("externalId", externalId).queryFirst();
            if (UtilValidate.isEmpty(party)) {
                String message = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", message);
                return "error";
            }
            return doLoginByPartyId(request, party.getString("partyId"), password);
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
    }

    /**
     * externalId登录预处理
     */
    public static String externalLoginPreProcess(HttpServletRequest request, HttpServletResponse response) throws GenericServiceException, GenericEntityException {
        GenericValue userLogin;
        Map<String, Object> serviceMap = WebDavUtil.getCredentialsFromRequest(request);
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        if (serviceMap == null) {
            userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
            request.setAttribute("userLogin", userLogin);
            return "success";
        }
        String userLoginId = null;
        if (UtilValidate.isNotEmpty(serviceMap.get("login.username"))) {
            GenericValue partyAndContact = EntityQuery.use(delegator).from("Party").where("externalId", serviceMap.get("login.username")).queryFirst();
            if (UtilValidate.isNotEmpty(partyAndContact)){
                GenericValue userLoginEntity = EntityQuery.use(delegator).from("UserLogin").where("partyId", partyAndContact.getString("partyId")).queryFirst();
                if (UtilValidate.isNotEmpty(userLoginEntity)){
                    userLoginId = userLoginEntity.getString("userLoginId");
                }
            }
        }
        serviceMap.put("login.username", userLoginId);
        return preProcessLogin(serviceMap, request);
    }

    /**
     * 使用手机号登录
     */
    public static String telLogin(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        Map<String, String> loginForm = getLoginForm(request);
        if (UtilValidate.isEmpty(loginForm)) {
            return "error";
        }
        //tel
        String tel = loginForm.get("username");
        String password = loginForm.get("password");
        try {
            GenericValue partyAndContact = EntityQuery.use(delegator).from("PartyAndContact").where("phoneMobile", tel).queryFirst();
            if (UtilValidate.isEmpty(partyAndContact)) {
                String message = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", message);
                return "error";
            }
            return doLoginByPartyId(request, partyAndContact.getString("partyId"), password);
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
    }

    /**
     * 手机号登录预处理
     */
    public static String telLoginPreProcess(HttpServletRequest request, HttpServletResponse response) throws GenericServiceException, GenericEntityException {
        GenericValue userLogin;
        Map<String, Object> serviceMap = WebDavUtil.getCredentialsFromRequest(request);
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        if (serviceMap == null) {
            userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
            request.setAttribute("userLogin", userLogin);
            return "success";
        }
        String userLoginId = null;
        if (UtilValidate.isNotEmpty(serviceMap.get("login.username"))) {
            GenericValue partyAndContact = EntityQuery.use(delegator).from("PartyAndContact").where("phoneMobile", serviceMap.get("login.username")).queryFirst();
            if (UtilValidate.isNotEmpty(partyAndContact)){
                GenericValue userLoginEntity = EntityQuery.use(delegator).from("UserLogin").where("partyId", partyAndContact.getString("partyId")).queryFirst();
                if (UtilValidate.isNotEmpty(userLoginEntity)){
                    userLoginId = userLoginEntity.getString("userLoginId");
                }
            }
        }
        serviceMap.put("login.username", userLoginId);
        return preProcessLogin(serviceMap, request);
    }

    /**
     * 使用手机号和用戶ID登录
     */
    public static String telAndUserIdLogin(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        Map<String, String> loginForm = getLoginForm(request);
        if (UtilValidate.isEmpty(loginForm)) {
            return "error";
        }
        //Input Id
        String inputId = loginForm.get("username");
        String password = loginForm.get("password");
        String userLoginId = inputId;
        try {
            GenericValue partyAndContact = EntityQuery.use(delegator).from("PartyAndContact").where("phoneMobile", inputId).queryFirst();
            if (UtilValidate.isNotEmpty(partyAndContact)) {
                GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("partyId", partyAndContact.getString("partyId")).queryFirst();
                if (UtilValidate.isNotEmpty(userLogin)) {
                    userLoginId = userLogin.getString("userLoginId");
                }
            }
            return doLoginByUserId(request, userLoginId, password);
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
    }

    /**
     * 手机号和用户ID登录预处理
     */
    public static String telAndUserLoginPreProcess(HttpServletRequest request, HttpServletResponse response) throws GenericServiceException, GenericEntityException {
        GenericValue userLogin;
        Map<String, Object> serviceMap = WebDavUtil.getCredentialsFromRequest(request);
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        if (serviceMap == null) {
            userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
            request.setAttribute("userLogin", userLogin);
            return "success";
        }
        String userLoginId = (String) serviceMap.get("login.username");
        if (UtilValidate.isNotEmpty(serviceMap.get("login.username"))) {
            GenericValue partyAndContact = EntityQuery.use(delegator).from("PartyAndContact").where("phoneMobile", serviceMap.get("login.username")).queryFirst();
            if (UtilValidate.isNotEmpty(partyAndContact)){
                GenericValue userLoginEntity = EntityQuery.use(delegator).from("UserLogin").where("partyId", partyAndContact.getString("partyId")).queryFirst();
                if (UtilValidate.isNotEmpty(userLoginEntity)){
                    userLoginId = userLoginEntity.getString("userLoginId");
                }
            }
        }
        serviceMap.put("login.username", userLoginId);
        return preProcessLogin(serviceMap, request);
    }


    /**
     * 手机号和external都可以登录
     */
    public static String externalAndTelLogin(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        Map<String, String> loginForm = getLoginForm(request);
        if (UtilValidate.isEmpty(loginForm)) {
            return "error";
        }
        String username = loginForm.get("username");
        String password = loginForm.get("password");
        try {
            EntityCondition findCondition = EntityCondition.makeCondition(UtilMisc.toList(EntityCondition.makeCondition("externalId", username),
                    EntityCondition.makeCondition("phoneMobile", username)), EntityOperator.OR);
            GenericValue partyAndContact = EntityQuery.use(delegator).from("PartyAndContact").where(findCondition).queryFirst();
            if (UtilValidate.isEmpty(partyAndContact)) {
                String message = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", message);
                return "error";
            }
            return doLoginByPartyId(request, partyAndContact.getString("partyId"), password);
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }
    }

    /**
     * 手机号和external登录预处理
     */
    public static String externalAndTelLoginPreProcess(HttpServletRequest request, HttpServletResponse response) throws GenericServiceException, GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue userLogin;
        Map<String, Object> serviceMap = WebDavUtil.getCredentialsFromRequest(request);
        if (serviceMap == null) {
            userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
            request.setAttribute("userLogin", userLogin);
            return "success";
        }
        if (UtilValidate.isNotEmpty(serviceMap.get("login.username"))) {
            //Compatible with tel login and externalId login
            EntityCondition findCondition = EntityCondition.makeCondition(UtilMisc.toList(EntityCondition.makeCondition("externalId", serviceMap.get("login.username")),
                    EntityCondition.makeCondition("phoneMobile", serviceMap.get("login.username"))), EntityOperator.OR);
            GenericValue partyAndContact = EntityQuery.use(delegator).from("PartyAndContact").where(findCondition).queryFirst();
            if (UtilValidate.isEmpty(partyAndContact)) {
                return "error";
            }
            userLogin = EntityQuery.use(delegator).from("UserLogin").where("partyId", partyAndContact.getString("partyId")).queryFirst();
            if (UtilValidate.isEmpty(userLogin)) {
                return "error";
            }
            serviceMap.put("login.username", userLogin.getString("userLoginId"));
        }
        return preProcessLogin(serviceMap, request);
    }

    private static String preProcessLogin(Map<String, Object> serviceMap, HttpServletRequest request) throws GenericServiceException {
        if (UtilValidate.isEmpty(serviceMap.get("login.username"))) {
            String message = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", message);
            return "error";
        }
        serviceMap.put("locale", UtilHttp.getLocale(request));
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Map<String, Object> result = dispatcher.runSync("userLogin", serviceMap);
        if (ServiceUtil.isError(result) || ServiceUtil.isFailure(result)) {
            return "error";
        }
        GenericValue userLogin = (GenericValue) result.get("userLogin");
        request.setAttribute("userLogin", userLogin);
        HttpSession httpSession = request.getSession(true);
        httpSession.setAttribute("userLogin", userLogin);
        return "success";
    }

    /**
     * 登录
     */
    private static String doLoginByPartyId(HttpServletRequest request, String partyId, String password) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("partyId", partyId).queryFirst();
        if (UtilValidate.isEmpty(userLogin)) {
            String message = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", message);
            return "error";
        }
        boolean useEncryption = "true".equals(EntityUtilProperties.getPropertyValue("security", "password.encrypt", delegator));
        if (LoginServices.checkPassword(userLogin.getString("currentPassword"), useEncryption, password)) {
            LoginWorker.doBasicLogin(userLogin, request);
            request.setAttribute("_LOGIN_PASSED_", "TRUE");
            return "success";
        } else {
            String message = UtilProperties.getMessage(resource, "loginservices.password_incorrect", request.getLocale());
            request.setAttribute("_ERROR_MESSAGE_", message);
            return "error";
        }
    }

    /**
     * 登录
     */
    private static String doLoginByUserId(HttpServletRequest request, String userLoginId, String password) throws GenericEntityException {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("userLoginId", userLoginId).queryOne();
        if (UtilValidate.isEmpty(userLogin)) {
            String message = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", message);
            return "error";
        }
        boolean useEncryption = "true".equals(EntityUtilProperties.getPropertyValue("security", "password.encrypt", delegator));
        if (LoginServices.checkPassword(userLogin.getString("currentPassword"), useEncryption, password)) {
            LoginWorker.doBasicLogin(userLogin, request);
            request.setAttribute("_LOGIN_PASSED_", "TRUE");
            return "success";
        } else {
            String message = UtilProperties.getMessage(resource, "loginservices.password_incorrect", request.getLocale());
            request.setAttribute("_ERROR_MESSAGE_", message);
            return "error";
        }
    }

    /**
     * 从请求数据中获取用户名称和密码
     */
    private static Map<String, String> getLoginForm(HttpServletRequest request) {
        String username = request.getParameter("USERNAME");
        String password = request.getParameter("PASSWORD");
        if (UtilValidate.isNotEmpty(request.getAttribute("USERNAME"))) {
            username = (String) request.getAttribute("USERNAME");
        }
        if (UtilValidate.isNotEmpty(request.getAttribute("PASSWORD"))) {
            password = (String) request.getAttribute("PASSWORD");
        }
        if (UtilValidate.isEmpty(username)) {
            String message = UtilProperties.getMessage(resource, "loginevents.username_was_empty_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", message);
            return null;
        }
        if (UtilValidate.isEmpty(password)) {
            String message = UtilProperties.getMessage(resource, "loginevents.password_was_empty_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", message);
            return null;
        }
        return UtilMisc.toMap("username", username, "password", password);
    }


}
