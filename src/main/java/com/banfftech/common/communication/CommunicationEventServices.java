package com.banfftech.common.communication;

import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.mail.MimeMessageWrapper;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.util.*;

public class CommunicationEventServices {

    public static final String module = CommunicationEventServices.class.getName();

    public static Map<String, Object> sendCommEventAsEmail(DispatchContext ctx, Map<String, ? extends Object> context) {
        Delegator delegator = ctx.getDelegator();
        LocalDispatcher dispatcher = ctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Locale locale = (Locale) context.get("locale");

        String communicationEventId = (String) context.get("communicationEventId");

        Map<String, Object> result = ServiceUtil.returnSuccess();
        List<Object> errorMessages = new LinkedList<Object>(); // used to keep a list of all error messages returned from sending emails to contact list

        try {
            // find the communication event and make sure that it is actually an email
            GenericValue communicationEvent = EntityQuery.use(delegator).from("CommunicationEvent").where("communicationEventId", communicationEventId).queryOne();
            if (communicationEvent == null) {
                return ServiceUtil.returnError("Error event: " + communicationEventId);
            }
            String communicationEventType = communicationEvent.getString("communicationEventTypeId");
            if (!("EMAIL_COMMUNICATION".equals(communicationEventType) || "AUTO_EMAIL_COMM".equals(communicationEventType))) {
                return ServiceUtil.returnError("Error event type: " + communicationEventId);
            }

            // assign some default values because required by sendmail and better not make them defaults over there
            if (UtilValidate.isEmpty(communicationEvent.getString("subject"))) {
                communicationEvent.put("subject", " ");
            }
            if (UtilValidate.isEmpty(communicationEvent.getString("content"))) {
                communicationEvent.put("content", " ");
            }

            // prepare the email
            Map<String, Object> sendMailParams = new HashMap<String, Object>();
            sendMailParams.put("sendFrom", communicationEvent.getRelatedOne("FromContactMech", false).getString("infoString"));
            sendMailParams.put("subject", communicationEvent.getString("subject"));
            sendMailParams.put("contentType", communicationEvent.getString("contentMimeTypeId"));
            sendMailParams.put("personal", communicationEvent.getString("headerString"));
            sendMailParams.put("userLogin", userLogin);

            Debug.logInfo("Sending communicationEvent: " + communicationEventId, module);

            // check for attachments
            boolean isMultiPart = false;
            List <GenericValue> comEventContents = EntityQuery.use(delegator).from("CommEventContentAssoc").where("communicationEventId", communicationEventId).filterByDate().queryList();
            if (UtilValidate.isNotEmpty(comEventContents)) {
                isMultiPart = true;
                List<Map<String, ? extends Object>> bodyParts = new LinkedList<Map<String,? extends Object>>();
                if (UtilValidate.isNotEmpty(communicationEvent.getString("content"))) {
                    bodyParts.add(UtilMisc.<String, Object>toMap("content", communicationEvent.getString("content"), "type", communicationEvent.getString("contentMimeTypeId")));
                }
                for (GenericValue comEventContent : comEventContents) {
                    GenericValue content = comEventContent.getRelatedOne("FromContent", false);
                    GenericValue dataResource = content.getRelatedOne("DataResource", false);
                    ByteBuffer dataContent = DataResourceWorker.getContentAsByteBuffer(delegator, dataResource.getString("dataResourceId"), null, null, locale, null);
                    bodyParts.add(UtilMisc.<String, Object>toMap("content", dataContent.array(), "type", dataResource.getString("mimeTypeId"), "filename", dataResource.getString("dataResourceName")));
                }
                sendMailParams.put("bodyParts", bodyParts);
            } else {
                sendMailParams.put("body", communicationEvent.getString("content"));
            }

            // if there is no contact list, then send look for a contactMechIdTo and partyId
            if ((UtilValidate.isEmpty(communicationEvent.getString("contactListId")))) {
                // send to address
                String sendTo = communicationEvent.getString("toString");
                if (UtilValidate.isEmpty(sendTo)) {
                    GenericValue toContactMech = communicationEvent.getRelatedOne("ToContactMech", false);
                    if (toContactMech != null && "EMAIL_ADDRESS".equals(toContactMech.getString("contactMechTypeId"))) {
                        sendTo = toContactMech.getString("infoString");
                    }
                }
                if (UtilValidate.isEmpty(sendTo)) {
                    return ServiceUtil.returnError("Wrong email: " + communicationEventId);
                }
                // add other parties from roles
                String sendCc = null;
                String sendBcc = null;
                List <GenericValue> commRoles = communicationEvent.getRelated("CommunicationEventRole", null, null, false);
                if (UtilValidate.isNotEmpty(commRoles)) {
                    for (GenericValue commRole : commRoles) { // 'from' and 'to' already defined on communication event
                        if (commRole.getString("partyId").equals(communicationEvent.getString("partyIdFrom")) || commRole.getString("partyId").equals(communicationEvent.getString("partyIdTo"))) {
                            continue;
                        }
                        GenericValue contactMech = commRole.getRelatedOne("ContactMech", false);
                        if (contactMech != null && UtilValidate.isNotEmpty(contactMech.getString("infoString"))) {
                            if ("ADDRESSEE".equals(commRole.getString("roleTypeId"))) {
                                sendTo += "," + contactMech.getString("infoString");
                            } else if ("CC".equals(commRole.getString("roleTypeId"))) {
                                if (sendCc != null) {
                                    sendCc += "," + contactMech.getString("infoString");
                                } else {
                                    sendCc = contactMech.getString("infoString");
                                }
                            } else if ("BCC".equals(commRole.getString("roleTypeId"))) {
                                if (sendBcc != null) {
                                    sendBcc += "," + contactMech.getString("infoString");
                                } else {
                                    sendBcc = contactMech.getString("infoString");
                                }
                            }
                        }
                    }
                }

                sendMailParams.put("communicationEventId", communicationEventId);
                sendMailParams.put("sendTo", sendTo);
                if (sendCc != null) {
                    sendMailParams.put("sendCc", sendCc);
                }
                if (sendBcc != null) {
                    sendMailParams.put("sendBcc", sendBcc);
                }
                sendMailParams.put("partyId", communicationEvent.getString("partyIdTo"));  // who it's going to

                // send it - using a new transaction
                Map<String, Object> tmpResult = null;
                if (isMultiPart) {
                    tmpResult = dispatcher.runSync("sendMailMultiPart", sendMailParams, 360, true);
                } else {
                    tmpResult = dispatcher.runSync("sendMail", sendMailParams, 360, true);
                }

                if (ServiceUtil.isError(tmpResult)) {
                    if (ServiceUtil.getErrorMessage(tmpResult).startsWith("[ADDRERR]")) {
                        // address error; mark the communication event as BOUNCED
                        communicationEvent.set("statusId", "COM_BOUNCED");
                        try {
                            communicationEvent.store();
                        } catch (GenericEntityException e) {
                            Debug.logError(e, module);
                            return ServiceUtil.returnError(e.getMessage());
                        }
                    } else {
                        // setup or communication error
                        errorMessages.add(ServiceUtil.getErrorMessage(tmpResult));
                    }
                } else {
                    // set the message ID on this communication event
                    String messageId = (String) tmpResult.get("messageId");
                    communicationEvent.set("messageId", messageId);
                    try {
                        communicationEvent.store();
                    } catch (GenericEntityException e) {
                        Debug.logError(e, module);
                        return ServiceUtil.returnError(e.getMessage());
                    }

                    Map<String, Object> completeResult = dispatcher.runSync("setCommEventComplete", UtilMisc.<String, Object>toMap("communicationEventId", communicationEventId, "partyIdFrom", communicationEvent.getString("partyIdFrom"), "userLogin", userLogin));
                    if (ServiceUtil.isError(completeResult)) {
                        errorMessages.add(ServiceUtil.getErrorMessage(completeResult));
                    }
                }

            } else {
                // Call the sendEmailToContactList service if there's a contactListId present
                Map<String, Object> sendEmailToContactListContext = new HashMap<String, Object>();
                sendEmailToContactListContext.put("contactListId", communicationEvent.getString("contactListId"));
                sendEmailToContactListContext.put("communicationEventId", communicationEventId);
                sendEmailToContactListContext.put("userLogin", userLogin);
                try {
                    dispatcher.runAsync("sendEmailToContactList", sendEmailToContactListContext);
                } catch (GenericServiceException e) {
                    String errMsg = "Fail in send";
                    Debug.logError(e, errMsg, module);
                    errorMessages.add(errMsg);
                    errorMessages.addAll(e.getMessageList());
                }
            }
        } catch (GeneralException eez) {
            return ServiceUtil.returnError(eez.getMessage());
        } catch (IOException eey) {
            return ServiceUtil.returnError(eey.getMessage());
        }

        // If there were errors, then the result of this service should be error with the full list of messages
        if (errorMessages.size() > 0) {
            result = ServiceUtil.returnError(errorMessages);
        }
        return result;
    }


