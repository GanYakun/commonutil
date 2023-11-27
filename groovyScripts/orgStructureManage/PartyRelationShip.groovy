import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilDateTime;
import java.sql.Timestamp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.entity.util.EntityQuery;


module = "generateFields.groovy"

def generateFields(Map<String, Object> context) {
    List<OdataOfbizEntity> entityList = context.parameters.entityList;
    entityList.each { entity ->
        String memberDepNameOrDepName;
        //第一:判断当前PartyRelationship的partyIdFrom是用户组
        GenericValue partyRelationship = (GenericValue) entity.getGenericValue();
        String partyIdFrom = partyRelationship.getString("partyIdFrom");
        GenericValue userGroupPartyRole = EntityQuery.use(delegator).from("PartyRole")
                .where("partyId", partyIdFrom, "roleTypeId", "OTHER_ORGANIZATION_U").queryOne();
        if (UtilValidate.isNotEmpty(userGroupPartyRole)) {
            //第二:判断当前PartyRelationship的partyIdTo是部门还是成员
            GenericValue party = partyRelationship.getRelatedOne("ToParty", false);
            String partyTypeId = party.getString("partyTypeId");
            //如果是部门
            if ("PARTY_GROUP".equals(partyTypeId)) {
                memberDepNameOrDepName = party.getString("partyName");
            }//如果是成员(查询改成员的部门)
            else {
                GenericValue departmentShip = EntityQuery.use(delegator).from("PartyRelationship")
                        .where("partyIdTo", party.getString("partyId"), "roleTypeIdTo", "ORD_EMPLOYEE").queryOne();
                GenericValue department =departmentShip.getRelatedOne("FromParty", false);
                memberDepNameOrDepName = department.getString("partyName");
            }
            entity.addProperty("memberDepNameOrDepName", memberDepNameOrDepName);
        }
    }

    return entityList;
}
