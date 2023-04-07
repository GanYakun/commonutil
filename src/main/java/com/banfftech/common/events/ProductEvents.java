package com.banfftech.common.events;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.services.ProcessorServices;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.ex.ODataException;

import java.util.*;


public class ProductEvents {

    public static final String module = ProductEvents.class.getName();


    /*(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)*/
    public static Object newProduct(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericServiceException, GenericEntityException, ODataException {
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        actionParameters.put("createdByUserLogin", userLogin.getString("userLoginId"));
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) ProcessorServices.stickySessionNewAction(oDataContext, actionParameters, edmBindingTarget);

        return ofbizEntity;
    }


    public static Object setPrimaryProductCategory(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericServiceException, ODataException {
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");

        OdataOfbizEntity productCategoryMemberEntity = (OdataOfbizEntity) actionParameters.get("productCategoryMember");
        GenericValue productCategoryMember = productCategoryMemberEntity.getGenericValue();
        String productId = productCategoryMember.getString("productId");
        String primaryProductCategoryId = productCategoryMember.getString("productCategoryId");
        try {
            dispatcher.runSync("banfftech.updateProduct", UtilMisc.toMap("productId", productId,
                    "primaryProductCategoryId", primaryProductCategoryId, "userLogin", userLogin));
        } catch (GenericServiceException e) {
            throw new OfbizODataException("设置主分类失败");
        }

        return productCategoryMemberEntity;
    }

}