    public static Map<String, Object> setCommEventComplete(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String communicationEventId = (String) context.get("communicationEventId");
        String partyIdFrom = (String) context.get("partyIdFrom");

        try {
            Map<String, Object> result = dispatcher.runSync("banfftech.updateCommunicationEvent", UtilMisc.<String, Object>toMap("communicationEventId", communicationEventId,
                    "partyIdFrom", partyIdFrom, "statusId", "COM_COMPLETE", "userLogin", userLogin));
            if (ServiceUtil.isError(result)) {
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(result));
            }
        } catch (GenericServiceException esx) {
            return ServiceUtil.returnError(esx.getMessage());
        }

        return ServiceUtil.returnSuccess();
    }

    /*
     * Store an outgoing email as a communication event;
     * runs as a pre-invoke ECA on sendMail and sendMultipartMail services
     * - service should run as the 'system' user
     */
    public static Map<String, Object> createCommEventFromEmail(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();
        Delegator delegator = dctx.getDelegator();

        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String subject = (String) context.get("subject");
        String sendFrom = (String) context.get("sendFrom");
        String sendTo = (String) context.get("sendTo");
        String partyId = (String) context.get("partyId");
        String contentType = (String) context.get("contentType");
        String statusId = (String) context.get("statusId");
        String orderId = (String) context.get("orderId");
        if (statusId == null) {
            statusId = "COM_PENDING";
        }

        // get the from contact mech info
        String contactMechIdFrom = null;
        String partyIdFrom = null;
        GenericValue fromCm;
        try {
            fromCm = EntityQuery.use(delegator).from("PartyAndContactMech").where("infoString", sendFrom).orderBy("-fromDate").filterByDate().queryFirst();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }
        if (fromCm != null) {
            contactMechIdFrom = fromCm.getString("contactMechId");
            partyIdFrom = fromCm.getString("partyId");
        }

        // get the to contact mech info
        String contactMechIdTo = null;
        GenericValue toCm;
        try {
            toCm = EntityQuery.use(delegator).from("PartyAndContactMech").where("infoString", sendTo, "partyId", partyId).orderBy("-fromDate").filterByDate().queryFirst();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }
        if (toCm != null) {
            contactMechIdTo = toCm.getString("contactMechId");
        }

        Timestamp now = UtilDateTime.nowTimestamp();

        Map<String, Object> commEventMap = new HashMap<String, Object>();
        commEventMap.put("communicationEventTypeId", "EMAIL_COMMUNICATION");
        commEventMap.put("contactMechTypeId", "EMAIL_ADDRESS");
        commEventMap.put("contactMechIdFrom", contactMechIdFrom);
        commEventMap.put("contactMechIdTo", contactMechIdTo);
        commEventMap.put("statusId", statusId);

        commEventMap.put("partyIdFrom", partyIdFrom);
        commEventMap.put("partyIdTo", partyId);
        commEventMap.put("datetimeStarted", now);
        commEventMap.put("entryDate", now);

        commEventMap.put("subject", subject);
        commEventMap.put("userLogin", userLogin);
        commEventMap.put("contentMimeTypeId", contentType);
        if (UtilValidate.isNotEmpty(orderId)) {
            commEventMap.put("orderId", orderId);
        }

        Map<String, Object> createResult;
        try {
            createResult = dispatcher.runSync("createCommunicationEvent", commEventMap);
        } catch (GenericServiceException e) {
            Debug.logError(e, module);
            return ServiceUtil.returnError(e.getMessage());
        }
        if (ServiceUtil.isError(createResult)) {
            return ServiceUtil.returnError(ServiceUtil.getErrorMessage(createResult));
        }
        String communicationEventId = (String) createResult.get("communicationEventId");

        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("communicationEventId", communicationEventId);
        return result;
    }

    /*
     * Update the communication event with information from the email;
     * runs as a post-commit ECA on sendMail and sendMultiPartMail services
     * - service should run as the 'system' user
     */
    public static Map<String, Object> updateCommEventAfterEmail(DispatchContext dctx, Map<String, ? extends Object> context) {
        LocalDispatcher dispatcher = dctx.getDispatcher();

        GenericValue userLogin = (GenericValue) context.get("userLogin");
        String communicationEventId = (String) context.get("communicationEventId");
        MimeMessageWrapper wrapper = (MimeMessageWrapper) context.get("messageWrapper");

        Map<String, Object> commEventMap = new HashMap<String, Object>();
        commEventMap.put("communicationEventId", communicationEventId);
        commEventMap.put("subject", wrapper.getSubject());
        commEventMap.put("statusId", "COM_COMPLETE");
        commEventMap.put("datetimeEnded", UtilDateTime.nowTimestamp());
        commEventMap.put("entryDate", wrapper.getSentDate());
        commEventMap.put("messageId", wrapper.getMessageId());
        commEventMap.put("userLogin", userLogin);
        commEventMap.put("content", wrapper.getMessageBody());

        // populate the address (to/from/cc/bcc) data
        populateAddressesFromMessage(wrapper, commEventMap);

        // save the communication event
        try {
            dispatcher.runSync("updateCommunicationEvent", commEventMap);
        } catch (GenericServiceException e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        // attachments
        try {
            createAttachmentContent(dispatcher, dctx.getDelegator(), wrapper, communicationEventId, userLogin);
        } catch (GenericServiceException | GenericEntityException e) {
            return ServiceUtil.returnError(e.getMessage());
        }

        return ServiceUtil.returnSuccess();
    }


    private static void populateAddressesFromMessage(MimeMessageWrapper wrapper, Map<String, Object> commEventMap) {
        // Retrieve all the addresses from the email
        Address[] addressesFrom = wrapper.getFrom();
        Address[] addressesTo = wrapper.getTo();
        Address[] addressesCC = wrapper.getCc();
        Address[] addressesBCC = wrapper.getBcc();

        Set<String> emailAddressesFrom = new TreeSet<String>();
        Set<String> emailAddressesTo = new TreeSet<String>();
        Set<String> emailAddressesCC = new TreeSet<String>();
        Set<String> emailAddressesBCC = new TreeSet<String>();
        for (int x = 0 ; x < addressesFrom.length ; x++) {
            emailAddressesFrom.add(((InternetAddress) addressesFrom[x]).getAddress());
        }
        for (int x = 0 ; x < addressesTo.length ; x++) {
            emailAddressesTo.add(((InternetAddress) addressesTo[x]).getAddress());
        }
        if (addressesCC != null) {
            for (int x = 0 ; x < addressesCC.length ; x++) {
                emailAddressesCC.add(((InternetAddress) addressesCC[x]).getAddress());
            }
        }
        if (addressesBCC != null) {
            for (int x = 0 ; x < addressesBCC.length ; x++) {
                emailAddressesBCC.add(((InternetAddress) addressesBCC[x]).getAddress());
            }
        }
        String fromString = StringUtil.join(UtilMisc.toList(emailAddressesFrom), ",");
        String toString = StringUtil.join(UtilMisc.toList(emailAddressesTo), ",");
        String ccString = StringUtil.join(UtilMisc.toList(emailAddressesCC), ",");
        String bccString = StringUtil.join(UtilMisc.toList(emailAddressesBCC), ",");

        if (UtilValidate.isNotEmpty(fromString)) commEventMap.put("fromString", fromString);
        if (UtilValidate.isNotEmpty(toString)) commEventMap.put("toString", toString);
        if (UtilValidate.isNotEmpty(ccString)) commEventMap.put("ccString", ccString);
        if (UtilValidate.isNotEmpty(bccString)) commEventMap.put("bccString", bccString);
    }

    private static List<String> getCommEventAttachmentNames(final Delegator delegator, final String communicationEventId) throws GenericEntityException {
        List<GenericValue> commEventContentAssocList = EntityQuery.use(delegator)
            .from("CommEventContentDataResource")
            .where(EntityCondition.makeCondition("communicationEventId", communicationEventId))
            .filterByDate()
            .queryList();

        List<String> attachmentNames = new ArrayList<String>();
        for (GenericValue commEventContentAssoc : commEventContentAssocList) {
            String dataResourceName = commEventContentAssoc.getString("drDataResourceName");
            attachmentNames.add(dataResourceName);
        }

        return attachmentNames;
    }

    private static void createAttachmentContent(LocalDispatcher dispatcher, Delegator delegator, MimeMessageWrapper wrapper, String communicationEventId, GenericValue userLogin) throws GenericServiceException, GenericEntityException {
        // handle the attachments
        String subject = wrapper.getSubject();
        List<String> attachmentIndexes = wrapper.getAttachmentIndexes();
        List<String> currentAttachmentNames = getCommEventAttachmentNames(delegator, communicationEventId);

        if (attachmentIndexes.size() > 0) {
            Debug.logInfo("=== message has attachments [" + attachmentIndexes.size() + "] =====", module);
            for (String attachmentIdx : attachmentIndexes) {
                String attFileName = wrapper.getPartFilename(attachmentIdx);
                if (currentAttachmentNames.contains(attFileName)) {
                    Debug.logWarning(String.format("CommunicationEvent [%s] already has attachment named '%s'", communicationEventId, attFileName), module);
                    continue;
                }

                Map<String, Object> attachmentMap = new HashMap<String, Object>();
                attachmentMap.put("communicationEventId", communicationEventId);
                attachmentMap.put("contentTypeId", "DOCUMENT");
                attachmentMap.put("mimeTypeId", "text/html");
                attachmentMap.put("userLogin", userLogin);
                if (subject != null && subject.length() > 80) {
                    subject = subject.substring(0,80); // make sure not too big for database field. (20 characters for filename)
                }

                String attContentType = wrapper.getPartContentType(attachmentIdx);
                if (attContentType != null && attContentType.indexOf(";") > -1) {
                    attContentType = attContentType.toLowerCase().substring(0, attContentType.indexOf(";"));
                }

                if (UtilValidate.isNotEmpty(attFileName)) {
                    attachmentMap.put("contentName", attFileName);
                    attachmentMap.put("description", subject + "-" + attachmentIdx);
                } else {
                    attachmentMap.put("contentName", subject + "-" + attachmentIdx);
                }

                attachmentMap.put("drMimeTypeId", attContentType);
                if (attContentType.startsWith("text")) {
                    String text = wrapper.getPartText(attachmentIdx);
                    attachmentMap.put("drDataResourceTypeId", "ELECTRONIC_TEXT");
                    attachmentMap.put("textData", text);
                } else {
                    ByteBuffer data = wrapper.getPartByteBuffer(attachmentIdx);
                    if (Debug.infoOn()) Debug.logInfo("Binary attachment size: " + data.limit(), module);
                    attachmentMap.put("drDataResourceName", attFileName);
                    attachmentMap.put("imageData", data);
                    attachmentMap.put("drDataResourceTypeId", "IMAGE_OBJECT"); // TODO: why always use IMAGE
                    attachmentMap.put("_imageData_contentType", attContentType);
                }

                // save the content
                dispatcher.runSync("createCommContentDataResource", attachmentMap);
            }
        }
    }


}
