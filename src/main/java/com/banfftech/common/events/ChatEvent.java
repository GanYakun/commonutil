package com.banfftech.common.events;

import com.dpbird.odata.OfbizODataException;
import com.dpbird.odata.Util;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.ofbiz.base.util.HttpClientException;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

/**
 * @author scy
 * @date 2023/5/9
 */
public class ChatEvent {

    /**
     * 发送消息
     */
    public static String sendMsg(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        try {
            Map<String, Object> multiPartMap = UtilHttp.getMultiPartParameterMap(request);
            String workEffortId = (String) multiPartMap.get("workEffortId");
            String msgType = (String) multiPartMap.get("msgType");
            String msgData = "image".equals(msgType) ? saveImageChat(request, dispatcher, userLogin, multiPartMap) :
                    (String) multiPartMap.get("msgData");
            //创建一条消息
            dispatcher.runSync("banfftech.createChatMessage", UtilMisc.toMap("messageId", delegator.getNextSeqId("ChatMessage"),
                    "sequence", delegator.getNextSeqIdLong("ChatMessageSequence"), "messageTypeId", msgType, "messageInfo", msgData,
                    "fromPartyId", userLogin.getString("partyId"), "workEffortId", workEffortId, "userLogin", userLogin));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            return "error";
        }
        return "success";
    }

    /**
     * 获取消息列表
     */
    public static String getMsg(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        //workEffortId
        String workEffortId = request.getParameter("workEffortId");
        //序号
        String sequence = request.getParameter("sequence");
        try {
            EntityCondition queryCond = EntityCondition.makeCondition("workEffortId", workEffortId);
            if (UtilValidate.isNotEmpty(sequence)) {
                //只查询大于这个序号的消息
                queryCond = Util.appendCondition(queryCond, EntityCondition.makeCondition("sequence", EntityOperator.GREATER_THAN, Long.parseLong(sequence)));
            }
            List<GenericValue> chatList = EntityQuery.use(delegator).from("ChatMessage").where(queryCond).orderBy("sequence").queryList();
            if (UtilValidate.isEmpty(chatList)) {
                return "success";
            }
            //消息列表
            JSONArray msgArr = new JSONArray();
            for (GenericValue chat : chatList) {
                JSONObject msgJson = new JSONObject();
                msgJson.put("_id", String.valueOf(chat.getLong("sequence")));
                msgJson.put("type", chat.getString("messageTypeId"));
                msgJson.put("content", chat.getString("messageInfo"));
                msgJson.put("createdAt", chat.getTimestamp("createdStamp").getTime());
                msgJson.put("position", userLogin.getString("partyId").equals(chat.getString("fromPartyId")) ? "right" : "left");
                msgJson.put("hasTime", true);
                msgArr.add(msgJson);
            }
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            try(PrintWriter out = response.getWriter()) {
                out.append(msgArr.toString());
            }
            return "success";
        } catch (GenericEntityException | IOException e) {
            e.printStackTrace();
            request.setAttribute("sendResult", "error");
            return "error";
        }
    }


    /**
     * 保存图片 返回图片的odata访问地址
     */
    private static String saveImageChat(HttpServletRequest request, LocalDispatcher dispatcher, GenericValue userLogin, Map<String, Object> multiPartMap) throws GenericServiceException {
        Map<String, Object> createResult = dispatcher.runSync("createDataResource",
                UtilMisc.toMap("userLogin", userLogin, "mimeTypeId", multiPartMap.get("_msgData_contentType")));
        String dataResourceId = (String) createResult.get("dataResourceId");
        // create ImageDataResource
        ByteBuffer fileBuff = (ByteBuffer) multiPartMap.get("msgData");
        dispatcher.runSync("createImageDataResource", UtilMisc.toMap("userLogin", userLogin, "dataResourceId", dataResourceId, "imageData", fileBuff.array()));
        return request.getRequestURL().toString().replace("sendMsg", "odatasvc/mdtManage/ImageDataResources('" + dataResourceId + "')/$value");
    }


//    /**
//     * 获取当前会话 如果是第一次发送给消息则创建会话
//     */
//    private static GenericValue getCurrentChat(Delegator delegator, LocalDispatcher dispatcher, GenericValue userLogin, String workEffortId) throws GenericEntityException, GenericServiceException {
//        Map<String, Object> chatQueryMap = UtilMisc.toMap("workEffortParentId", workEffortId, "workEffortTypeId", "CHAT");
//        GenericValue chatWorkEffort = EntityQuery.use(delegator).from("WorkEffort").where(chatQueryMap).queryFirst();
//        if (UtilValidate.isNotEmpty(chatWorkEffort)) {
//            return chatWorkEffort;
//        }
//        //创建会话
//        Map<String, Object> createChatResult = dispatcher.runSync("banfftech.createWorkEffort", chatQueryMap);
//        String chatId = (String) createChatResult.get("workEffortId");
//        //查询要关联这个会话的人 关联会话
//        GenericValue doctorTeam = EntityQuery.use(delegator).from("WorkEffortPartyAssignment").where("roleTypeId", "DEPARTMENT", "workEffortId", workEffortId).queryFirst();
//        GenericValue patient = EntityQuery.use(delegator).from("WorkEffortPartyAssignment").where("roleTypeId", "patient", "workEffortId", workEffortId).queryFirst();
//        Map<String, Object> createAssignmentParam1 = new HashMap<>(doctorTeam);
//        Map<String, Object> createAssignmentParma2 = new HashMap<>(patient);
//        createAssignmentParam1.putAll(UtilMisc.toMap("userLogin", userLogin, "workEffortId", chatId));
//        createAssignmentParma2.putAll(UtilMisc.toMap("userLogin", userLogin, "workEffortId", chatId));
//        dispatcher.runSync("banfftech.createWorkEffortPartyAssignment", createAssignmentParam1);
//        dispatcher.runSync("banfftech.createWorkEffortPartyAssignment", createAssignmentParma2);
//        return delegator.findOne("WorkEffort", UtilMisc.toMap("workEffortId", chatId), false);
//    }


}
