package com.banfftech.common.util;

import com.dpbird.odata.OfbizODataException;

import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class InventoryItemUtils {

    /**
     *  根据条件扣减库存量
     *
     * @param deductQuantity 需要扣减的实际数量
     * @param productId 产品标识
     * @param userLogin userLogin
     * @param workEffortId 生产标识
     * @param handTotal 是否扣减占用量
     */
    public static void inventoryDeduction(BigDecimal deductQuantity, String productId,
                                          GenericValue userLogin, Delegator delegator, Boolean handTotal,
                                          String workEffortId, LocalDispatcher dispatcher){
        try {
            List<GenericValue> inventoryItems = delegator.findByAnd("InventoryItem",
                    UtilMisc.toMap("productId", productId), null, false);
            String inventoryQuantity = handTotal ? "quantityOnHandTotal" : "availableToPromiseTotal";
            String inventoryDetailQuantity = handTotal ? "quantityOnHandDiff" : "availableToPromiseDiff";
            Map <String, BigDecimal> realityDeductQuantity = handTotal ?
                    UtilMisc.toMap("quantityOnHandDiff", BigDecimal.ZERO.subtract(deductQuantity), "availableToPromiseDiff", null) :
                    UtilMisc.toMap("quantityOnHandDiff", null, "availableToPromiseDiff", BigDecimal.ZERO.subtract(deductQuantity));
            //如果原料库存项为空则创建库存项并扣减实际投料量
            if (UtilValidate.isEmpty(inventoryItems)){
                InventoryItemUtils.createInventoryItemAndDetail(productId, "MATERIAL-WH",null,
                        workEffortId, null, null, realityDeductQuantity.get("quantityOnHandDiff"),
                        realityDeductQuantity.get("availableToPromiseDiff"), dispatcher, userLogin);
            }else{
                //如果不为空则循环扣减库存项
                for (int i = 0; i < inventoryItems.size(); i++){

                    BigDecimal inventoryItemQuantity = inventoryItems.get(i).getBigDecimal(inventoryQuantity);
                    String inventoryItemId = (String) inventoryItems.get(i).get("inventoryItemId");
                    //如果是最后一个库存项或库存占有量大于实际投料量则直接扣减
                    if ( i == inventoryItems.size()-1 || inventoryItemQuantity.compareTo(deductQuantity) == 1 || inventoryItemQuantity.compareTo(deductQuantity) == 0){
                        deductQuantity = BigDecimal.ZERO.subtract(deductQuantity);
                        dispatcher.runSync("banfftech.createInventoryItemDetail",
                                UtilMisc.toMap(inventoryDetailQuantity, deductQuantity,
                                        "inventoryItemId", inventoryItemId, "workEffortId", workEffortId, "userLogin", userLogin));
                        break;
                    }
                    //否则将库存占有量作为扣减量，扣减为零后继续循环下一个库存项
                    else {
                        inventoryItemQuantity = BigDecimal.ZERO.subtract(inventoryItemQuantity);
                        dispatcher.runSync("banfftech.createInventoryItemDetail",
                                UtilMisc.toMap(inventoryDetailQuantity, inventoryItemQuantity,
                                        "inventoryItemId", inventoryItemId, "workEffortId", workEffortId, "userLogin", userLogin));
                        deductQuantity = deductQuantity.add(inventoryItemQuantity);
                    }
                }
            }
        }
        catch (GenericEntityException | GenericServiceException | OfbizODataException e){
            e.printStackTrace();
        }
    }

    /**
     * 根据条件创建或更新货运项，并创建货运项明细
     * @param productId 产品标识
     * @param facilityId 场所标识 注：若货运项标识及场所标识不为空，则更新货运项
     * @param lotId 批次号
     * @param workEffortId 生产标识
     * @param shipmentId 生产标识
     * @param shipmentItemSeqId 生产标识
     * @param quantityOnHandDiff 可用库存
     * @param availableToPromiseDiff 承诺库存
     * @param dispatcher dispatcher
     * @param userLogin userLogin
     */
    public static void createInventoryItemAndDetail(String productId, String facilityId, String lotId,
                                                    String workEffortId, String shipmentId, String shipmentItemSeqId, BigDecimal quantityOnHandDiff,
                                                    BigDecimal availableToPromiseDiff, LocalDispatcher dispatcher, GenericValue userLogin) throws GenericServiceException, GenericEntityException, OfbizODataException {

        Delegator delegator = dispatcher.getDelegator();
        GenericValue lot = delegator.findOne("Lot", true, UtilMisc.toMap("lotId", lotId));
        if (UtilValidate.isNotEmpty(lotId) && UtilValidate.isEmpty(lot)){
            dispatcher.runSync("banfftech.createLot", UtilMisc.toMap("lotId", lotId, "createDate", UtilDateTime.nowTimestamp(), "userLogin", userLogin));
        }
        Map<String,Object> createInventoryResultMap = dispatcher.runSync("banfftech.createInventoryItem",
                   UtilMisc.toMap("productId", productId, "userLogin", userLogin, "facilityId", facilityId, "lotId", lotId));
        String newInventoryItemId = (String) createInventoryResultMap.get("inventoryItemId");
        onlyCreateInventoryItemDetail(newInventoryItemId, availableToPromiseDiff, quantityOnHandDiff, workEffortId, shipmentId, shipmentItemSeqId, dispatcher, userLogin);
    }

    public static void onlyCreateInventoryItemDetail (String inventoryItemId, BigDecimal availableToPromiseDiff,
                                                      BigDecimal quantityOnHandDiff,String workEffortId, String shipmentId,
                                                      String shipmentItemSeqId,  LocalDispatcher dispatcher, GenericValue userLogin) throws GenericServiceException {

        dispatcher.runSync("banfftech.createInventoryItemDetail", UtilMisc.toMap("inventoryItemId", inventoryItemId,
                "availableToPromiseDiff", availableToPromiseDiff, "quantityOnHandDiff", quantityOnHandDiff, "workEffortId", workEffortId,
                "shipmentId", shipmentId, "shipmentItemSeqId", shipmentItemSeqId, "userLogin", userLogin));
    }

    public static void updateInventoryItem (String inventoryItemId, String facilityId, String lotId, LocalDispatcher dispatcher, GenericValue userLogin) throws GenericServiceException {

        dispatcher.runSync("banfftech.updateInventoryItem", UtilMisc.toMap( "userLogin", userLogin,
                "inventoryItemId", inventoryItemId, "facilityId", facilityId, "lotId", lotId));
    }
}
