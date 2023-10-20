import com.dpbird.odata.edm.OdataOfbizEntity;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;


module = "generateFields.groovy"

def generateFields(Map<String, Object> context) {
    List<OdataOfbizEntity> entityList = context.parameters.entityList;
    entityList.each { entity ->
        GenericValue product = (GenericValue) entity.getGenericValue();
        String productId = product.getString("productId");
        List<GenericValue> fixedAssets = EntityQuery.use(delegator).from("FixedAsset").where("instanceOfProductId", productId,"fixedAssetTypeId","EQUIPMENT").queryList();

        entity.addProperty("assetNumber", fixedAssets.size());
    }

    return entityList;
}
