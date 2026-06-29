package com.mgmtp.gives.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

public class HtmlSanitizerUtil {
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("h1", "h2", "h3", "h4", "h5", "h6", "p", "ul", "ol", "li", "strong", "em", "br", "b", "i", "u", "a")
            .allowAttributes("href", "target", "rel").onElements("a")
            .allowStandardUrlProtocols()
            .toFactory();

    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return POLICY.sanitize(html);
    }
}
