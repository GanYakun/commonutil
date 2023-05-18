package com.banfftech.common.services;

import com.banfftech.common.util.CommonUtils;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.model.ModelEntity;
import org.apache.ofbiz.entity.model.ModelField;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.*;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommonServices {
    public static Map<String, Object> createPostalAddressAndContactMech(DispatchContext dctx, Map<String, Object> context)
            throws GenericServiceException {
        try {
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createContactMech",
                    (GenericValue) context.get("userLogin"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createPostalAddress",
                    (GenericValue) context.get("userLogin"));
        } catch (GeneralServiceException | GenericServiceException e) {
            throw new GenericServiceException(e.getMessage());
        }
        
        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> createPartyUserLogin(DispatchContext dctx, Map<String, Object> context)
            throws GenericEntityException {
        try {
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createParty",
                    (String) context.get("userLoginId"));
            context.put("enabled", "Y");
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createUserLogin",
                    (String) context.get("userLoginId"));
        } catch (GeneralServiceException | GenericServiceException | GenericEntityException e) {
            throw new GenericEntityException(e.getMessage());
        }

        return ServiceUtil.returnSuccess();
    }

    public static Map<String, Object> updatePartyUserLogin(DispatchContext dctx, Map<String, Object> context)
            throws GenericServiceException {
        try {
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateParty",
                    (String) context.get("userLoginId"));
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateUserLogin",
                    (String) context.get("userLoginId"));
        } catch (GeneralServiceException | GenericEntityException | GenericServiceException e) {
            throw new GenericServiceException(e.getMessage());
        }

        return ServiceUtil.returnSuccess();
    }

    /**
     * 通用的文件上传
     */
    public static Map<String, Object> uploadFile(DispatchContext dctx, Map<String, Object> context)
            throws OfbizODataException {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Locale locale = (Locale) context.get("locale");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        //文件数据
        Map<String, Object> mediaInfo = UtilGenerics.checkMap(context.get("multiFrom"));
        //文件
        ByteBuffer fileBuff = (ByteBuffer) mediaInfo.get("file");
        //文件名称
        String fileName = (String) mediaInfo.get("_file_fileName");
        //文件类型
        String fileType = (String) mediaInfo.get("_file_contentType");
        //主键
        Map<String, Object> primaryKey = UtilGenerics.checkMap(context.get("key"));
        List<String> relation = UtilGenerics.checkList(context.get("relation"));
        //关联实体
        String entityName = relation.get(0);
        String mediaEntityName = relation.get(relation.size() - 1);

        String relContentType = (String) context.get("relContentType");
        try {
            GenericValue media = findMedia(delegator, primaryKey, relation);
            if (UtilValidate.isNotEmpty(media)) {
                //存在文件 更新文件内容
                String updateMedia = Util.getEntityActionService(null, media.getEntityName(), "update", delegator);
                dispatcher.runSync(updateMedia, UtilMisc.toMap("userLogin", userLogin, "dataResourceId", media.getString("dataResourceId"), "imageData", fileBuff.array()));
                //更新文件名称、类型
                String updateResource = Util.getEntityActionService(null, "DataResource", "update", delegator);
                dispatcher.runSync(updateResource, UtilMisc.toMap("userLogin", userLogin, "dataResourceId", media.getString("dataResourceId"),
                        "dataResourceName", fileName, "mimeTypeId", fileType));
                return ServiceUtil.returnSuccess();
            }
            // 创建文件
            // 场景1: FacilityContent/Content/DataResource/ImageDataResource
            String imageResourceService = Util.getEntityActionService(null, mediaEntityName, "create", delegator);
            String resourceService = Util.getEntityActionService(null, "DataResource", "create", delegator);
            // create DataResource
            Map<String, Object> createResult = dispatcher.runSync(resourceService,
                    UtilMisc.toMap("userLogin", userLogin, "mimeTypeId", fileType, "dataResourceName", fileName));
            String dataResourceId = (String) createResult.get("dataResourceId");
            // create ImageDataResource
            dispatcher.runSync(imageResourceService, UtilMisc.toMap("userLogin", userLogin, "dataResourceId", dataResourceId, getByteField(delegator, mediaEntityName), fileBuff.array()));
            //起源是Content 直接更新Content
            if (entityName.endsWith("Content")) {
                String contentService = Util.getEntityActionService(null, "Content", "update", delegator);
                dispatcher.runSync(contentService, UtilMisc.toMap("userLogin", userLogin, "contentId", primaryKey.get("contentId"), "dataResourceId", dataResourceId));
                return ServiceUtil.returnSuccess();
            }
            // 场景2: Facility/FacilityContent/Content/DataResource/ImageDataResource 创建Content和中间对象
            String contentService = Util.getEntityActionService(null, "Content", "create", delegator);
            String relContentService = Util.getEntityActionService(null, entityName + "Content", "create", delegator);
            // create Content
            createResult = dispatcher.runSync(contentService, UtilMisc.toMap("userLogin", userLogin, "dataResourceId", dataResourceId));
            // create Rel Content
            primaryKey.put("userLogin", userLogin);
            primaryKey.put("contentId", createResult.get("contentId"));
            primaryKey.put("fromDate", UtilDateTime.nowTimestamp());
            primaryKey.put(Util.firstLowerCase(entityName + "ContentTypeId"), relContentType);
            Map<String, Object> serviceParam = ServiceUtil.setServiceFields(dispatcher, relContentService, primaryKey, userLogin, null, locale);
            dispatcher.runSync(relContentService, serviceParam);
        } catch (GeneralException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        return ServiceUtil.returnSuccess();
    }

    //查询上传文件传递的关系链 看存不存在图片
    private static GenericValue findMedia(Delegator delegator, Map<String, Object> primaryKey, List<String> relation) throws GenericEntityException {
        GenericValue genericValue = delegator.findOne(relation.get(0), primaryKey, true);
        for (int i = 1; i < relation.size(); i++) {
            genericValue = EntityUtil.getFirst(genericValue.getRelated(relation.get(i), null, null, true));
            if (UtilValidate.isEmpty(genericValue)) {
                return null;
            }
        }
        return genericValue;
    }

    private static String getByteField(Delegator delegator, String mediaEntity) {
        ModelEntity modelEntity = delegator.getModelEntity(mediaEntity);
        Iterator<ModelField> fieldsIterator = modelEntity.getFieldsIterator();
        while (fieldsIterator.hasNext()) {
            ModelField next = fieldsIterator.next();
            if ("byte-array".equals(next.getType())) {
                return next.getName();
            }
        }
        return null;
    }


}
