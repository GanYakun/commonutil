package com.banfftech.common.events;

import com.dpbird.odata.Util;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author scy
 * @date 2023/5/9
 */
public class ChatEvent {
    /**
     * 用户头像
     */
    private static final String CUSTOMER_AVATAR = "https://img2.baidu.com/it/u=2370366942,2724449755&fm=253&fmt=auto&app=138&f=JPEG?w=500&h=797";
    /**
     * 专家头像
     */
    private static final String EXPERT_AVATAR = "https://bpic.51yuansu.com/pic3/cover/03/74/75/5bf7e4c23f34d_610.jpg";
    /**
     * 消息状态: 0/用户已读 1/用户未读 2/专家未读
     */
    private static final String C_READ = "0";
    private static final String C_NO_READ = "1";
    private static final String E_NO_READ = "2";

    /**
     * 发送消息
     */
    public static String sendMsg(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        try {
            GenericValue systemUser = Util.getSystemUser(delegator);
            Map<String, Object> multiPartMap = UtilHttp.getMultiPartParameterMap(request);
            String chatId = (String) multiPartMap.get("chatId");
            String msgType = (String) multiPartMap.get("msgType");
            String msgData = "image".equals(msgType) ? saveImageChat(request, dispatcher, systemUser, multiPartMap) :
                    (String) multiPartMap.get("msgData");
            //创建一条消息
            Long msgSequence = delegator.getNextSeqIdLong("ChatMessageSequence");
            dispatcher.runSync("banfftech.createChatMessage", UtilMisc.toMap("chatId", chatId,
                    "sequence", msgSequence, "messageTypeId", msgType, "messageInfo", msgData,
                    "fromPartyId", userLogin.getString("partyId"), "userLogin", systemUser));
            //修改会话状态
            GenericValue role = EntityQuery.use(delegator).from("PartyRole").where("partyId", userLogin.getString("partyId")).queryFirst();
            String msgStatus = "PATIENT".equals(role.getString("roleTypeId")) ? E_NO_READ : C_NO_READ;
            dispatcher.runSync("banfftech.updateChat", UtilMisc.toMap("chatId", chatId, "msgStatus", msgStatus, "userLogin", systemUser));
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            JSONObject result = new JSONObject();
            result.put("msgSequence", msgSequence);
            try (PrintWriter out = response.getWriter()) {
                out.append(result.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
            return "error";
        }
        return "success";
    }

    /**
     * 将会话状态改为用户已读
     */
    public static String readMsg(HttpServletRequest request, HttpServletResponse response) {
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String chatId = (String) request.getAttribute("chatId");
        try {
            GenericValue systemUser = Util.getSystemUser(delegator);
            dispatcher.runSync("banfftech.updateChat", UtilMisc.toMap("chatId", chatId, "msgStatus", C_READ, "userLogin", systemUser));
            request.setAttribute("result", "success");
        } catch (GenericEntityException | GenericServiceException e) {
            e.printStackTrace();
            return "error";
        }
        return "success";
    }

    /**
     * 获取会话列表
     */
    public static String getChatList(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        try {
            JSONObject mainJson = new JSONObject();
            JSONArray chatJsonList = new JSONArray();
            String partyId = userLogin.getString("partyId");
            GenericValue role = EntityQuery.use(delegator).from("PartyRole").where("partyId", partyId).queryFirst();
            if ("PATIENT".equals(role.getString("roleTypeId"))) {
                //患者
                String workEffortId = request.getParameter("workEffortId");
                GenericValue currentChat = getChat(delegator, dispatcher, workEffortId);
                JSONObject chatJson = new JSONObject();
                GenericValue party = userLogin.getRelatedOne("Party", false);
                chatJson.put("title", party.getString("partyName"));
                chatJson.put("chatId", currentChat.getString("chatId"));
                GenericValue lastChat = EntityQuery.use(delegator).from("ChatMessage").where("chatId", currentChat.getString("chatId"))
                        .orderBy("-sequence").queryFirst();
                String dataTime = UtilValidate.isNotEmpty(lastChat) ? lastChat.getTimestamp("createdStamp").toString() : null;
                chatJson.put("dateTime", dataTime);
                String msgStatus = currentChat.getString("msgStatus");
                int noRead = 0;
                if (UtilValidate.isNotEmpty(msgStatus)) {
                    chatJson.elementOpt("msgStatus", currentChat.getString("msgStatus"));
                    noRead = msgStatus.equals(C_NO_READ) ? 1 : 0;
                }
                chatJson.elementOpt("msgStatus", currentChat.getString("msgStatus"));
                chatJsonList.add(chatJson);
                mainJson.put("list", chatJsonList);
                mainJson.put("noRead", noRead);
            } else {
                //获取科室
                GenericValue relationShip = EntityQuery.use(delegator).from("PartyRelationship")
                        .where("partyIdTo", partyId, "roleTypeIdFrom", "DEPARTMENT", "roleTypeIdTo", "DOCTOR").filterByDate().queryFirst();
                //当前科室的所有住院列表
                List<GenericValue> assList = EntityQuery.use(delegator).from("WorkEffortPartyAssignmentWorkEffToDep")
                        .where("partyId", relationShip.getString("partyIdFrom"), "currentStatusId", "BEING_HOSPITALIZED").select("workEffortId").queryList();
                List<String> workEffortIdList = EntityUtil.getFieldListFromEntityList(assList, "workEffortId", true);
                EntityCondition condition = EntityCondition.makeCondition("roleTypeId", "PATIENT");
                condition = Util.appendCondition(condition, EntityCondition.makeCondition("workEffortId", EntityOperator.IN, workEffortIdList));
                List<GenericValue> workEffortPartyAssignments = EntityQuery.use(delegator).from("WorkEffortPartyAssignment").where(condition).queryList();
                int noRead = 0;
                for (GenericValue ass : workEffortPartyAssignments) {
                    String workEffortId = ass.getString("workEffortId");
                    GenericValue currentChat = getChat(delegator, dispatcher, workEffortId);
                    GenericValue lastChat = EntityQuery.use(delegator).from("ChatMessage").where("chatId", currentChat.getString("chatId"))
                            .orderBy("-sequence").queryFirst();
                    if (UtilValidate.isNotEmpty(currentChat)) {
                        JSONObject chatJson = new JSONObject();
                        GenericValue party = ass.getRelatedOne("Party", false);
                        chatJson.put("title", party.getString("partyName"));
                        chatJson.put("chatId", currentChat.getString("chatId"));
                        chatJson.put("workEffortId", workEffortId);
                        chatJson.put("dateTime", lastChat.getTimestamp("createdStamp").toString());
                        String msgStatus = currentChat.getString("msgStatus");
                        if (UtilValidate.isNotEmpty(msgStatus)) {
                            chatJson.elementOpt("msgStatus", currentChat.getString("msgStatus"));
                            if (msgStatus.equals(E_NO_READ)) {
                                noRead++;
                            }
                        }
                        chatJsonList.add(chatJson);
                    }
                }
                mainJson.put("list", chatJsonList);
                mainJson.put("noRead", noRead);
            }
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            try (PrintWriter out = response.getWriter()) {
                out.append(mainJson.toString());
            }
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
        //chatId
        String chatId = request.getParameter("chatId");
        //序号
        String sequence = request.getParameter("sequence");
        try {
            EntityCondition queryCond = EntityCondition.makeCondition("chatId", chatId);
            if (UtilValidate.isNotEmpty(sequence)) {
                //只查询大于这个序号的消息
                queryCond = Util.appendCondition(queryCond, EntityCondition.makeCondition("sequence", EntityOperator.GREATER_THAN, Long.parseLong(sequence)));
            }
            //消息列表
            List<GenericValue> chatList = EntityQuery.use(delegator).from("ChatMessage").where(queryCond).orderBy("sequence").queryList();
            JSONArray msgArr = new JSONArray();
            if (UtilValidate.isNotEmpty(chatList)) {
                for (GenericValue chat : chatList) {
                    msgArr.add(parseMsg(chat, userLogin.getString("partyId"), delegator));
                }
            }
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            try (PrintWriter out = response.getWriter()) {
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
     * 获取消息历史
     */
    public static String getHistoryMsg(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        //chatId
        String chatId = request.getParameter("chatId");
        //序号
        String sequence = request.getParameter("sequence");
        int top = Integer.parseInt(request.getParameter("top"));
        try {
            EntityCondition queryCond = EntityCondition.makeCondition("chatId", chatId);
            if (UtilValidate.isNotEmpty(sequence)) {
                //只查询小于这个序号的消息
                queryCond = Util.appendCondition(queryCond, EntityCondition.makeCondition("sequence", EntityOperator.LESS_THAN, Long.parseLong(sequence)));
            }
            List<GenericValue> chatList = EntityQuery.use(delegator).from("ChatMessage").where(queryCond).orderBy("-sequence").maxRows(top).queryList();
            Collections.reverse(chatList);
            JSONObject msgJson = new JSONObject();
            if (UtilValidate.isEmpty(chatList)) {
                msgJson.put("list", new JSONArray());
                msgJson.put("noMore", true);
            } else {
                //消息列表
                boolean noMore = noMoreChat(delegator, chatList, top);
                JSONArray msgArr = new JSONArray();
                for (GenericValue chat : chatList) {
                    msgArr.add(parseMsg(chat, userLogin.getString("partyId"), delegator));
                }
                msgJson.put("list", msgArr);
                msgJson.put("noMore", noMore);
            }
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json; charset=utf-8");
            try (PrintWriter out = response.getWriter()) {
                out.append(msgJson.toString());
            }
            return "success";
        } catch (GenericEntityException | IOException e) {
            e.printStackTrace();
            request.setAttribute("sendResult", "error");
            return "error";
        }
    }

    /**
     * 是否有更多历史消息
     */
    private static boolean noMoreChat(Delegator delegator, List<GenericValue> chatList, Integer top) throws GenericEntityException {
        if (chatList.size() < top) {
            return true;
        }
        GenericValue minChat = chatList.get(0);
        EntityCondition condition = EntityCondition.makeCondition("chatId", minChat.getString("chatId"));
        condition = Util.appendCondition(condition, EntityCondition.makeCondition("sequence", EntityOperator.LESS_THAN, minChat.getLong("sequence")));
        long count = EntityQuery.use(delegator).from("ChatMessage").where(condition).queryCount();
        return count <= 0;
    }

    /**
     * 获取一个会话 如果没有就创建一个新的会话
     */
    private static GenericValue getChat(Delegator delegator, LocalDispatcher dispatcher, String workEffortId) throws GenericEntityException, GenericServiceException {
        GenericValue chat = EntityQuery.use(delegator).from("Chat").where("workEffortId", workEffortId).queryFirst();
        if (UtilValidate.isNotEmpty(chat)) {
            return chat;
        }
        //创建会话
        String chatId = delegator.getNextSeqId("Chat");
        GenericValue systemUser = Util.getSystemUser(delegator);
        dispatcher.runSync("banfftech.createChat", UtilMisc.toMap("workEffortId", workEffortId, "chatId", chatId, "userLogin", systemUser));

        //查询要关联这个会话的人 关联会话
        GenericValue doctorTeam = EntityQuery.use(delegator).from("WorkEffortPartyAssignment").where("roleTypeId", "DEPARTMENT", "workEffortId", workEffortId).queryFirst();
        GenericValue patient = EntityQuery.use(delegator).from("WorkEffortPartyAssignment").where("roleTypeId", "PATIENT", "workEffortId", workEffortId).queryFirst();
        Map<String, Object> serviceParam = UtilMisc.toMap("chatId", chatId, "userLogin", systemUser, "partyId", doctorTeam.getString("partyId"), "roleTypeId", "DEPARTMENT");
        dispatcher.runSync("banfftech.createChatParty", serviceParam);
        serviceParam.put("partyId", patient.getString("partyId"));
        serviceParam.put("roleTypeId", "DEPARTMENT");
        dispatcher.runSync("banfftech.createChatParty", serviceParam);
        return delegator.findOne("Chat", UtilMisc.toMap("chatId", chatId), false);
    }

    /**
     * 获取一个消息的json
     */
    private static JSONObject parseMsg(GenericValue msg, String partyId, Delegator delegator) throws GenericEntityException {
        JSONObject msgJson = new JSONObject();
        GenericValue role = EntityQuery.use(delegator).from("PartyRole").where("partyId", partyId).queryFirst();
        String roleTypeId = role.getString("roleTypeId");

        msgJson.put("_id", String.valueOf(msg.getLong("sequence")));
        String msgType = msg.getString("messageTypeId");
        msgJson.put("type", msgType);
        JSONObject contentJson = new JSONObject();
        if ("text".equals(msgType)) {
            contentJson.put("text", msg.getString("messageInfo"));
        }
        if ("image".equals(msgType)) {
            contentJson.put("picUrl", msg.getString("messageInfo"));
        }
        JSONObject userJson = new JSONObject();
        boolean isMe = partyId.equals(msg.getString("fromPartyId"));
        if ("PATIENT".equals(roleTypeId) && !isMe) {
            userJson.put("avatar", EXPERT_AVATAR);
        } else if ("DOCTOR".equals(roleTypeId) && !isMe) {
            userJson.put("avatar", CUSTOMER_AVATAR);
        }
        msgJson.put("user", userJson);
        msgJson.put("content", contentJson);
        msgJson.put("createdAt", msg.getTimestamp("createdStamp").getTime());
        msgJson.put("position", isMe ? "right" : "left");
        msgJson.put("hasTime", true);
        return msgJson;
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
        String currentUrl = request.getRequestURL().toString();
        if (!currentUrl.startsWith("https")) {
            currentUrl = currentUrl.replace("http", "https");
        }
        return currentUrl.replace("sendMsg", "odatasvc/mdtManage/ImageDataResources('" + dataResourceId + "')/$value");
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
