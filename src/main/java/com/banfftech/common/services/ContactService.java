package com.banfftech.common.services;

import com.banfftech.common.tools.util.CommonUtils;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.*;

import java.util.Map;

/**
 * @author scy
 * @date 2023/5/26
 */
public class ContactService {

    public static Map<String, Object> createPartyAndContact(DispatchContext dctx, Map<String, Object> context) throws GeneralServiceException, GenericServiceException {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        LocalDispatcher dispatcher = dctx.getDispatcher();
        //create Party
        Map<String, Object> serviceResult = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createParty", userLogin);
        if (ServiceUtil.isError(serviceResult)) {
            return serviceResult;
        }
        //create Person
        String partyId = (String) serviceResult.get("partyId");
        context.put("partyId", partyId);
        Map<String, Object> personServiceResult = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPerson", userLogin);
        if (ServiceUtil.isError(personServiceResult)) {
            return serviceResult;
        }
        //create Contact
        createContact(dispatcher, userLogin, partyId, context);
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("partyId", partyId);
        return resultMap;
    }

    public static Map<String, Object> updatePartyAndContact(DispatchContext dctx, Map<String, Object> context) throws GeneralServiceException, GenericServiceException, GenericEntityException {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        LocalDispatcher dispatcher = dctx.getDispatcher();
        String partyId = (String) context.get("partyId");
        //update Party
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateParty", userLogin);
        //update Person
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updatePerson", userLogin);
        //update Contact
        updateContact(dispatcher, userLogin, partyId, context);
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("partyId", partyId);
        return resultMap;
    }

