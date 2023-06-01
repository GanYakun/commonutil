package com.banfftech.common.events;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.services.ProcessorServices;
import org.apache.ofbiz.base.crypto.HashCrypt;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.ex.ODataException;

import java.util.Map;
import java.util.Objects;

import static org.apache.ofbiz.common.login.LoginServices.getHashType;

public class PartyEvents {
    public static final String module = PartyEvents.class.getName();

    /*(HttpServletRequest request, Map<String, Object> actionParameters, Object boundObject)*/
    public static Object newSupplier(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericServiceException, GenericEntityException, ODataException {
        return newPartyGroupWithRole("SUPPLIER", oDataContext, actionParameters, edmBindingTarget);
    }

    public static Object newCustomer(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericServiceException, GenericEntityException, ODataException {
        return newPartyGroupWithRole("CUSTOMER", oDataContext, actionParameters, edmBindingTarget);
    }

    public static Object newPartyUserLogin(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericServiceException, GenericEntityException, ODataException {

        return newPartyGroupWithRole((String) actionParameters.get("roleTypeId"), oDataContext, actionParameters, edmBindingTarget);
    }

    private static Object newPartyGroupWithRole(String roleTypeId, Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericServiceException, GenericEntityException, ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String sapContextId = (String) oDataContext.get("sapContextId");
        String partyTypeId = "PARTY_GROUP";
        if (!Objects.equals(roleTypeId, "SUPPLIER") && !Objects.equals(roleTypeId, "CUSTOMER")) {
            partyTypeId = "PERSON";
            String partyId = delegator.getNextSeqId("Party");
            actionParameters.put("partyId", partyId);
        }

        actionParameters.put("partyTypeId", partyTypeId);
        actionParameters.put("statusId", "PARTY_ENABLED");
        //创建PartyDraft
        OdataOfbizEntity ofbizEntity = (OdataOfbizEntity) ProcessorServices.stickySessionNewAction(oDataContext, actionParameters, edmBindingTarget);
        //创建Party相关子对象的Draft
        createPartyRelationsDraft(oDataContext, actionParameters, sapContextId, ofbizEntity, roleTypeId, partyTypeId);

        return ofbizEntity;
    }

    private static void createPartyRelationsDraft(Map<String, Object> oDataContext, Map<String, Object> actionParameters, String sapContextId,
                                                  OdataOfbizEntity ofbizEntity, String roleTypeId, String partyTypeId) throws OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        String contactMechId = "ID" + delegator.getNextSeqId("PostalAddress");
        String partyId = (String) ofbizEntity.getPropertyValue("partyId");

        //创建PartyGroupDraft/创建PersonDraft
        if (partyTypeId.equals("PARTY_GROUP")) {
            Util.createNavDraftData(oDataContext, sapContextId, UtilMisc.toMap("partyId", partyId),
                    "PartyGroup", null);
        } else {
            Util.createNavDraftData(oDataContext, sapContextId, UtilMisc.toMap("partyId", partyId),
                    "Person", null);
        }
        //创建PartyRoleDraft
        Util.createNavDraftData(oDataContext, sapContextId, UtilMisc.toMap("partyId", partyId, "roleTypeId", roleTypeId),
                "PartyRole", UtilMisc.toMap("partyId", partyId, "roleTypeId", roleTypeId));
        //创建PostalAddressDraft
        Util.createNavDraftData(oDataContext, sapContextId, UtilMisc.toMap("partyId", partyId),
                "PartyAndContact", UtilMisc.toMap("partyId", partyId));

    }

    public static Object restPassWord(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws GenericServiceException, GenericEntityException, ODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OdataOfbizEntity userEntity = (OdataOfbizEntity) actionParameters.get("user");
        GenericValue user = userEntity.getGenericValue();
        String currentPassword = (String) actionParameters.get("currentPassword");

        boolean useEncryption = "true".equals(EntityUtilProperties.getPropertyValue("security", "password.encrypt", delegator));
        String userLoginId = user.getString("userLoginId");
        currentPassword = useEncryption ? HashCrypt.cryptUTF8(getHashType(), null, currentPassword) : currentPassword;
        try {
            dispatcher.runSync("banfftech.updateUserLogin", UtilMisc.toMap("userLogin", userLogin, "userLoginId", userLoginId, "currentPassword", currentPassword));
        } catch (GenericServiceException e) {
            throw new OfbizODataException("重置密码失败");
        }

        return user;
    }

    public static Object disableOrActivateUserLogin(Map<String, Object> oDataContext, Map<String, Object> actionParameters, EdmBindingTarget edmBindingTarget)
            throws OfbizODataException {
        Delegator delegator = (Delegator) oDataContext.get("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) oDataContext.get("dispatcher");
        GenericValue userLogin = (GenericValue) oDataContext.get("userLogin");
        OdataOfbizEntity userEntity = (OdataOfbizEntity) actionParameters.get("user");
        GenericValue user = userEntity.getGenericValue();

        String userLoginId = user.getString("userLoginId");
        String enabled = user.getString("enabled");
        String errorMessage = !enabled.equals("N") ? "注销账号失败" : "激活账号失败";
        enabled = !enabled.equals("N") ? "N" : "Y";
        try {
            dispatcher.runSync("banfftech.updateUserLogin", UtilMisc.toMap("userLogin", userLogin, "userLoginId", userLoginId, "enabled", enabled));
        } catch (GenericServiceException e) {
            throw new OfbizODataException(errorMessage);
        }

        return user;
    }

}
