package com.banfftech.common.services;

import com.banfftech.common.tools.util.CommonUtils;
import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
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

        String relContentTypeIdName = (String) context.get("relContentTypeIdName");
        String relContentTypeIdValue = (String) context.get("relContentTypeIdValue");
        Map<String, Object> queryMap = null;
        if (UtilValidate.isNotEmpty(relContentTypeIdName)) {
            queryMap = UtilMisc.toMap(relContentTypeIdName, relContentTypeIdValue);
        }
        try {
            GenericValue media = findMedia(delegator, primaryKey, queryMap, relation);
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
            if (UtilValidate.isNotEmpty(media)) {
                //已经存在的文件 更新content的dataResourceId
                delegator.storeByCondition("Content", UtilMisc.toMap("dataResourceId", dataResourceId), EntityCondition.makeCondition("dataResourceId", media.getString("dataResourceId")));
                return ServiceUtil.returnSuccess();
            }
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
            if (UtilValidate.isNotEmpty(relContentTypeIdName)) {
                primaryKey.put(relContentTypeIdName, relContentTypeIdValue);
            }
            Map<String, Object> serviceParam = ServiceUtil.setServiceFields(dispatcher, relContentService, primaryKey, userLogin, null, locale);
            dispatcher.runSync(relContentService, serviceParam);
        } catch (GeneralException e) {
            e.printStackTrace();
            throw new OfbizODataException(e.getMessage());
        }
        return ServiceUtil.returnSuccess();
    }

    //查询上传文件传递的关系链 看存不存在图片
    private static GenericValue findMedia(Delegator delegator, Map<String, Object> primaryKey, Map<String, Object> byAnd, List<String> relation) throws GenericEntityException {
        GenericValue genericValue = delegator.findOne(relation.get(0), primaryKey, true);
        for (int i = 1; i < relation.size(); i++) {
            String currRelation = relation.get(i);
            List<GenericValue> related;
            if (UtilValidate.isNotEmpty(byAnd) && currRelation.equals(genericValue.getEntityName() + "Content")) {
                related = genericValue.getRelated(currRelation, byAnd, null, true);
            } else {
                related = genericValue.getRelated(currRelation, null, null, true);
            }
            genericValue = EntityUtil.getFirst(related);
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

    public static Map<String, Object> createContentAndMediaDataResource(DispatchContext dctx, Map<String, Object> context) throws GeneralServiceException, GenericServiceException, GenericEntityException {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue systemUser = Util.getSystemUser(delegator);
        //create DataResource
        Map<String, Object> result = CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createDataResource", systemUser);
        String dataResourceId = (String) result.get("dataResourceId");

        //create ImageDataResource
        Map<String, Object> createMediaParam = UtilMisc.toMap("dataResourceId", dataResourceId, "userLogin", systemUser);
        if (context.containsKey("imageData")) {
            createMediaParam.put("imageData", context.get("imageData"));
            dispatcher.runSync("banfftech.createImageDataResource", createMediaParam);
        }
        if (context.containsKey("videoData")) {
            createMediaParam.put("videoData", context.get("videoData"));
            dispatcher.runSync("banfftech.createVideoDataResource", createMediaParam);
        }
        if (context.containsKey("audioData")) {
            createMediaParam.put("audioData", context.get("audioData"));
            dispatcher.runSync("banfftech.createAudioDataResource", createMediaParam);
        }
        String contentId = (String) context.get("contentId");
        if (UtilValidate.isEmpty(contentId)) {
            contentId = delegator.getNextSeqId("Content");
        }
        context.put("contentId", contentId);
        context.put("dataResourceId", dataResourceId);
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.createContent", systemUser);
        Map<String, Object> resultMap = ServiceUtil.returnSuccess();
        resultMap.put("contentId", contentId);
        return resultMap;
    }

    public static Map<String, Object> updateContentAndMediaDataResource(DispatchContext dctx, Map<String, Object> context) throws GenericEntityException, GeneralServiceException, GenericServiceException {
        Delegator delegator = dctx.getDelegator();
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue systemUser = Util.getSystemUser(delegator);
        String contentId = (String) context.get("contentId");
        CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateContent", systemUser);
        GenericValue content = delegator.findOne("Content",UtilMisc.toMap("contentId", contentId), true);
        String dataResourceId = content.getString("dataResourceId");
        if (UtilValidate.isNotEmpty(dataResourceId)) {
            context.put("dataResourceId", dataResourceId);
            CommonUtils.setServiceFieldsAndRun(dctx, context, "banfftech.updateDataResource", systemUser);
            //update ImageDataResource
            Map<String, Object> updateMediaParam = UtilMisc.toMap("dataResourceId", dataResourceId, "userLogin", systemUser);
            if (context.containsKey("imageData")) {
                updateMediaParam.put("imageData", context.get("imageData"));
                GenericValue imageDataResource = delegator.findOne("ImageDataResource", UtilMisc.toMap("dataResourceId", dataResourceId), false);
                String service = UtilValidate.isEmpty(imageDataResource) ? "banfftech.createImageDataResource" : "banfftech.updateImageDataResource";
                dispatcher.runSync(service, updateMediaParam);
            }
            if (UtilValidate.isNotEmpty(context.get("videoData"))) {
                updateMediaParam.put("videoData", context.get("videoData"));
                GenericValue videoDataResource = delegator.findOne("VideoDataResource", UtilMisc.toMap("dataResourceId", dataResourceId), false);
                String service = UtilValidate.isEmpty(videoDataResource) ? "banfftech.createVideoDataResource" : "banfftech.updateVideoDataResource";
                dispatcher.runSync(service, updateMediaParam);
            }
            if (UtilValidate.isNotEmpty(context.get("audioData"))) {
                updateMediaParam.put("audioData", context.get("audioData"));
                GenericValue audioDataResource = delegator.findOne("AudioDataResource", UtilMisc.toMap("dataResourceId", dataResourceId), false);
                String service = UtilValidate.isEmpty(audioDataResource) ? "banfftech.createAudioDataResource" : "banfftech.updateAudioDataResource";
                dispatcher.runSync(service, updateMediaParam);
            }
        }
        Map<String, Object> map = ServiceUtil.returnSuccess();
        map.put("contentId", contentId);
        return map;
    }


}
