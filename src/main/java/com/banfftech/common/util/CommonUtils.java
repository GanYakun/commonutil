package com.banfftech.common.util;

import com.dpbird.odata.OfbizODataException;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericPK;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.*;

import java.util.*;

/**
 * @author scy
 * @date: 2021/9/23
 */
public class CommonUtils {

    private static Object GenericEntityException;
    /**
     * 检查哪些状态可以切换
     *
     * @param currentStatusId 当前状态
     * @param allStatusMap    需要切换的状态
     */
    public static void checkStatus(String currentStatusId, Map<String, Boolean> allStatusMap, Delegator delegator) {
        try {
            Set<Map.Entry<String, Boolean>> entries = allStatusMap.entrySet();
            for (Map.Entry<String, Boolean> entry : entries) {
                Map<String, Object> queryMap = UtilMisc.toMap("statusId", currentStatusId, "statusIdTo", entry.getKey());
                GenericValue statusValidChange = delegator.findOne("StatusValidChange", queryMap, false);
                if (UtilValidate.isNotEmpty(statusValidChange)) {
                    entry.setValue(false);
                }
            }
        } catch (GenericEntityException e) {
            e.printStackTrace();
        }
    }

    /**
     * 检测产品所属category是否在服务范围内
     *
     * @param delegator
     * @param productId
     * @param checkCategoryId
     * @throws OfbizODataException
     * @throws GenericEntityException
     */
    public static Boolean checkProductCategory(Delegator delegator, String productId, String checkCategoryId)
            throws OfbizODataException, GenericEntityException {

        Boolean checkCategory = true;
        String primaryParentCategoryId = null;
        List<GenericValue> productCategoryMembers = delegator.findByAnd("ProductCategoryMember", UtilMisc.toMap("productId", productId), null, false);
        GenericValue productCategoryMember = EntityUtil.getFirst(productCategoryMembers);
        if (UtilValidate.isNotEmpty(productCategoryMember)) {
            String productCategoryId = productCategoryMember.getString("productCategoryId");
            GenericValue primaryProductCategory = delegator.findOne("ProductCategory", UtilMisc.toMap("productCategoryId", productCategoryId), false);
            if (UtilValidate.isEmpty(primaryProductCategory)) {
                checkCategory = false;
            } else {
                primaryParentCategoryId = primaryProductCategory.getString("primaryParentCategoryId");
                if (!UtilValidate.areEqual(primaryParentCategoryId, checkCategoryId)) {
                    checkCategory = false;
                }
            }
        } else {
            checkCategory = false;
        }
        return checkCategory;
    }

    /**
     * 验证输入是否重复
     *
     * @param delegator       delegator
     * @param inputStringName 输入字段的名称
     * @param findEntityName  待查询对象名称
     * @param checkValue      待检查输入值
     */
    public static Boolean checkInputRepeat(Delegator delegator, String inputStringName, String findEntityName, Map<String, Object> findCondition, String checkValue)
            throws OfbizODataException, GenericEntityException {
        Boolean isRepeat = false;
        List<GenericValue> findEntities = delegator.findByAnd(findEntityName, findCondition, null, true);
        if (UtilValidate.isNotEmpty(findEntities)) {
            for (GenericValue findEntity : findEntities) {
                String findString = findEntity.getString(inputStringName);
                if (UtilValidate.areEqual(findString, checkValue)) {
                    isRepeat = true;
                }
            }
        }
        return isRepeat;
    }


    public static void setServiceFieldsAndRun(DispatchContext dctx, Map<String, Object> context,String serviceName, String userLoginId)
            throws GeneralServiceException, GenericServiceException, GenericEntityException {
        Delegator delegator = dctx.getDispatcher().getDelegator();

        GenericValue userLogin = delegator.findOne("UserLogin", UtilMisc.toMap("userLoginId", userLoginId), true);
        setServiceFieldsAndRun(dctx, context, serviceName, userLogin);
    }

    public static void setServiceFieldsAndRun(DispatchContext dctx, Map<String, Object> context,String serviceName, GenericValue userLogin)
            throws GeneralServiceException, GenericServiceException {
        LocalDispatcher dispatcher = dctx.getDispatcher();

        Map<String, Object> validFieldsForService = ServiceUtil.setServiceFields(dispatcher, serviceName,
                context, userLogin, null, null);
        dispatcher.runSync(serviceName, UtilMisc.toMap(validFieldsForService));
    }

    public static GenericValue getObjectAttributeGv(GenericValue genericValue, String attrName) throws GenericEntityException {
        Delegator delegator = genericValue.getDelegator();
        String attrEntityName = genericValue.getEntityName() + "Attribute";
        GenericPK genericPK = genericValue.getPrimaryKey();
        Map<String, Object> fieldCondition = new HashMap<>();
        fieldCondition.putAll(genericPK);
        fieldCondition.put("attrName", attrName);
        return delegator.findOne(attrEntityName, fieldCondition, false);
    }
    public static String getObjectAttribute(GenericValue genericValue, String attrName) {
        GenericValue attrGv;
        try {
            attrGv = getObjectAttributeGv(genericValue, attrName);
            return attrGv.getString("attrValue");
        } catch (org.apache.ofbiz.entity.GenericEntityException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static Boolean getObjectAttributeAsBoolean(GenericValue genericValue, String attrName) {
        GenericValue attrGv;
        try {
            attrGv = getObjectAttributeGv(genericValue, attrName);
            return attrGv.getBoolean("attrValue");
        } catch (org.apache.ofbiz.entity.GenericEntityException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static void setObjectAttribute(GenericValue genericValue, String attrName, String attrValue) throws GenericEntityException {
        GenericValue attrGv = getObjectAttributeGv(genericValue, attrName);
        if (attrGv == null) {
            Delegator delegator = genericValue.getDelegator();
            String attrEntityName = genericValue.getEntityName() + "Attribute";
            GenericPK genericPK = genericValue.getPrimaryKey();
            Map<String, Object> fieldMap = new HashMap<>();
            fieldMap.putAll(genericPK);
            fieldMap.put("attrName", attrName);
            fieldMap.put("attrValue", attrValue);
            attrGv = delegator.makeValue(attrEntityName, fieldMap);
            attrGv.create();
        }
    }
    public static void setObjectAttributeBoolean(GenericValue genericValue, String attrName, boolean booleanValue) throws GenericEntityException {
        String attrValue = "N";
        if (booleanValue) {
            attrValue = "Y";
        }
        setObjectAttribute(genericValue, attrName, attrValue);
    }
}
