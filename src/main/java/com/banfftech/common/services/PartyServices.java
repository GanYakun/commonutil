package com.banfftech.common.services;

import com.banfftech.common.util.CommonUtils;
import com.banfftech.common.util.PartyServiceUtils;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.services.OfbizServiceException;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityTypeUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.*;

import java.sql.Timestamp;
import java.util.*;

public class PartyServices {
    private static final String MODULE = PartyServices.class.getName();
    //    private static final String RESOURCE = "PartyUiLabels";
    private static final String RES_ERROR = "PartyErrorUiLabels";

    public static Map<String, Object> createPartyGroup(DispatchContext ctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = new HashMap<>();
        Delegator delegator = ctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Timestamp now = UtilDateTime.nowTimestamp();

        String partyId = (String) context.get("partyId");
        Locale locale = (Locale) context.get("locale");

        // partyId might be empty, so check it and get next seq party id if empty
        if (UtilValidate.isEmpty(partyId)) {
            try {
                partyId = delegator.getNextSeqId("Party");
            } catch (IllegalArgumentException e) {
                return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                        "partyservices.could_not_create_party_group_generation_failure", locale));
            }
        } else {
            // if specified partyId starts with a number, return an error
            if (partyId.matches("\\d+")) {
                return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                        "partyservices.could_not_create_party_ID_digit", locale));
            }
        }

        try {
            // check to see if party object exists, if so make sure it is PARTY_GROUP type party
            GenericValue party = EntityQuery.use(delegator).from("Party").where("partyId", partyId).queryOne();
            GenericValue partyGroupPartyType = EntityQuery.use(delegator).from("PartyType").where("partyTypeId", "PARTY_GROUP").cache().queryOne();

            if (partyGroupPartyType == null) {
                return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                        "partyservices.partyservices.party_type_not_found_in_database_cannot_create_party_group", locale));
            }

            if (party != null) {
                GenericValue partyType = party.getRelatedOne("PartyType", true);

                if (!EntityTypeUtil.isType(partyType, partyGroupPartyType)) {
                    return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                            "partyservices.partyservices.cannot_create_party_group_already_exists_not_PARTY_GROUP_type", locale));
                }
            } else {
                // create a party if one doesn't already exist
                String partyTypeId = "PARTY_GROUP";

                if (UtilValidate.isNotEmpty(context.get("partyTypeId"))) {
                    GenericValue desiredPartyType = EntityQuery.use(delegator).from("PartyType").where("partyTypeId", context.get("partyTypeId"))
                            .cache().queryOne();
                    if (desiredPartyType != null && EntityTypeUtil.isType(desiredPartyType, partyGroupPartyType)) {
                        partyTypeId = desiredPartyType.getString("partyTypeId");
                    } else {
//                        return ServiceUtil.returnError(UtilProperties.getMessage(RESOURCE,
//                                "PartyPartyTypeIdNotFound", UtilMisc.toMap("partyTypeId", context.get("partyTypeId")), locale));
                        return ServiceUtil.returnError("party type not found: " + context.get("partyTypeId"));
                    }
                }

                Map<String, Object> newPartyMap = UtilMisc.toMap("partyId", partyId, "partyTypeId", partyTypeId, "createdDate", now,
                        "lastModifiedDate", now);
                if (userLogin != null) {
                    newPartyMap.put("createdByUserLogin", userLogin.get("userLoginId"));
                    newPartyMap.put("lastModifiedByUserLogin", userLogin.get("userLoginId"));
                }

                String statusId = (String) context.get("statusId");
                party = delegator.makeValue("Party", newPartyMap);
                party.setNonPKFields(context);

                if (statusId == null) {
                    statusId = "PARTY_ENABLED";
                }
                party.set("statusId", statusId);
                party.create();

                // create the status history
                GenericValue partyStat = delegator.makeValue("PartyStatus",
                        UtilMisc.toMap("partyId", partyId, "statusId", statusId, "statusDate", now));
                if (userLogin != null) {
                    partyStat.put("changeByUserLoginId", userLogin.get("userLoginId"));
                }
                partyStat.create();
            }

            GenericValue partyGroup = EntityQuery.use(delegator).from("PartyGroup").where("partyId", partyId).queryOne();
            if (partyGroup != null) {
                return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                        "partyservices.cannot_create_party_group_already_exists", locale));
            }

            partyGroup = delegator.makeValue("PartyGroup", UtilMisc.toMap("partyId", partyId));
            partyGroup.setNonPKFields(context);
            partyGroup.create();

        } catch (GenericEntityException e) {
            Debug.logWarning(e, MODULE);
            return ServiceUtil.returnError(UtilProperties.getMessage(RES_ERROR,
                    "partyservices.data_source_error_adding_party_group",
                    UtilMisc.toMap("errMessage", e.getMessage()), locale));
        }

        result.put("partyId", partyId);
        result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        return result;
    }

    public static Map<String, Object> createPartyRelationship(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GeneralServiceException, OfbizODataException, GenericServiceException {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        String roleTypeIdTo = (String) context.get("roleTypeIdTo");
        String partyIdTo = (String) context.get("partyIdTo");
        context.put("fromDate", UtilDateTime.nowTimestamp());

        GenericValue partyRole = delegator.findOne("PartyRole", UtilMisc.toMap("partyId", partyIdTo, "roleTypeId", roleTypeIdTo), false);
        if (UtilValidate.isEmpty(partyRole) && UtilValidate.isNotEmpty(roleTypeIdTo)) {
            Map<String, Object> partyRoleResultMap = dispatcher.runSync("banfftech.createPartyRole", UtilMisc.toMap("userLogin", context.get("userLogin"), "partyId", context.get("partyIdTo"), "roleTypeId", context.get("roleTypeIdTo")));
        }
        return CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyRelationshipByEntityAuto", (GenericValue) context.get("userLogin"));
    }


    public static Map<String, Object> createPartyAndPartyGroup(DispatchContext dctx, Map<String, Object> context) throws GenericServiceException {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        try {
            Map<String, Object> partyResult = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createParty",
                    (String) context.get("userLoginId"));
            context.put("partyId", partyResult.get("partyId"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyGroup",
                    (String) context.get("userLoginId"));
            result.put("partyId", partyResult.get("partyId"));
        } catch (GeneralServiceException | GenericEntityException | GenericServiceException e) {
            throw new GenericServiceException(e.getMessage());
        }

        return result;
    }

    public static Map<String, Object> updatePartyAndPartyGroup(DispatchContext dctx, Map<String, Object> context)
            throws GenericServiceException {
        try {
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateParty",
                    (String) context.get("userLoginId"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updatePartyGroup",
                    (String) context.get("userLoginId"));
        } catch (GeneralServiceException | GenericEntityException | GenericServiceException e) {
            throw new GenericServiceException(e.getMessage());
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> deletePartyAndPartyGroup(DispatchContext dctx, Map<String, Object> context)
            throws GenericServiceException {
        try {
            String partyId = (String) context.get("partyId");

            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.deletePartyRelationShip",
                    (String) context.get("userLoginId"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.deletePartyGroup",
                    (String) context.get("userLoginId"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.deleteParty",
                    (String) context.get("userLoginId"));
        } catch (GeneralServiceException | GenericEntityException | GenericServiceException e) {
            throw new GenericServiceException(e.getMessage());
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> createDepartment(DispatchContext dctx, Map<String, Object> context) throws GenericServiceException {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        try {

            Map<String, Object> partyResult = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createParty",
                    (String) context.get("userLoginId"));
            context.put("partyId", partyResult.get("partyId"));
            context.put("roleTypeId", "DEPARTMENT");
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyRole",
                    (String) context.get("userLoginId"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyGroup",
                    (String) context.get("userLoginId"));
            result.put("partyId", partyResult.get("partyId"));
        } catch (GeneralServiceException | GenericEntityException | GenericServiceException e) {
            throw new GenericServiceException(e.getMessage());
        }
        return result;
    }

    public static Map<String, Object> createUserGroup(DispatchContext dctx, Map<String, Object> context) throws GenericServiceException {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        try {
            Map<String, Object> partyResult = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createParty",
                    (String) context.get("userLoginId"));
            context.put("partyId", partyResult.get("partyId"));
            context.put("roleTypeId", "OTHER_ORGANIZATION_U");
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyRole",
                    (String) context.get("userLoginId"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyGroup",
                    (String) context.get("userLoginId"));
            result.put("partyId", partyResult.get("partyId"));
        } catch (GeneralServiceException | GenericEntityException | GenericServiceException e) {
            throw new GenericServiceException(e.getMessage());
        }
        return result;
    }

    public static Map<String, Object> updateMemberNumber(DispatchContext dctx, Map<String, Object> context)
            throws GenericServiceException {
        try {
            Delegator delegator = dctx.getDelegator();
            LocalDispatcher dispatcher = dctx.getDispatcher();
            GenericValue userLogin = (GenericValue) context.get("userLogin");
            String partyIdFrom = (String) context.get("partyIdFrom");
            //部门添加成员时该字段为空(仅供成员更改部门时使用)
            String oldDepartmentId = (String) context.get("oldPartyIdFrom");

            //第一步:获取新老部门的所有父级部门ID
            Set<String> parentDepartmentIds = PartyServiceUtils.traverseParentDepartments(delegator, partyIdFrom);
            if (UtilValidate.isNotEmpty(oldDepartmentId)) {
                parentDepartmentIds.addAll(PartyServiceUtils.traverseParentDepartments(delegator, oldDepartmentId));
            }

            //第二步:更新相关部门的的成员数量
            for (String departmentId : parentDepartmentIds) {
                List<GenericValue> allMembers = new ArrayList<>();
                PartyServiceUtils.getDepartmentALlMembers(delegator, departmentId, allMembers);
                dispatcher.runSync("banfftech.updatePartyGroup",
                        UtilMisc.toMap("partyId", departmentId, "numEmployees", allMembers.size(), "userLogin", userLogin));
            }

        } catch (GenericEntityException | OfbizODataException e) {
            throw new GenericServiceException(e.getMessage());
        }

        return ServiceUtil.returnSuccess();
    }

    /**
     * @param [dctx, context]
     * @Author yyp
     * @Description 主要作用:在创建成员之后Eca触发创建对应的登录账号和登录权限(默认创建后的初始状态是未激活)
     * @Date 10:33 2023/9/20
     * @EntityTypeName
     * @ServiceName
     **/
    public static Map<String, Object> createUserLoginAndPermission(DispatchContext dctx, Map<String, Object> context) throws GenericServiceException, OfbizServiceException {
        try {
            Delegator delegator = dctx.getDelegator();
            GenericValue userLogin = (GenericValue) context.get("userLogin");
            String userLoginId = (String) context.get("phoneMobile");
            GenericValue verifyUserLogin = delegator.findOne("UserLogin", UtilMisc.toMap("userLoginId", userLoginId), false);
            if (UtilValidate.isNotEmpty(verifyUserLogin)) {
                throw new OfbizServiceException("当前组织内已存在相同的手机号码,请更换后重试");
            }
            //创建人员初始化密码 存在配置中
            String defaultPassword = EntityUtilProperties.getPropertyValue("gconfig", "defaultPassword",
                    "gongsconfig", delegator);
            context.put("userLoginId", userLoginId);
            context.put("enabled", "Y");
            context.put("currentPassword", CommonUtils.getEncryptedPassword(delegator, defaultPassword));
            context.put("groupId", "VISIT");
            context.put("fromDate", UtilDateTime.nowTimestamp());

            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createUserLogin",
                    userLogin.getString("userLoginId"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createUserLoginSecurityGroup",
                    userLogin.getString("userLoginId"));
        } catch (GeneralServiceException | GenericEntityException | GenericServiceException e) {
            throw new GenericServiceException(e.getMessage());
        }
        return ServiceUtil.returnSuccess();
    }

    /**
     * @Author yyp
     * @Description 主要作用:更新PartyRelationship,并且检查创建PartyRole.且当更新的是部门和成员关系时更新相关部门的人员数量
     * @Date 10:37 2023/10/16
     * @param [dctx, context]
     * @EntityName PartyRelationship
     * @ServiceName banfftech.updatePartyRelationshipAndCheckRole
     **/
    public static Map<String, Object> updatePartyRelationshipAndCheckRole(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GeneralServiceException, OfbizODataException, GenericServiceException {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        String partyRelationshipId = (String) context.get("partyRelationshipId");
        String roleTypeIdTo = (String) context.get("roleTypeIdTo");
        String partyIdTo = (String) context.get("partyIdTo");
        context.put("fromDate", UtilDateTime.nowTimestamp());

        GenericValue partyRole = delegator.findOne("PartyRole", UtilMisc.toMap("partyId", partyIdTo, "roleTypeId", roleTypeIdTo), false);
        if (UtilValidate.isEmpty(partyRole) && UtilValidate.isNotEmpty(roleTypeIdTo)) {
            Map<String, Object> partyRoleResultMap = dispatcher.runSync("banfftech.createPartyRole",
                    UtilMisc.toMap("userLogin", context.get("userLogin"), "partyId", context.get("partyIdTo"), "roleTypeId", context.get("roleTypeIdTo")));
        }
        //更新PartyRelationship之前获取partyIdFrom
        GenericValue partyRelationship = delegator.findOne("PartyRelationship", UtilMisc.toMap("partyRelationshipId", partyRelationshipId), false);
        context.put("oldPartyIdFrom", partyRelationship.getString("partyIdFrom"));

        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updatePartyRelationship", (GenericValue) context.get("userLogin"));
        //如果是成员和部门之间的关系变更,则更新所有相关部门的成员数量
        if ("ORD_EMPLOYEE".equals(partyRelationship.getString("roleTypeIdTo"))) {
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateMemberNumber", (GenericValue) context.get("userLogin"));
        }
        return resultMap;
    }

    public static Map<String, Object> createRelationshipAndToParty(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GeneralServiceException, OfbizODataException, GenericServiceException {
        GenericValue userLogin = (GenericValue) context.get("userLogin");

        String partyName = CommonUtils.joinPartyName((String) context.get("firstName"), (String) context.get("middleName"), (String) context.get("lastName"));
        context.put("partyName", partyName);
        Map<String, Object> result = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPersonAndContact", userLogin);
        context.put("partyId", result.get("partyId"));
        context.put("partyIdTo", result.get("partyId"));
        Map<String, Object> createResult = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyRelationship", userLogin);
        //create media
        result = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createDataResource", userLogin);
        String dataResourceId = (String) result.get("dataResourceId");
        context.put("dataResourceId", dataResourceId);
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createOtherDataResource", userLogin);
        result = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createContent", userLogin);
        context.put("contentId", result.get("contentId"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyContent", userLogin);
        return createResult;
    }
    public static Map<String, Object> updateRelationshipAndToParty(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GeneralServiceException, OfbizODataException, GenericServiceException {
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updatePersonAndContact", (GenericValue) context.get("userLogin"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updatePartyRelationship", (GenericValue) context.get("userLogin"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateDataResource", (GenericValue) context.get("userLogin"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateOtherDataResource", (GenericValue) context.get("userLogin"));
        return ServiceUtil.returnSuccess();
    }
    public static Map<String, Object> deleteRelationshipAndToParty(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GeneralServiceException, OfbizODataException, GenericServiceException {
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.deletePartyRelationship", (GenericValue) context.get("userLogin"));
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> createPartyContactMechPurposeAndAddress(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GeneralServiceException, OfbizODataException, GenericServiceException {
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPostalAddressAndContactMech", (GenericValue) context.get("userLogin"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyContactMechPurpose", (GenericValue) context.get("userLogin"));
        return ServiceUtil.returnSuccess();
    }
    public static Map<String, Object> updatePartyContactMechPurposeAndAddress(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GeneralServiceException, OfbizODataException, GenericServiceException {
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updatePartyContactMechPurpose", (GenericValue) context.get("userLogin"));
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updatePostalAddress", (GenericValue) context.get("userLogin"));
        return ServiceUtil.returnSuccess();
    }

}
