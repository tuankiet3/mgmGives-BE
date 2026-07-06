package com.mgmtp.gives.util;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

public class HtmlSanitizerUtil {
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("h1", "h2", "h3", "p", "ul", "ol", "li", "strong", "em", "br", "b", "i", "u", "a", "img", "video")
            .allowAttributes("data-text-align").onElements("h1", "h2", "h3", "p")
            .allowAttributes("href", "target", "rel").onElements("a")
            .allowAttributes("src", "alt", "title").onElements("img")
            .allowAttributes("src", "title", "controls", "preload").onElements("video")
            .allowStandardUrlProtocols()
            .toFactory();

    public static String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return POLICY.sanitize(html);
    }
}
