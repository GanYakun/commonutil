package com.banfftech.common.events;

import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.common.login.LoginServices;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.webapp.control.LoginWorker;
import org.apache.ofbiz.webapp.stats.VisitHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Map;

import static org.apache.ofbiz.base.util.UtilGenerics.checkMap;

/**
 * @author scy
 * @date 2023/7/4
 */
public class LoginEvent {
    private static final String MODULE = LoginEvent.class.getName();
    private static final String resource = "SecurityextUiLabels";

    public static String externalLogin(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Locale locale = request.getLocale();
        //externalId
        String externalId = request.getParameter("USERNAME");
        String password = request.getParameter("PASSWORD");
        if (UtilValidate.isNotEmpty(request.getAttribute("USERNAME"))) {
            externalId = (String) request.getAttribute("USERNAME");
        }
        if (UtilValidate.isNotEmpty(request.getAttribute("PASSWORD"))) {
            password = (String) request.getAttribute("PASSWORD");
        }
        if (UtilValidate.isEmpty(externalId)) {
            String message = UtilProperties.getMessage(resource, "loginevents.username_was_empty_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", message);
            return "error";
        }
        if (UtilValidate.isEmpty(password)) {
            String message = UtilProperties.getMessage(resource, "loginevents.password_was_empty_reenter", UtilHttp.getLocale(request));
            request.setAttribute("_ERROR_MESSAGE_", message);
            return "error";
        }
        try {
            GenericValue party = EntityQuery.use(delegator).from("Party").where("externalId", externalId).queryFirst();
            if (UtilValidate.isEmpty(party)) {
                String message = UtilProperties.getMessage(resource, "loginevents.username_not_found_reenter", UtilHttp.getLocale(request));
                request.setAttribute("_ERROR_MESSAGE_", message);
                return "error";
            }
            GenericValue userLogin = EntityQuery.use(delegator).from("UserLogin").where("partyId", party.getString("partyId")).queryFirst();
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
                String message = UtilProperties.getMessage(resource,"loginservices.password_incorrect", locale);
                request.setAttribute("_ERROR_MESSAGE_", message);
                return "error";
            }

//            String visitId = VisitHandler.getVisitId(request.getSession());
//            Map<String, Object> result = dispatcher.runSync("userLogin", UtilMisc.toMap("login.username", userLogin.getString("userLoginId"), "login.password", password,
//                    "visitId", visitId, "locale", UtilHttp.getLocale(request), "request", request));
//            if (ServiceUtil.isError(result)) {
//                request.setAttribute("_ERROR_MESSAGE_", ServiceUtil.getErrorMessage(result));
//                return "error";
//            }
//            GenericValue loginUser = (GenericValue) result.get("userLogin");
//            Map<String, Object> userLoginSession = checkMap(result.get("userLoginSession"), String.class, Object.class);
//            return LoginWorker.doMainLogin(request, response, loginUser, userLoginSession);
        } catch (GenericEntityException e) {
            request.setAttribute("_ERROR_MESSAGE_", e.getMessage());
            return "error";
        }


    }
}
