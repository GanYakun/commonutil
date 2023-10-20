import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;


module = "generateFields.groovy"

def generateFields(Map<String, Object> context) {
    List<OdataOfbizEntity> entityList = context.parameters.entityList;
    entityList.each { entity ->
        Boolean isHiddenSubmitApproval = true;
        Boolean isHiddenCancelApproval = true;
        GenericValue leaveApproval = (GenericValue) entity.getGenericValue();
        String leaveStatus = leaveApproval.getString("statusId");
        if("APPROVAL_CREATED".equals(leaveStatus) ||"APPROVAL_REJECTED".equals(leaveStatus)) {
            isHiddenSubmitApproval = false;
        }
        if("APPROVAL_SUBMITTED".equals(leaveStatus)) {
            isHiddenCancelApproval = false;
        }

        entity.addProperty("isHiddenSubmitApproval", isHiddenSubmitApproval);
        entity.addProperty("isHiddenCancelApproval", isHiddenCancelApproval);
    }

    return entityList;
}
