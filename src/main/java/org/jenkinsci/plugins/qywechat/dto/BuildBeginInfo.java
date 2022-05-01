package org.jenkinsci.plugins.qywechat.dto;

import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.scm.ChangeLogSet;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.qywechat.NotificationUtil;
import org.jenkinsci.plugins.qywechat.model.NotificationConfig;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开始构建的通知信息
 * @author jiaju
 */
public class BuildBeginInfo {

    /**
     * 请求参数
     */
    private Map params = new HashMap<String, Object>();

    /**
     * 预计时间，毫秒
     */
    private Long durationTime = 0L;

    /**
     * 本次构建控制台地址
     */
    private String consoleUrl;

    /**
     * 工程名称
     */
    private String projectName;

    /**
     * 环境名称
     */
    private String topicName = "";

    private NotificationConfig config;

    private ChangeLogSet<? extends ChangeLogSet.Entry> changeLogSet;

    public BuildBeginInfo(String projectName, AbstractBuild<?, ?> build, NotificationConfig config){
        //获取请求参数
        List<ParametersAction> parameterList = build.getActions(ParametersAction.class);
        if(parameterList!=null && parameterList.size()>0){
            for(ParametersAction p : parameterList){
                for(ParameterValue pv : p.getParameters()){
                    this.params.put(pv.getName(), pv.getValue());
                }
            }
        }
        //预计时间
        if(build.getProject().getEstimatedDuration()>0){
            this.durationTime = build.getProject().getEstimatedDuration();
        }
        //控制台地址
        StringBuilder urlBuilder = new StringBuilder();
        String jenkinsUrl = NotificationUtil.getJenkinsUrl();
        if(StringUtils.isNotEmpty(jenkinsUrl)){
            String buildUrl = build.getUrl();
            urlBuilder.append(jenkinsUrl);
            if(!jenkinsUrl.endsWith("/")){
                urlBuilder.append("/");
            }
            urlBuilder.append(buildUrl);
            if(!buildUrl.endsWith("/")){
                urlBuilder.append("/");
            }
            urlBuilder.append("console");
        }
        this.consoleUrl = urlBuilder.toString();
        //工程名称
        this.projectName = projectName;
        //环境名称
        if(config.topicName!=null){
            topicName = config.topicName;
        }
        this.changeLogSet = build.getChangeSet();
        this.config = config;
    }

    public String toJSONString(){
        //参数组装
        StringBuffer paramBuffer = new StringBuffer();
        params.forEach((key, val)->{
            paramBuffer.append(key);
            paramBuffer.append("=");
            paramBuffer.append(val);
            paramBuffer.append(", ");
        });
        if(paramBuffer.length()==0){
            paramBuffer.append("无");
        }else{
            paramBuffer.deleteCharAt(paramBuffer.length()-2);
        }

        //耗时预计
        String durationTimeStr = "无";
        if(durationTime>0){
            Long l = durationTime / (1000 * 60);
            durationTimeStr = l + "分钟";
        }

        //组装内容
        StringBuilder content = new StringBuilder();
        if(StringUtils.isNotEmpty(topicName)){
            content.append(this.topicName);
        }
        content.append("<font color=\"info\">【" + this.projectName + "】</font>开始构建\n");
        content.append(" >构建参数：<font color=\"comment\">" + paramBuffer.toString() + "</font>\n");
        content.append(" >预计用时：<font color=\"comment\">" +  durationTimeStr + "</font>\n");
        if(config.recordChangeLog){
            processChangeLogSet(content, changeLogSet, config);
        }
        if(StringUtils.isNotEmpty(this.consoleUrl)){
            content.append(" >[查看控制台](" + this.consoleUrl + ")");
        }

        Map markdown = new HashMap<String, Object>();
        markdown.put("content", content.toString());

        Map data = new HashMap<String, Object>();
        data.put("msgtype", "markdown");
        data.put("markdown", markdown);

        String req = JSONObject.fromObject(data).toString();
        return req;
    }

    private void processChangeLogSet(StringBuilder sb, ChangeLogSet cs, NotificationConfig config) {
        sb.append(">提交记录：");
        DateFormat df = new SimpleDateFormat(config.dateFormat);
        if(cs.isEmptySet()){
            sb.append("无改动\n");
        }else{
            sb.append("\n");
            for (Object o : cs) {
                ChangeLogSet.Entry e = (ChangeLogSet.Entry) o;
                sb.append(String.format(fixNull(config.entryFormat), e.getAuthor(), e.getCommitId(), e.getMsg(), df.format(new Date(e.getTimestamp()))));

                try {
                    for (ChangeLogSet.AffectedFile file : e.getAffectedFiles()) {
                        sb.append(String.format(fixNull(config.lineFormat), file.getEditType().getName(), file.getPath()));
                    }
                } catch (UnsupportedOperationException ex) {
                    // early versions of SCM did not support getAffectedFiles, only had getAffectedPaths
                    for (String file : e.getAffectedPaths()) {
                        sb.append(String.format(fixNull(config.lineFormat), "", file));
                    }
                }
            }
        }
        sb.append("\n");
    }

    private String fixNull(String s){
        return StringUtils.isNotBlank(s) ? (s + "\n") : "";
    }
}
