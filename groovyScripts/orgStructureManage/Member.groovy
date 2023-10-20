import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.base.util.UtilDateTime;
import java.sql.Timestamp;
import org.apache.ofbiz.base.util.UtilMisc;


module = "generateFields.groovy"

def generateFields(Map<String, Object> context) {
    List<OdataOfbizEntity> entityList = context.parameters.entityList;
    entityList.each { entity ->
        GenericValue department = (GenericValue) entity.getGenericValue();
        Boolean isManager = false;
        Boolean isNotManager = true;
        String partyId = department.getString("partyId");
        GenericValue managerRole = delegator.findOne("PartyRole",
                UtilMisc.toMap("partyId", partyId, "roleTypeId", "MANAGER"), false);
        if (UtilValidate.isNotEmpty(managerRole)) {
            isManager = true;
            isNotManager = false;
        }
        entity.addProperty("isManager", isManager);
        entity.addProperty("isNotManager", isNotManager);
    }

    return entityList;
}