    public static Map<String, Object> createUserLoginAndContact(DispatchContext dctx, Map<String, Object> context) throws GeneralServiceException, GenericServiceException {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        LocalDispatcher dispatcher = dctx.getDispatcher();
        //create Party
        Map<String, Object> serviceResult = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createParty", userLogin);
        String partyId = (String) serviceResult.get("partyId");
        context.put("partyId", partyId);
        //create UserLogin
        context.put("currentPasswordVerify", context.get("currentPassword"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "createUserLogin", userLogin);

        createContact(dispatcher, userLogin, partyId, context);
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("userLoginId", context.get("userLoginId"));
        return resultMap;
    }

    public static Map<String, Object> updateUserLoginAndContact(DispatchContext dctx, Map<String, Object> context) throws GeneralServiceException, GenericServiceException, GenericEntityException {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();
        String userLoginId = (String) context.get("userLoginId");
        GenericValue updateUserLogin = delegator.findOne("UserLogin", UtilMisc.toMap("userLoginId", userLoginId), false);
        String partyId = updateUserLogin.getString("partyId");
        if (UtilValidate.isEmpty(context.get("partyId"))) {
            context.put("partyId", partyId);
        }
        //update UserLogin
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateUserLogin", userLogin);
        //update Party
        if (UtilValidate.isNotEmpty(partyId)) {
            context.put("partyId", partyId);
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateParty", userLogin);
            //update contact
            updateContact(dispatcher, userLogin, partyId, context);
        }
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("userLoginId", context.get("userLoginId"));
        return resultMap;
    }

    private static void createContact(LocalDispatcher dispatcher, GenericValue userLogin, String partyId, Map<String, Object> context) throws GenericServiceException {
        String primaryPhone = (String) context.get("primaryPhone");
        String phoneMobile = (String) context.get("phoneMobile");
        String primaryEmail = (String) context.get("primaryEmail");
        String address1 = (String) context.get("address1");
        String address2 = (String) context.get("address2");
        String city = (String) context.get("city");
        //create primaryPhone
        if (UtilValidate.isNotEmpty(primaryPhone)) {
            createPartyPrimaryPhone(dispatcher, partyId, primaryPhone, userLogin);
        }
        //create phoneMobile
        if (UtilValidate.isNotEmpty(phoneMobile)) {
            createPartyPhoneMobile(dispatcher,  partyId, phoneMobile, userLogin);
        }
        //create primaryEmail
        if (UtilValidate.isNotEmpty(primaryEmail)) {
            createPartyPrimaryEmail(dispatcher, partyId, primaryEmail, userLogin);
        }
        //create address1,address2,city
        if (UtilValidate.isNotEmpty(address1) || UtilValidate.isNotEmpty(address2) || UtilValidate.isNotEmpty(city)) {
            createPartyPrimaryLocation(dispatcher, partyId, address1, address2, city, userLogin);
        }
    }

    private static void updateContact(LocalDispatcher dispatcher, GenericValue userLogin, String partyId, Map<String, Object> context) throws GenericServiceException, GenericEntityException {
        String primaryPhone = (String) context.get("primaryPhone");
        String phoneMobile = (String) context.get("phoneMobile");
        String primaryEmail = (String) context.get("primaryEmail");
        String address1 = (String) context.get("address1");
        String address2 = (String) context.get("address2");
        String city = (String) context.get("city");
        Delegator delegator = dispatcher.getDelegator();
        //update primaryPhone
        if (UtilValidate.isNotEmpty(primaryPhone)) {
            GenericValue partyContactMechPurpose = EntityQuery.use(delegator).from("PartyContactMechPurpose").where("partyId", partyId, "contactMechPurposeTypeId", "PRIMARY_PHONE").select("contactMechId").cache().queryFirst();
            if (UtilValidate.isEmpty(partyContactMechPurpose)) {
                createPartyPrimaryPhone(dispatcher, partyId, primaryPhone, userLogin);
            } else {
                String contactMechId = partyContactMechPurpose.getString("contactMechId");
                dispatcher.runSync("banfftech.updateTelecomNumber", UtilMisc.toMap("contactMechId", contactMechId, "contactNumber", primaryPhone, "userLogin", userLogin));
            }
        }
        //update phoneMobile
        if (UtilValidate.isNotEmpty(phoneMobile)) {
            GenericValue partyContactMechPurpose = EntityQuery.use(delegator).from("PartyContactMechPurpose").where("partyId", partyId, "contactMechPurposeTypeId", "PHONE_MOBILE").select("contactMechId").cache().queryFirst();
            if (UtilValidate.isEmpty(partyContactMechPurpose)) {
                createPartyPhoneMobile(dispatcher,  partyId, phoneMobile, userLogin);
            } else {
                String contactMechId = partyContactMechPurpose.getString("contactMechId");
                dispatcher.runSync("banfftech.updateTelecomNumber", UtilMisc.toMap("contactMechId", contactMechId, "contactNumber", phoneMobile, "userLogin", userLogin));
            }
        }
        //update primaryEmail
        if (UtilValidate.isNotEmpty(primaryEmail)) {
            GenericValue partyContactMechPurpose = EntityQuery.use(delegator).from("PartyContactMechPurpose").where("partyId", partyId, "contactMechPurposeTypeId", "PRIMARY_EMAIL").select("contactMechId").cache().queryFirst();
            if (UtilValidate.isEmpty(partyContactMechPurpose)) {
                createPartyPrimaryEmail(dispatcher, partyId, primaryEmail, userLogin);
            } else {
                String contactMechId = partyContactMechPurpose.getString("contactMechId");
                dispatcher.runSync("banfftech.updateContactMech", UtilMisc.toMap("contactMechId", contactMechId, "contactMechTypeId", "EMAIL_ADDRESS", "infoString", primaryEmail, "userLogin", userLogin));
            }
        }
        //update address1,address2,city
        if (UtilValidate.isNotEmpty(address1) || UtilValidate.isNotEmpty(address2) || UtilValidate.isNotEmpty(city)) {
            GenericValue partyContactMechPurpose = EntityQuery.use(delegator).from("PartyContactMechPurpose").where("partyId", partyId, "contactMechPurposeTypeId", "PRIMARY_LOCATION").select("contactMechId").cache().queryFirst();
            if (UtilValidate.isEmpty(partyContactMechPurpose)) {
                createPartyPrimaryLocation(dispatcher, partyId, address1, address2, city, userLogin);
            } else {
                String contactMechId = partyContactMechPurpose.getString("contactMechId");
                dispatcher.runSync("banfftech.updatePostalAddress", UtilMisc.toMap("contactMechId", contactMechId, "address1", address1, "address2", address2, "city", city, "userLogin", userLogin));
            }
        }
    }

    public static String createPartyPrimaryPhone(LocalDispatcher dispatcher, String partyId, String primaryPhone, GenericValue userLogin) throws GenericServiceException {
        Delegator delegator = dispatcher.getDelegator();
        String contactMechId = delegator.getNextSeqId("ContactMech");
        dispatcher.runSync("banfftech.createContactMech", UtilMisc.toMap("contactMechId", contactMechId, "contactMechTypeId", "TELECOM_NUMBER", "userLogin", userLogin));
        dispatcher.runSync("banfftech.createTelecomNumber", UtilMisc.toMap("contactMechId", contactMechId, "contactNumber", primaryPhone, "userLogin", userLogin));
        dispatcher.runSync("banfftech.createPartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId,
                "contactMechPurposeTypeId", "PRIMARY_PHONE", "fromDate", UtilDateTime.nowTimestamp(), "userLogin", userLogin));
        return contactMechId;
    }

