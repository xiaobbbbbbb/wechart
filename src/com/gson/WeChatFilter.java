/**
 * 微信公众平台开发模式(JAVA) SDK
 * (c) 2012-2013 ____′↘夏悸 <wmails@126.cn>, MIT Licensed
 * http://www.jeasyuicn.com/wechat
 */
package com.gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.Properties;
import java.util.Random;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.gson.bean.Articles;
import com.gson.bean.InMessage;
import com.gson.bean.OutMessage;
import com.gson.bean.TextOutMessage;
import com.gson.inf.MessageProcessingHandler;
import com.gson.util.Tools;
import com.gson.util.XStreamFactory;
import com.thoughtworks.xstream.XStream;

/**
 * 请求拦截
 * 
 * @author GodSon
 * 
 */
public class WeChatFilter implements Filter {

	private final Logger	logger			= Logger.getLogger(WeChatFilter.class);
	private String			_token;
	private String			conf			= "classPath:wechat.properties";
	private String			defaultHandler	= "com.gson.inf.DefaultMessageProcessingHandlerImpl";
	private Properties		p;

	@Override
	public void destroy() {
		logger.info("WeChatFilter已经销毁");
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		Boolean isGet = request.getMethod().equals("GET");

		String path = request.getServletPath();
		String pathInfo = path.substring(path.lastIndexOf("/"));

		if (pathInfo == null) {
			response.getWriter().write("error");
		} else {
			_token = pathInfo.substring(1);
			if (isGet) {
				doGet(request, response);
			} else {
				doPost(request, response);
			}
		}
	}

	private void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setCharacterEncoding("UTF-8");
		response.setContentType("text/xml");

		OutMessage oms = new OutMessage();
		ServletInputStream in = request.getInputStream();
		// 转换微信post过来的xml内容
		XStream xs = XStreamFactory.init(false);
		xs.alias("xml", InMessage.class);
		String xmlMsg = Tools.inputStream2String(in);

		logger.debug("输入消息:[" + xmlMsg + "]");

		InMessage msg = (InMessage) xs.fromXML(xmlMsg);
		// 获取自定消息处理器，如果自定义处理器则使用默认处理器。
		String handler = p.getProperty("MessageProcessingHandlerImpl");
		if (handler == null)
			handler = defaultHandler;

		try {
			// 加载处理器
			Class<?> clazz = Class.forName(handler);
			MessageProcessingHandler processingHandler = (MessageProcessingHandler) clazz.newInstance();
			// 取得消息类型
			String type = msg.getMsgType();
			Method mt = clazz.getMethod(type + "TypeMsg", InMessage.class);
			oms = (OutMessage) mt.invoke(processingHandler, msg);
			if (oms == null) {
				oms = new TextOutMessage();
				((TextOutMessage) oms).setContent("系统错误!");
			}
			setMsgInfo(oms,msg);
		} catch (Exception e) {
			logger.error(e);
			oms = new TextOutMessage();
			((TextOutMessage) oms).setContent("系统错误!");
			try {
				setMsgInfo(oms,msg);
			} catch (Exception e1) {
				logger.error(e);
			}
		}

		// 把发送发送对象转换为xml输出
		xs = XStreamFactory.init(true);
		xs.alias("xml", oms.getClass());
		xs.alias("item", Articles.class);
		String xml = xs.toXML(oms);

		logger.debug("输出消息:[" + xml + "]");

		response.getWriter().write(xml);
	}

	private void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String signature = request.getParameter("signature");// 微信加密签名
		String timestamp = request.getParameter("timestamp");// 时间戳
		String nonce = request.getParameter("nonce");// 随机数
		String echostr = request.getParameter("echostr");//
		// 验证
		if (Tools.checkSignature(_token, signature, timestamp, nonce)) {
			response.getWriter().write(echostr);
		}
	}

	private void setMsgInfo(OutMessage oms,InMessage msg) throws Exception {
		// 设置发送信息
		Class<?> outMsg = oms.getClass().getSuperclass();
		Field CreateTime = outMsg.getDeclaredField("CreateTime");
		Field ToUserName = outMsg.getDeclaredField("ToUserName");
		Field FromUserName = outMsg.getDeclaredField("FromUserName");

		ToUserName.setAccessible(true);
		CreateTime.setAccessible(true);
		FromUserName.setAccessible(true);

		CreateTime.set(oms, new Date().getTime());
		ToUserName.set(oms, msg.getFromUserName());
		FromUserName.set(oms, msg.getToUserName());
	}

	/**
	 * 启动的时候加载wechat.properties配置 可以在过滤器配置wechat.properties路径
	 */
	@Override
	public void init(FilterConfig config) throws ServletException {
		String cf = config.getInitParameter("conf");
		if (cf != null) {
			conf = cf;
		}
		String classPath = this.getClass().getResource("/").getPath().replaceAll("%20", " ");
		conf = conf.replace("classPath:", classPath);
		p = new Properties();
		File pfile = new File(conf);
		if (pfile.exists()) {
			try {
				p.load(new FileInputStream(pfile));
			} catch (FileNotFoundException e) {
				logger.error("未找到wechat.properties", e);
			} catch (IOException e) {
				logger.error("wechat.properties读取异常", e);
			}
		}
		logger.info("WeChatFilter已经启动！");
	}

}
