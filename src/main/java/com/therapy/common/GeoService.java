package com.therapy.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
public class GeoService {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Resolves a 2-letter country code from an IP address using ipapi.co (free, no key needed).
     * Falls back to "AR" on any error.
     */
    @SuppressWarnings("unchecked")
    public String countryCodeFromIp(String ip) {
        if (ip == null || ip.isBlank() || ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return "AR";
        }
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    "https://ipapi.co/" + ip + "/json/", Map.class);
            if (response != null && response.containsKey("country_code")) {
                String code = (String) response.get("country_code");
                if (code != null && code.matches("[A-Z]{2}")) return code;
            }
        } catch (Exception e) {
            log.debug("Geo lookup failed for IP {}: {}", ip, e.getMessage());
        }
        return "AR";
    }
}