    public static String createPartyPhoneMobile(LocalDispatcher dispatcher, String partyId, String phoneMobile, GenericValue userLogin) throws GenericServiceException {
        Delegator delegator = dispatcher.getDelegator();
        String contactMechId = delegator.getNextSeqId("ContactMech");
        dispatcher.runSync("banfftech.createContactMech", UtilMisc.toMap("contactMechId", contactMechId, "contactMechTypeId", "TELECOM_NUMBER", "userLogin", userLogin));
        dispatcher.runSync("banfftech.createTelecomNumber", UtilMisc.toMap("contactMechId", contactMechId, "contactNumber", phoneMobile, "userLogin", userLogin));
        dispatcher.runSync("banfftech.createPartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId,
                "contactMechPurposeTypeId", "PHONE_MOBILE", "fromDate", UtilDateTime.nowTimestamp(), "userLogin", userLogin));
        return contactMechId;
    }

    public static String createPartyPrimaryEmail(LocalDispatcher dispatcher, String partyId, String primaryEmail, GenericValue userLogin) throws GenericServiceException {
        Delegator delegator = dispatcher.getDelegator();
        String contactMechId = delegator.getNextSeqId("ContactMech");
        dispatcher.runSync("banfftech.createContactMech", UtilMisc.toMap("contactMechId", contactMechId, "contactMechTypeId", "EMAIL_ADDRESS", "infoString", primaryEmail, "userLogin", userLogin));
        dispatcher.runSync("banfftech.createPartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId,
                "contactMechPurposeTypeId", "PRIMARY_EMAIL", "fromDate", UtilDateTime.nowTimestamp(), "userLogin", userLogin));
        return contactMechId;
    }

    public static String createPartyPrimaryLocation(LocalDispatcher dispatcher, String partyId, String address1, String address2, String city, GenericValue userLogin) throws GenericServiceException {
        Delegator delegator = dispatcher.getDelegator();
        String contactMechId = delegator.getNextSeqId("ContactMech");
        dispatcher.runSync("banfftech.createContactMech", UtilMisc.toMap("contactMechId", contactMechId, "contactMechTypeId", "POSTAL_ADDRESS", "userLogin", userLogin));
        dispatcher.runSync("banfftech.createPostalAddress", UtilMisc.toMap("contactMechId", contactMechId, "address1", address1, "address2", address2, "city", city, "userLogin", userLogin));
        dispatcher.runSync("banfftech.createPartyContactMechPurpose", UtilMisc.toMap("partyId", partyId, "contactMechId", contactMechId,
                "contactMechPurposeTypeId", "PRIMARY_LOCATION", "fromDate", UtilDateTime.nowTimestamp(), "userLogin", userLogin));
        return contactMechId;
    }



    public static Map<String, Object> createMemberProductCategory(DispatchContext dctx, Map<String, Object> context) throws GeneralServiceException, GenericServiceException, GenericEntityException {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String productId = (String) context.get("productId");
        String productCategoryId = (String) context.get("productCategoryId");
        if (UtilValidate.isEmpty(productId) && UtilValidate.isEmpty(productCategoryId)) {
            return ServiceUtil.returnError("Missing products or categories");
        }
        if (UtilValidate.isEmpty(context.get("id"))) {
            context.put("id", dctx.getDelegator().getNextSeqId("ProductCategoryMember"));
        }
        return CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createProductCategoryMember", userLogin);
    }

    public static Map<String, Object> updateMemberProductCategory(DispatchContext dctx, Map<String, Object> context) throws GeneralServiceException, GenericServiceException, GenericEntityException {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String productId = (String) context.get("productId");
        String productCategoryId = (String) context.get("productCategoryId");
        if (UtilValidate.isEmpty(productId) && UtilValidate.isEmpty(productCategoryId)) {
            return ServiceUtil.returnError("Missing products or categories");
        }
        return CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateProductCategoryMember", userLogin);
    }


}
