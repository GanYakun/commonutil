package com.banfftech.common.services;

import com.banfftech.common.util.CommonUtils;
import com.dpbird.odata.OfbizODataException;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityTypeUtil;
import org.apache.ofbiz.service.*;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPartyRelationshipByEntityAuto", (GenericValue) context.get("userLogin"));

        return resultMap;
    }
}
