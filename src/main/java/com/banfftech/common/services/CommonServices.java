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
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        String userLoginId = (String) context.get("userLoginId");

        GenericValue userLogin = delegator.findOne("UserLogin", UtilMisc.toMap("userLoginId", userLoginId), false);
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createContactMech", userLogin);
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPostalAddress", userLogin);

        return resultMap;
    }
}
