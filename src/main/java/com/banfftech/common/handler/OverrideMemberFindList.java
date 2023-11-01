package com.banfftech.common.handler;

import com.banfftech.common.util.PartyServiceUtils;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.dpbird.odata.edm.OdataOfbizEntity;
import com.dpbird.odata.handler.DefaultEntityHandler;
import com.dpbird.odata.handler.HandlerResults;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityExpr;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.olingo.commons.api.edm.EdmBindingTarget;
import org.apache.olingo.commons.api.edm.EdmNavigationProperty;
import org.apache.olingo.server.api.uri.queryoption.FilterOption;
import org.apache.olingo.server.api.uri.queryoption.QueryOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                //处理filter
                int count;
                FilterOption filterOption = (FilterOption) queryOptions.get("filterOption");
                if (UtilValidate.isNotEmpty(filterOption)) {
                    String filterString = ((FilterOption) queryOptions.get("filterOption")).getExpression().toString();
                    Pattern pattern = Pattern.compile("'.*?'");
                    Matcher matcher = pattern.matcher(filterString);
                    String filterValue = null;
                    while (matcher.find()) {
                        filterValue = matcher.group().replaceAll("'", "");
                    }
                    if (UtilValidate.isNotEmpty(filterValue)) {
                        EntityCondition queryCondition = EntityCondition.makeCondition("partyName", EntityOperator.LIKE, "%" + filterValue + "%");
                        allMembers = EntityUtil.filterByCondition(allMembers, queryCondition);
                    }
                }
                count = allMembers.size();
                //处理分页
                int top = Util.getTopOption(queryOptions);
                int skip = Util.getSkipOption(queryOptions);
                if ((skip + top) > allMembers.size()) {
                    if (allMembers.size() <= skip) {
                        allMembers = new ArrayList<>();
                    } else {
                        allMembers = new ArrayList<>(allMembers.subList(skip, allMembers.size()));
                    }
                } else {
                    allMembers = new ArrayList<>(allMembers.subList(skip, skip + top));
                }
                return new HandlerResults(count, allMembers);
            }
        }
        //否则(如果是一段式或者NavigationName!=AllMember)直接调用Super
        return super.findList(odataContext, edmBindingTarget, primaryKey, queryOptions, navigationParam);


    }

}
