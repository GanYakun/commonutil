package com.banfftech.common.services;

import com.banfftech.common.util.CommonUtils;
import com.dpbird.odata.OfbizODataException;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GeneralServiceException;
import org.apache.ofbiz.service.ServiceUtil;

import java.util.Map;

public class CommonServices {
    public static Map<String, Object> createPostalAddressAndContactMech(DispatchContext dctx, Map<String, Object> context)
            throws GeneralServiceException, GenericEntityException, OfbizODataException {
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createContactMech", (GenericValue) context.get("userLogin"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPostalAddress", (GenericValue) context.get("userLogin"));

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> createPartyUserLogin(DispatchContext dctx, Map<String, Object> context)
            throws GeneralServiceException, GenericEntityException, OfbizODataException {
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createParty", (String) context.get("userLoginId"));
        context.put("enabled", "Y");
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createUserLogin", (String) context.get("userLoginId"));

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> updatePartyUserLogin(DispatchContext dctx, Map<String, Object> context)
            throws GeneralServiceException, OfbizODataException {
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateParty", (String) context.get("userLoginId"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateUserLogin", (String) context.get("userLoginId"));

        return ServiceUtil.returnSuccess();
    }
}
