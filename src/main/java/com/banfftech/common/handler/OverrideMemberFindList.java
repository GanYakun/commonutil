package com.banfftech.common.handler;

import com.banfftech.common.util.PartyServiceUtils;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.DefaultEntityHandler;
import com.dpbird.odata.handler.HandlerResults;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @ClassName: OverrideEnumerationFindList
 * @Description: 主要作用: 重写findLis,查询发回自定义内容
 * @Author: banff
 * @Date: 2023/8/10 12:16
 */
public class OverrideMemberFindList extends DefaultEntityHandler {

    @Override
    public HandlerResults findList(Map<String, Object> odataContext, EdmBindingTarget edmBindingTarget, Map<String, Object> primaryKey,
                                   Map<String, QueryOption> queryOptions, Map<String, Object> navigationParam) throws OfbizODataException {
        Delegator delegator = (Delegator) odataContext.get("delegator");

        //如果不是一段式并且NavigationName=AllMember
        if (UtilValidate.isNotEmpty(navigationParam)) {
            EdmNavigationProperty edmNavigationProperty = (EdmNavigationProperty) navigationParam.get("edmNavigationProperty");
            String navigationPropertyName = edmNavigationProperty.getName();
            //当NavigationName=AllMember时,递归查询当前部门及其所有子部门的成员
            if ("AllMember".equals(navigationPropertyName)) {
                OdataOfbizEntity entity = (OdataOfbizEntity) navigationParam.get("entity");
                GenericValue genericValue = entity.getGenericValue();
                String departmentId = genericValue.getString("partyId");

                List<GenericValue> allMembers = new ArrayList<>();
                PartyServiceUtils.getDepartmentALlMembers(delegator, departmentId, allMembers);
                return new HandlerResults(allMembers.size(), allMembers);
            }
        }
        //否则(如果是一段式或者NavigationName!=AllMember)直接调用Super
        return super.findList(odataContext, edmBindingTarget, primaryKey, queryOptions, navigationParam);


    }

}
