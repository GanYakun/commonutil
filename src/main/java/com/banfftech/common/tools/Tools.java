package com.banfftech.common.tools;


import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;


public class Tools {

    private static Map<String, Map<String, Object>> xmlMap = new HashMap<>();
    private final static String appPath = "plugins/bfui5/o3/apps/";
    private final static String xmlPath = "plugins/dinstitute/config/dinstituteUiLabels.xml";
    private final static Map<String,String> propertyMap = new LinkedHashMap<>();
    static {
        propertyMap.put("en","i18n.properties");
        propertyMap.put("zh","i18n_zh_CN.properties");
    }

    public static Map<String,Object> updateI18nProperties(DispatchContext dctx, Map<String, ? extends Object> context) throws IOException {
        Map<String,Object> map = ServiceUtil.returnSuccess();
        Boolean initialize = (Boolean) context.get("initialize");
        if( UtilValidate.isEmpty(initialize)){
            initialize = false;
        }

        xmlToMap(xmlPath);

        //写入properties
        File file = new File(appPath);
        String[] files = file.list();
        if( UtilValidate.isEmpty(files)){
            throw new NullPointerException("There are no files under the folder");
        }
        for (String appName : files) {
            String i18nPath = readfile(appPath + appName);
            Debug.log("i18nPath = " + i18nPath );

            if(UtilValidate.isNotEmpty(i18nPath)){
                LinkedProperties englishProperty = null;

                for(String language:propertyMap.keySet()){
                    String propertyPath = i18nPath + propertyMap.get(language);
                    if( language.equals("en") ){
                        englishProperty = createOrUpdate(englishProperty,initialize,propertyPath,language);
                    }else {
                        createOrUpdate(englishProperty,initialize,propertyPath,language);
                    }
                }

            }
        }

        return map;
    }

    public static void xmlToMap(String xmlPath){
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(xmlPath);//传入文件名可以是相对路径也可以是绝对路径
            NodeList bookList = document.getElementsByTagName("property");
            for (int i = 0; i < bookList.getLength(); i++) {
                Element book1 = (Element) bookList.item(i);

                String attrValue = book1.getAttribute("key");

                Map<String, Object> map = new HashMap<>();
                NodeList childNodes = book1.getChildNodes();
                for (int k = 0; k < childNodes.getLength(); k++) {
                    //区分出text类型的node以及element类型的node
                    if (childNodes.item(k).getNodeType() == Node.ELEMENT_NODE) {
                        String key = childNodes.item(k).getAttributes().getNamedItem("xml:lang").getNodeValue();
                        String value = childNodes.item(k).getTextContent();
                        map.put(key, value);
                    }
                }
                xmlMap.put(attrValue, map);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static LinkedProperties createOrUpdate(LinkedProperties englishProperty,Boolean initialize,String path,String language) throws IOException {
        LinkedProperties currentProperties = null;
        LinkedProperties newProperties = new LinkedProperties();
        OutputStreamWriter writer = null;
        FileOutputStream outputFile = null;


        try {
            //读取当前的properties文件
            currentProperties = (LinkedProperties) loadProps(path);
            if( language.equals("en") ){
                englishProperty = currentProperties;
            }
            //遍历英语properties的key
            for (String key : englishProperty.stringPropertyNames() ){
                String value = englishProperty.getProperty(key);
                //保存改变的键值对
                if( UtilValidate.isNotEmpty(currentProperties.getProperty(key)) ){
                    value = currentProperties.getProperty(key);
                }
                //初始化被改变的UiLabel中的键值对
                if(initialize || UtilValidate.isEmpty(value) ||  UtilValidate.isEmpty(currentProperties.getProperty(key))  ){
                    //是label.xml中的键值对
                    if(  UtilValidate.isNotEmpty(xmlMap.get(key)) &&  UtilValidate.isNotEmpty(xmlMap.get(key).get(language)) ){
                        value = (String) xmlMap.get(key).get(language);
                    }
                }
                //value依然为空时，说明是自定义键值对，或者xml中没有该语言的翻译
                if( UtilValidate.isEmpty(value) ){
                    value = key;
                }
                newProperties.setProperty(key,value);
            }

            outputFile = new FileOutputStream(path);
            writer = new OutputStreamWriter(outputFile, StandardCharsets.UTF_8);
            newProperties.store(writer, "modify");

        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try {
                writer.close();
                outputFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return newProperties;
    }

    public static String readfile(String filepath) throws FileNotFoundException, IOException {
        String resPath = null;
        try {
            File file = new File(filepath);
            String[] filelist = file.list();
            for (String dir : filelist) {
                File readfile = new File(filepath + "\\" + dir);
                if (readfile.isDirectory()) {
                    String path = readfile(filepath + "\\" + dir);
                    if ( UtilValidate.isNotEmpty(path) ) {
                        return path;
                    }
                }else{
                    if( readfile.getName().equals("i18n.properties")){
                        return readfile.getParent() + "\\";
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("readfile()   Exception:" + e.getMessage());
        }
        return null;
    }

    public static LinkedProperties loadProps(String path) throws IOException {
        //判断Properties文件是否存在，不存在就创建
        File folder = new File(path);
        if (!folder.exists() ) {
            Properties createProperties = new Properties();
            createProperties.store(new FileOutputStream(path), "create");
        }

        LinkedProperties properties = new LinkedProperties();
        InputStreamReader reader = null;

        try {
            reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8);
            properties.load(reader);
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            reader.close();
        }
        return properties;
    }

}
