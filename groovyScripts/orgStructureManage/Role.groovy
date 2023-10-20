import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;


module = "generateFields.groovy"

def generateFields(Map<String, Object> context) {
    List<OdataOfbizEntity> entityList = context.parameters.entityList;
    entityList.each { entity ->
        GenericValue roleType = (GenericValue) entity.getGenericValue();
        String roleTypeId = roleType.getString("roleTypeId");
        List<GenericValue> partyRoles = EntityQuery.use(delegator).from("PartyRole").where("roleTypeId", roleTypeId).queryList();
        entity.addProperty("userNumbers", partyRoles.size());
    }

    return entityList;
}
