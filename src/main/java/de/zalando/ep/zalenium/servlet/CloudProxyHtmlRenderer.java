package de.zalando.ep.zalenium.servlet;

import com.google.gson.JsonObject;
import de.zalando.ep.zalenium.proxy.BrowserStackRemoteProxy;
import de.zalando.ep.zalenium.proxy.SauceLabsRemoteProxy;
import de.zalando.ep.zalenium.proxy.TestingBotRemoteProxy;
import org.apache.commons.io.IOUtils;
import org.openqa.grid.common.exception.GridException;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.TestSession;
import org.openqa.grid.internal.TestSlot;
import org.openqa.grid.internal.utils.HtmlRenderer;
import org.openqa.grid.web.servlet.beta.MiniCapability;
import org.openqa.grid.web.servlet.beta.SlotsLines;
import org.openqa.selenium.Platform;
import org.openqa.selenium.remote.CapabilityType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CloudProxyHtmlRenderer implements HtmlRenderer {

    private final String templateName = "html_templates/proxy_tab_browser_line_renderer.html";
    private RemoteProxy proxy;
    private TemplateRenderer templateRenderer;

    @SuppressWarnings("unused")
    private CloudProxyHtmlRenderer() {}

    public CloudProxyHtmlRenderer(RemoteProxy proxy) {
        this.proxy = proxy;
        templateRenderer = new TemplateRenderer(templateName);
    }

    public String renderSummary() {
        InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("html_templates/proxy_tab_renderer.html");
        try {
            String tabConfigRenderer = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
            return tabConfigRenderer.replace("{{proxyName}}", proxy.getClass().getSimpleName())
                    .replace("{{proxyVersion}}", getHtmlNodeVersion())
                    .replace("{{proxyId}}", proxy.getId())
                    .replace("{{proxyPlatform}}", getPlatform(proxy))
                    .replace("{{tabBrowsers}}", tabBrowsers())
                    .replace("{{tabConfig}}", tabConfig());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    private String getHtmlNodeVersion() {
        try {
            JsonObject object = proxy.getStatus();
            String version = object.get("value").getAsJsonObject()
                    .get("build").getAsJsonObject()
                    .get("version").getAsString();
            return " (version : "+version+ ")";
        }catch (Exception e) {
            return " unknown version,"+e.getMessage();
        }
    }

    // content of the config tab.
    private String tabConfig() {
        InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("html_templates/proxy_tab_config_renderer.html");
        try {
            String tabConfigRenderer = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
            return tabConfigRenderer.replace("{{proxyConfig}}", proxy.getConfig().toString("<p>%1$s: %2$s</p>"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }


    // content of the browsers tab
    private String tabBrowsers() {
        SlotsLines wdLines = new SlotsLines();
        for (TestSlot slot : proxy.getTestSlots()) {
            wdLines.add(slot);
        }
        String webDriverLines = "";
        if (wdLines.getLinesType().size() != 0) {
            webDriverLines = webDriverLines.concat(getLines(wdLines));
        }
        InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("html_templates/proxy_tab_browser_renderer.html");
        try {
            String tabConfigRenderer = IOUtils.toString(resourceAsStream, StandardCharsets.UTF_8);
            return tabConfigRenderer.replace("{{webDriverLines}}", webDriverLines);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return webDriverLines;
    }

    // the lines of icon representing the possible slots
    private String getLines(SlotsLines lines) {
        String slotLines = "";
        for (MiniCapability cap : lines.getLinesType()) {
            String version = cap.getVersion();
            if (version != null) {
                version = "v:" + version;
            } else {
                version = "";
            }
            String singleSlotsHtml = "";
            for (TestSlot s : lines.getLine(cap)) {
                singleSlotsHtml = singleSlotsHtml.concat(getSingleSlotHtml(s));
            }
            Map<String, String> linesValues = new HashMap<>();
            linesValues.put("{{browserVersion}}", version);
            linesValues.put("{{singleSlots}}", singleSlotsHtml);
            slotLines = slotLines.concat(templateRenderer.renderTemplate(linesValues));
        }
        return slotLines;
    }

    private String getSingleSlotHtml(TestSlot s) {
        TestSession session = s.getSession();
        String icon = "";
        if (proxy instanceof TestingBotRemoteProxy) {
            icon = "/grid/admin/ZaleniumResourceServlet/images/testingbot.png";
        }
        if (proxy instanceof BrowserStackRemoteProxy) {
            icon = "/grid/admin/ZaleniumResourceServlet/images/browserstack.png";
        }
        if (proxy instanceof SauceLabsRemoteProxy) {
            icon = "/grid/admin/ZaleniumResourceServlet/images/saucelabs.png";
        }
        String slotClass = "";
        String slotTitle;
        if (session != null) {
            slotClass = "busy";
            slotTitle = session.get("lastCommand").toString();
        } else {
            slotTitle = s.getCapabilities().toString();
        }
        Map<String, String> singleSlotValues = new HashMap<>();
        singleSlotValues.put("{{slotIcon}}", icon);
        singleSlotValues.put("{{slotClass}}", slotClass);
        singleSlotValues.put("{{slotTitle}}", slotTitle);
        return templateRenderer.renderSection("{{singleSlots}}", singleSlotValues);
    }

    /**
     * return the platform for the proxy. It should be the same for all slots of the proxy, so checking that.
     * @param proxy remote proxy
     * @return Either the platform name, "Unknown", "mixed OS", or "not specified".
     */
    private static String getPlatform(RemoteProxy proxy) {
        Platform res;
        if (proxy.getTestSlots().size() == 0) {
            return "Unknown";
        }
        res = getPlatform(proxy.getTestSlots().get(0));

        for (TestSlot slot : proxy.getTestSlots()) {
            Platform tmp = getPlatform(slot);
            if (tmp != res) {
                return "mixed OS";
            }
            res = tmp;
        }
        if (res == null) {
            return "not specified";
        }
        return res.toString();
    }

    private static Platform getPlatform(TestSlot slot) {
        Object o = slot.getCapabilities().get(CapabilityType.PLATFORM);
        if (o == null) {
            return Platform.ANY;
        }
        if (o instanceof String) {
            return Platform.valueOf((String) o);
        } else if (o instanceof Platform) {
            return (Platform) o;
        } else {
            throw new GridException("Cannot cast " + o + " to org.openqa.selenium.Platform");
        }
    }

}
