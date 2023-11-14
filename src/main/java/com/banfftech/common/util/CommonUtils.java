package com.banfftech.common.util;

import com.dpbird.odata.OdataParts;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.base.crypto.HashCrypt;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericPK;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.*;
import org.apache.olingo.commons.api.data.ComplexValue;
import org.apache.olingo.commons.api.data.Property;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.ofbiz.common.login.LoginServices.getHashType;

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


    public static Map<String, Object> setServiceFieldsAndRun(DispatchContext dctx, Map<String, Object> context, String serviceName, String userLoginId)
            throws GeneralServiceException, GenericServiceException, GenericEntityException {
        Delegator delegator = dctx.getDispatcher().getDelegator();

        GenericValue userLogin = delegator.findOne("UserLogin", UtilMisc.toMap("userLoginId", userLoginId), true);
        return setServiceFieldsAndRun(dctx, context, serviceName, userLogin);
    }

    public static Map<String, Object> setServiceFieldsAndRun(DispatchContext dctx, Map<String, Object> context, String serviceName, GenericValue userLogin)
            throws GeneralServiceException, GenericServiceException {
        LocalDispatcher dispatcher = dctx.getDispatcher();

        Map<String, Object> validFieldsForService = ServiceUtil.setServiceFields(dispatcher, serviceName,
                context, userLogin, null, null);
        validFieldsForService.putIfAbsent("userLogin",userLogin);
        return dispatcher.runSync(serviceName, UtilMisc.toMap(validFieldsForService));
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
            Map<String, Object> fieldMap = new HashMap<>(genericValue.getPrimaryKey());
            fieldMap.put("attrName", attrName);
            fieldMap.put("attrValue", attrValue);
            attrGv = delegator.makeValue(attrEntityName, fieldMap);
            attrGv.create();
        } else {
            attrGv.set("attrValue", attrValue);
            attrGv.store();
        }
    }

    public static void setObjectAttributeBoolean(GenericValue genericValue, String attrName, boolean booleanValue) throws GenericEntityException {
        String attrValue = "N";
        if (booleanValue) {
            attrValue = "Y";
        }
        setObjectAttribute(genericValue, attrName, attrValue);
    }

    public static String joinMultipleFields(List<String> linkFields) {
        StringBuilder resultField = new StringBuilder();
        for (String linkField : linkFields) {
            if (UtilValidate.isNotEmpty(linkField)) {
                resultField.append(linkField);
            }
        }
        return resultField.toString();
    }

    public static Map<String, Object> complexToMap(ComplexValue complexValue) {
        Map<String, Object> resultMap = new HashMap<>();
        for (Property property : complexValue.getValue()) {
            resultMap.put(property.getName(), property.getValue());
        }
        return resultMap;
    }

    public static List<Map<String, Object>> complexToMap(List<ComplexValue> complexValues) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (ComplexValue complexValue : complexValues) {
            resultList.add(complexToMap(complexValue));
        }
        return resultList;
    }

    /**
     * 查询用户所属机构(也就是查询新版的部门)
     */
    public static String getPartyCompany(String partyId, Delegator delegator) throws GenericEntityException {
        GenericValue relationship = EntityQuery.use(delegator).from("PartyRelationship")
                .where("partyIdTo", partyId, "roleTypeIdTo", "ORD_EMPLOYEE").queryFirst();
        if (UtilValidate.isEmpty(relationship)) {
            return null;
        }
        return relationship.getString("partyIdFrom");
    }

    /**
     * @param [dispatcher, genericValue, userLogin]
     * @Author yyp
     * @Description //主要作用:删除GenericValue-通过调用标准命名格式的deleteService
     * @Date 11:38 2023/8/4
     **/
    public static void removeGenericValueByAutoService(LocalDispatcher dispatcher, GenericValue genericValue, GenericValue userLogin)
            throws GenericServiceException {
        String genericValueName = genericValue.getModelEntity().getEntityName();
        Map<String, Object> primaryKeyMaps = new HashMap<>(genericValue.getPrimaryKey());
        primaryKeyMaps.put("userLogin", userLogin);
        String serviceName = "banfftech.delete" + genericValueName;
        dispatcher.runSync(serviceName, primaryKeyMaps);
    }

    /**
     * @param
     * @Author yyp
     * @Description //主要作用:1、使用范围Action(子对象Action)或多段查询的目标对象;2、获取多段式查询的其中某一段OdataOfbizEntity
     * @Date 11:40 2023/8/4
     **/

    public static OdataOfbizEntity getOdataPartByEntityType(Map<String, Object> oDataContext, String entityName)
            throws GenericServiceException {

        List<OdataParts> odataParts = UtilGenerics.checkList(oDataContext.get("odataParts"));
        for (OdataParts odataPart : odataParts) {
            String odataPartEntityTypeName = odataPart.getEdmEntityType().getName();
            if (entityName.equals(odataPartEntityTypeName)) {
                return (OdataOfbizEntity) odataPart.getEntityData();
            }
        }

        return null;
    }

    /**
     * @param [delegator, currentPassword]
     * @Author yyp
     * @Description 作用:输入铭文密码,获取到对应的密文密码
     * @Date 11:57 2023/9/20
     **/
    public static String getEncryptedPassword(Delegator delegator, String currentPassword)
            throws GenericServiceException {

        boolean useEncryption = "true".equals(EntityUtilProperties.getPropertyValue("security", "password.encrypt", delegator));
        currentPassword = useEncryption ? HashCrypt.cryptUTF8(getHashType(), null, currentPassword) : currentPassword;

        return currentPassword;
    }

    /**
     * 获取一个对象的创建人
     */
    public static GenericValue getCreateParty(GenericValue genericValue) throws GenericEntityException {
        if (genericValue == null || !genericValue.getModelEntity().isField("createdByUserLogin")
                || UtilValidate.isEmpty(genericValue.getString("createdByUserLogin"))) {
            return null;
        }
        GenericValue userLogin = EntityQuery.use(genericValue.getDelegator()).from("UserLogin").where("userLoginId", genericValue.getString("createdByUserLogin")).queryOne();
        if (UtilValidate.isEmpty(userLogin)) {
            return null;
        }
        return userLogin.getRelatedOne("Party", false);
    }

    /**
     * @Author yyp
     * @Description 作用:归还InventoryItemDetails(保证相应的字段和值并不发生改变,仅仅取反可用库存)
     * @Date 10:17 2023/10/27
     **/
    public static void returnInventoryItemDetails(LocalDispatcher dispatcher, List<GenericValue> inventoryItemDetails, GenericValue userLogin)
            throws GenericServiceException {
        //遍历InventoryItemDetails,创建新的流水
        for (GenericValue inventoryItemDetail : inventoryItemDetails) {
            returnInventoryItemDetail(dispatcher,inventoryItemDetail,userLogin);
        }
    }

    /**
     * @Author yyp
     * @Description 作用:归还InventoryItemDetail(保证相应的业务字段并不发生改变,仅仅取反给定的数量)
     * @Date 10:17 2023/10/27
     **/
    public static void returnInventoryItemDetail(LocalDispatcher dispatcher, GenericValue inventoryItemDetail, BigDecimal quantity, GenericValue userLogin)
            throws GenericServiceException {

            String inventoryItemDetailSeqId = inventoryItemDetail.getString("inventoryItemDetailSeqId");
            //获取当前流水的所有字段信息
            Map<String, Object> serviceParam = new HashMap<>(inventoryItemDetail);
            //删除当前流水参数中的一个主键inventoryItemDetailSeqId
            serviceParam.remove("inventoryItemDetailSeqId", inventoryItemDetailSeqId);
            //取反当前流水的可用库存数量
            serviceParam.put("availableToPromiseDiff", quantity.negate());
            //放入service调用需要的userLogin
            serviceParam.put("userLogin", userLogin);
            //更新有效时间
            serviceParam.put("effectiveDate", UtilDateTime.nowTimestamp());
            dispatcher.runSync("banfftech.createInventoryItemDetail", serviceParam);
    }

    /**
     * @Author yyp
     * @Description 作用:归还InventoryItemDetails(保证相应的业务字段并不发生改变,取反可用库存)
     * @Date 10:17 2023/10/27
     **/
    public static void returnInventoryItemDetail(LocalDispatcher dispatcher, GenericValue inventoryItemDetail, GenericValue userLogin)
            throws GenericServiceException {
            String inventoryItemDetailSeqId = inventoryItemDetail.getString("inventoryItemDetailSeqId");
            BigDecimal availableToPromiseDiff = inventoryItemDetail.getBigDecimal("availableToPromiseDiff");

            //获取当前流水的所有字段信息
            Map<String, Object> serviceParam = new HashMap<>(inventoryItemDetail);
            //删除当前流水参数中的一个主键inventoryItemDetailSeqId
            serviceParam.remove("inventoryItemDetailSeqId", inventoryItemDetailSeqId);
            //取反当前流水的可用库存数量
            serviceParam.put("availableToPromiseDiff", availableToPromiseDiff.negate());
            //放入service调用需要的userLogin
            serviceParam.put("userLogin", userLogin);
            //更新有效时间
            serviceParam.put("effectiveDate", UtilDateTime.nowTimestamp());
            dispatcher.runSync("banfftech.createInventoryItemDetail", serviceParam);
    }

    /**
     * @Author yyp
     * @Description 作用:归还InventoryItemDetails(按库存项分组,取反库存项对应的所有流水可用库存之和)
     * @Date 10:17 2023/10/27
     **/
    public static void returnInvItemDetailsGroupByInv(LocalDispatcher dispatcher, List<GenericValue> inventoryItemDetails, GenericValue userLogin)
            throws GenericServiceException {
        //按照库存项分组库存流水
        Map<String, List<GenericValue>> reqItemDetailList = inventoryItemDetails.stream().
                collect(Collectors.groupingBy(detail -> detail.getString("inventoryItemId")));
        //遍历分组后库存流水
        for (Map.Entry<String, List<GenericValue>> entry : reqItemDetailList.entrySet()) {
            List<GenericValue> reqItemDetails = entry.getValue();
            List<BigDecimal> promiseDiffs = EntityUtil.getFieldListFromEntityList(reqItemDetails, "availableToPromiseDiff", false);
            //计算当前库存流水之和
            BigDecimal total = promiseDiffs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.ZERO) < 0) {
                returnInventoryItemDetail(dispatcher, reqItemDetails.get(0), total.negate(), userLogin);
            }
        }
    }

}
